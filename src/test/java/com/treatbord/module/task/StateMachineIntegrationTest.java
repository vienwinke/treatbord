package com.treatbord.module.task;

import com.treatbord.common.BusinessException;
import com.treatbord.common.ResultCode;
import com.treatbord.module.review.service.ReviewService;
import com.treatbord.module.task.service.ClaimService;
import com.treatbord.module.task.service.TaskService;
import com.treatbord.module.submission.dto.SubmissionRequest;
import com.treatbord.module.submission.service.SubmissionService;
import com.treatbord.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 状态机与跨聚合校验的集成验收：
 * 非法流转一律 409/业务码，杜绝"已取消任务仍可提交""已通过的接取再次审核"等越界操作。
 */
class StateMachineIntegrationTest extends AbstractIntegrationTest {

    @Autowired private ClaimService claimService;
    @Autowired private TaskService taskService;
    @Autowired private ReviewService reviewService;
    @Autowired private SubmissionService submissionService;

    private SubmissionRequest submission(String content) {
        SubmissionRequest req = new SubmissionRequest();
        req.setContent(content);
        req.setFileIds(List.of());
        return req;
    }

    @Test
    @DisplayName("已取消的接取不能再提交凭证 → 3003")
    void cancelledClaimCannotSubmit() {
        Long publisherId = createUser("SM-发布者");
        Long claimerId = createUser("SM-接取者");
        Long taskId = createTask(publisherId, 1);
        Long claimId = claimService.claim(taskId, claimerId, null);

        claimService.cancelClaim(claimId, claimerId, null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> submissionService.submit(claimId, claimerId, submission("迟到提交"), null));
        assertEquals(ResultCode.CLAIM_NOT_SUBMITTABLE.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("已审核通过的接取不能再次审核 → 3004")
    void approvedClaimCannotBeReviewedAgain() {
        Long publisherId = createUser("SM2-发布者");
        Long claimerId = createUser("SM2-接取者");
        Long taskId = createTask(publisherId, 1);
        Long claimId = claimService.claim(taskId, claimerId, null);
        submissionService.submit(claimId, claimerId, submission("完成"), null);

        reviewService.review(claimId, publisherId, "approve", "一次通过", null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> reviewService.review(claimId, publisherId, "approve", "重复审核", null));
        assertEquals(ResultCode.CLAIM_NOT_REVIEWABLE.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("已取消的任务不能再被接取 → 2002")
    void cancelledTaskCannotBeClaimed() {
        Long publisherId = createUser("SM3-发布者");
        Long claimerId = createUser("SM3-接取者");
        Long taskId = createTask(publisherId, 1);

        taskService.cancel(taskId, publisherId, null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> claimService.claim(taskId, claimerId, null));
        assertEquals(ResultCode.TASK_NOT_CLAIMABLE.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("不能接取自己发布的任务 → 2005")
    void publisherCannotClaimOwnTask() {
        Long publisherId = createUser("SM4-发布者");
        Long taskId = createTask(publisherId, 1);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> claimService.claim(taskId, publisherId, null));
        assertEquals(ResultCode.SELF_CLAIM_FORBIDDEN.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("已通过凭证时被驳回 → 3004（状态机拒绝非法流转）")
    void cannotRejectAfterApprove() {
        Long publisherId = createUser("SM5-发布者");
        Long claimerId = createUser("SM5-接取者");
        Long taskId = createTask(publisherId, 2);   // quota=2，避免收尾把任务结算掉
        Long claimId = claimService.claim(taskId, claimerId, null);
        submissionService.submit(claimId, claimerId, submission("完成"), null);

        reviewService.review(claimId, publisherId, "approve", "通过", null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> reviewService.review(claimId, publisherId, "reject", "反悔", null));
        assertEquals(ResultCode.CLAIM_NOT_REVIEWABLE.getCode(), ex.getCode());
    }
}
