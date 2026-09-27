package com.treatbord.module.task;

import com.treatbord.common.BusinessException;
import com.treatbord.common.ResultCode;
import com.treatbord.module.task.entity.Task;
import com.treatbord.module.task.entity.TaskClaim;
import com.treatbord.module.task.service.ClaimService;
import com.treatbord.support.AbstractIntegrationTest;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 并发防超卖验收测试（ROADMAP 核心验收项）：
 * 20 个用户同时抢 5 个名额 → 恰好 5 人成功，claimed_count 与实际行数一致，零超卖。
 */
class ClaimConcurrencyTest extends AbstractIntegrationTest {

    @Autowired
    private ClaimService claimService;

    @Test
    @DisplayName("20 线程抢 5 名额：恰好 5 成功、其余名额已满、计数与行数一致")
    void concurrentClaimDoesNotOversell() throws Exception {
        Long publisherId = createUser("并发-发布者");
        Long taskId = createTask(publisherId, 5);

        int threads = 20;
        List<Long> claimerIds = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            claimerIds.add(createUser("并发-接取者" + i));
        }

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger full = new AtomicInteger();
        List<String> unexpected = Collections.synchronizedList(new ArrayList<>());

        for (Long claimerId : claimerIds) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    claimService.claim(taskId, claimerId, null);
                    success.incrementAndGet();
                } catch (BusinessException e) {
                    if (e.getCode() == ResultCode.TASK_FULL.getCode()) {
                        full.incrementAndGet();
                    } else {
                        unexpected.add("code=" + e.getCode() + " msg=" + e.getMessage());
                    }
                } catch (Exception e) {
                    unexpected.add(e.getClass().getSimpleName() + ": " + e.getMessage());
                }
            });
        }

        assertTrue(ready.await(10, TimeUnit.SECONDS), "所有线程应就绪");
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS), "并发接取应在 30 秒内结束");

        assertEquals(5, success.get(), "成功接取数必须等于名额（防超卖）");
        assertEquals(15, full.get(), "其余请求应因名额已满被拒绝");
        assertTrue(unexpected.isEmpty(), "不应出现其他异常: " + unexpected);

        Task task = taskMapper.selectById(taskId);
        assertEquals(5, task.getClaimedCount(), "claimed_count 必须等于实际成功数");
        assertEquals(5L, taskClaimMapper.selectCount(
                new LambdaQueryWrapper<TaskClaim>().eq(TaskClaim::getTaskId, taskId)),
                "task_claim 行数必须等于名额");

        System.out.printf("[并发验收] 线程=%d 名额=5 成功=%d 名额已满=%d claimed_count=%d%n",
                threads, success.get(), full.get(), task.getClaimedCount());
    }

    @Test
    @DisplayName("同一用户并发重复接取：唯一索引 + 显式校验兜底，只成功一次")
    void sameUserCannotClaimTwice() throws Exception {
        Long publisherId = createUser("重复-发布者");
        Long claimerId = createUser("重复-接取者");
        Long taskId = createTask(publisherId, 3);

        int attempts = 5;
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        CountDownLatch done = new CountDownLatch(attempts);

        for (int i = 0; i < attempts; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    claimService.claim(taskId, claimerId, null);
                    success.incrementAndGet();
                } catch (Exception ignored) {
                    // 重复接取属预期失败
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertTrue(done.await(20, TimeUnit.SECONDS));
        pool.shutdownNow();

        assertEquals(1, success.get(), "同一用户只应接取成功一次");
        assertEquals(1L, taskClaimMapper.selectCount(
                new LambdaQueryWrapper<TaskClaim>().eq(TaskClaim::getTaskId, taskId)));
    }
}
