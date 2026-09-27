package com.treatbord.module.task;

import com.treatbord.common.PageResult;
import com.treatbord.module.task.dto.TaskVO;
import com.treatbord.module.task.service.ClaimService;
import com.treatbord.module.task.service.TaskService;
import com.treatbord.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 任务缓存（Cache-Aside）验收：
 * 1) 命中缓存：直接改库后读接口仍返回旧值（证明走的是缓存）；
 * 2) 写操作失效：发生真实写操作（接取）后，读接口返回新值（证明失效生效）。
 */
class TaskCacheTest extends AbstractIntegrationTest {

    @Autowired private TaskService taskService;
    @Autowired private ClaimService claimService;

    @Test
    @DisplayName("列表缓存：命中返回旧值，写操作后失效取新值")
    void listCacheHitThenInvalidated() {
        Long publisherId = createUser("CACHE-发布者");
        Long taskId = createTask(publisherId, 1);
        String title = taskMapper.selectById(taskId).getTitle();

        // 第一次：回源并写缓存
        assertEquals(title, findTitle(taskService.list(null, title, 1, 20), taskId));

        // 绕过服务直接改库 → 缓存未失效，应仍返回旧值（证明命中缓存）
        jdbcTemplate.update("UPDATE task SET title = ? WHERE id = ?", title + "-DB", taskId);
        assertEquals(title, findTitle(taskService.list(null, title, 1, 20), taskId), "应命中缓存（返回旧标题）");

        // 真实写操作（接取）→ 列表版本号 +1 → 缓存失效，取到新值
        Long claimerId = createUser("CACHE-接取者");
        claimService.claim(taskId, claimerId, null);
        assertEquals(title + "-DB", findTitle(taskService.list(null, title, 1, 20), taskId),
                "写操作后缓存应失效并取到新标题");
    }

    @Test
    @DisplayName("详情缓存：命中返回旧值，写操作后失效；claimedByMe 为每用户字段实时计算")
    void detailCacheHitThenInvalidated() {
        Long publisherId = createUser("CACHE2-发布者");
        Long claimerId = createUser("CACHE2-接取者");
        Long taskId = createTask(publisherId, 1);
        String title = taskMapper.selectById(taskId).getTitle();

        assertEquals(title, taskService.detail(taskId, null).getTitle(), "首次详情回源");

        jdbcTemplate.update("UPDATE task SET title = ? WHERE id = ?", title + "-DB", taskId);
        assertEquals(title, taskService.detail(taskId, null).getTitle(), "应命中详情缓存（返回旧标题）");

        claimService.claim(taskId, claimerId, null);   // 写操作 → 详情缓存被删
        assertEquals(title + "-DB", taskService.detail(taskId, null).getTitle(), "写操作后应取到新标题");

        // claimedByMe 不缓存：接取者本人看到 true，其他人看到 false
        assertEquals(Boolean.TRUE, taskService.detail(taskId, claimerId).getClaimedByMe());
        assertEquals(Boolean.FALSE, taskService.detail(taskId, publisherId).getClaimedByMe());
    }

    @Test
    @DisplayName("不存在的任务：空值缓存防穿透（第二次仍抛 2001）")
    void absentTaskIsNegativelyCached() {
        long missingId = 999_999_999L;
        assertTrue(throwsTaskNotFound(() -> taskService.detail(missingId, null)));
        assertTrue(throwsTaskNotFound(() -> taskService.detail(missingId, null)),
                "第二次应由空值缓存直接拒绝（防穿透）");
    }

    private String findTitle(PageResult<TaskVO> page, Long taskId) {
        Optional<TaskVO> found = page.getList().stream().filter(v -> v.getId().equals(taskId)).findFirst();
        assertTrue(found.isPresent(), "列表中应包含目标任务");
        return found.get().getTitle();
    }

    private boolean throwsTaskNotFound(Runnable action) {
        try {
            action.run();
            return false;
        } catch (Exception e) {
            return e instanceof com.treatbord.common.BusinessException be
                    && be.getCode() == com.treatbord.common.ResultCode.TASK_NOT_FOUND.getCode();
        }
    }
}
