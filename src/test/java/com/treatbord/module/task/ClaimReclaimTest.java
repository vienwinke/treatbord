package com.treatbord.module.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.treatbord.common.BusinessException;
import com.treatbord.common.ResultCode;
import com.treatbord.module.task.entity.ClaimStatusLog;
import com.treatbord.module.task.entity.TaskClaim;
import com.treatbord.module.task.enums.ClaimStatus;
import com.treatbord.module.task.mapper.ClaimStatusLogMapper;
import com.treatbord.module.task.service.ClaimService;
import com.treatbord.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 「取消后重新接取同一任务」的回归测试。
 *
 * 修掉的 bug：task_claim 的唯一索引是 (task_id, user_id)，不含 status/deleted，
 * 而取消只把 status 改成 CANCELLED、不删行。旧实现重接时直接 insert ->
 * 撞唯一键 -> DuplicateKeyException -> 被全局处理器翻成 409「状态冲突，非法流转」，
 * 用户被永久锁死在该任务外，且错误信息完全误导。
 *
 * 现在的语义：重接**复用原行**（CANCELLED/REJECTED -> CLAIMED）；
 * 名额按「是否已回减」区分 —— CANCELLED 需重新扣减，REJECTED 不重复扣减。
 */
class ClaimReclaimTest extends AbstractIntegrationTest {

    @Autowired
    private ClaimService claimService;

    @Autowired
    private ClaimStatusLogMapper claimStatusLogMapper;

    @Test
    @DisplayName("取消后重新接取：复用同一行、只占一个名额、状态回到 CLAIMED、轨迹完整")
    void reclaimAfterCancel() {
        Long publisherId = createUser("重接-发布者");
        Long claimerId = createUser("重接-接取者");
        Long taskId = createTask(publisherId, 2);

        Long firstClaimId = claimService.claim(taskId, claimerId, null);
        claimService.cancelClaim(firstClaimId, claimerId, null);

        assertEquals(0, taskMapper.selectById(taskId).getClaimedCount(), "取消后名额应回减到 0");
        assertEquals(ClaimStatus.CANCELLED.name(), taskClaimMapper.selectById(firstClaimId).getStatus());

        // 关键断言：修复前这里会抛 409（insert 撞唯一键），现在应当成功
        Long secondClaimId = claimService.claim(taskId, claimerId, null);

        assertEquals(firstClaimId, secondClaimId, "重新接取必须复用原行，不能插第二行");
        assertEquals(1L, taskClaimMapper.selectCount(new LambdaQueryWrapper<TaskClaim>()
                        .eq(TaskClaim::getTaskId, taskId)
                        .eq(TaskClaim::getUserId, claimerId)),
                "同一用户同一任务在库里只能有一行");
        assertEquals(ClaimStatus.CLAIMED.name(), taskClaimMapper.selectById(firstClaimId).getStatus());
        assertEquals(1, taskMapper.selectById(taskId).getClaimedCount(), "重接后应重新占 1 个名额");

        assertEquals(3L, claimStatusLogMapper.selectCount(new LambdaQueryWrapper<ClaimStatusLog>()
                        .eq(ClaimStatusLog::getClaimId, firstClaimId)),
                "状态变更应留 CLAIMED->CANCELLED->CLAIMED 三条审计");
    }

    @Test
    @DisplayName("已有有效接取时重复接取：报明确的 CLAIM_DUPLICATE(3002)，而不是 409 非法流转")
    void duplicateClaimGetsClearError() {
        Long publisherId = createUser("重复码-发布者");
        Long claimerId = createUser("重复码-接取者");
        Long taskId = createTask(publisherId, 2);

        claimService.claim(taskId, claimerId, null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> claimService.claim(taskId, claimerId, null));
        assertEquals(ResultCode.CLAIM_DUPLICATE.getCode(), ex.getCode(),
                "应是 3002 重复接取，而不是 409 非法流转");
        assertEquals(1, taskMapper.selectById(taskId).getClaimedCount(), "失败的重复接取不应占名额");
    }
}
