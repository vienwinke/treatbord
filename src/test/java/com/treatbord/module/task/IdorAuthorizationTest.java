package com.treatbord.module.task;

import com.treatbord.common.BusinessException;
import com.treatbord.common.ResultCode;
import com.treatbord.module.review.service.ReviewService;
import com.treatbord.module.submission.service.SubmissionService;
import com.treatbord.module.task.service.ClaimService;
import com.treatbord.module.task.service.TaskService;
import com.treatbord.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 越权（IDOR）验收测试：非归属者不得取消他人接取、不得审核、不得查看凭证详情。
 */
class IdorAuthorizationTest extends AbstractIntegrationTest {

    @Autowired private ClaimService claimService;
    @Autowired private ReviewService reviewService;
    @Autowired private SubmissionService submissionService;
    @Autowired private TaskService taskService;

    @Test
    @DisplayName("陌生人不能取消他人接取 → 403")
    void strangerCannotCancelOthersClaim() {
        Long publisherId = createUser("IDOR-发布者");
        Long claimerId = createUser("IDOR-接取者");
        Long strangerId = createUser("IDOR-陌生人");
        Long taskId = createTask(publisherId, 1);
        Long claimId = claimService.claim(taskId, claimerId, null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> claimService.cancelClaim(claimId, strangerId, null));
        assertEquals(ResultCode.FORBIDDEN.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("非发布者不能审核凭证 → 403")
    void nonPublisherCannotReview() {
        Long publisherId = createUser("IDOR2-发布者");
        Long claimerId = createUser("IDOR2-接取者");
        Long strangerId = createUser("IDOR2-陌生人");
        Long taskId = createTask(publisherId, 1);
        Long claimId = claimService.claim(taskId, claimerId, null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> reviewService.review(claimId, strangerId, "approve", "越权尝试", null));
        assertEquals(ResultCode.FORBIDDEN.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("非当事人不能查看接取详情 → 403；当事人可以")
    void onlyParticipantsCanViewDetail() {
        Long publisherId = createUser("IDOR3-发布者");
        Long claimerId = createUser("IDOR3-接取者");
        Long strangerId = createUser("IDOR3-陌生人");
        Long taskId = createTask(publisherId, 1);
        Long claimId = claimService.claim(taskId, claimerId, null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> submissionService.detail(claimId, strangerId));
        assertEquals(ResultCode.FORBIDDEN.getCode(), ex.getCode());

        assertDoesNotThrow(() -> submissionService.detail(claimId, claimerId), "接取者本人可看");
        assertDoesNotThrow(() -> submissionService.detail(claimId, publisherId), "发布者可看");
    }

    @Test
    @DisplayName("非发布者不能取消任务、不能查看任务的接取列表 → 403")
    void onlyPublisherCanCancelAndList() {
        Long publisherId = createUser("IDOR4-发布者");
        Long strangerId = createUser("IDOR4-陌生人");
        Long taskId = createTask(publisherId, 1);

        assertEquals(ResultCode.FORBIDDEN.getCode(),
                assertThrows(BusinessException.class, () -> taskService.cancel(taskId, strangerId, null)).getCode());
        assertEquals(ResultCode.FORBIDDEN.getCode(),
                assertThrows(BusinessException.class,
                        () -> taskService.listClaimsOfTask(taskId, strangerId)).getCode());
    }
}
