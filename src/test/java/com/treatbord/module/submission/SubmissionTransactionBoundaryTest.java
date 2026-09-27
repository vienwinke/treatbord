package com.treatbord.module.submission;

import com.treatbord.module.security.service.ContentSecurityService;
import com.treatbord.module.submission.dto.SubmissionRequest;
import com.treatbord.module.submission.service.SubmissionService;
import com.treatbord.module.task.enums.ClaimStatus;
import com.treatbord.module.task.service.ClaimService;
import com.treatbord.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

/**
 * 长事务治理的验收测试：内容安全检测（真实环境是 HTTP 调用）必须在**事务之外**执行，
 * 数据库写入在事务内完成。若把两者放在同一事务，事务会在等待外部响应期间一直占用连接与行锁。
 */
class SubmissionTransactionBoundaryTest extends AbstractIntegrationTest {

    @MockitoBean
    private ContentSecurityService contentSecurityService;

    @Autowired
    private SubmissionService submissionService;

    @Autowired
    private ClaimService claimService;

    @Test
    @DisplayName("内容安全检测在事务外执行，提交写入在事务内完成")
    void contentSecurityCheckRunsOutsideTransaction() {
        Long publisherId = createUser("TXP-发布者");
        Long claimerId = createUser("TXP-接取者");
        Long taskId = createTask(publisherId, 1);
        Long claimId = claimService.claim(taskId, claimerId, null);

        AtomicBoolean checkRanInsideTransaction = new AtomicBoolean(true);
        doAnswer(invocation -> {
            checkRanInsideTransaction.set(TransactionSynchronizationManager.isActualTransactionActive());
            return null;
        }).when(contentSecurityService).checkText(any(), any(), any());

        SubmissionRequest req = new SubmissionRequest();
        req.setContent("事务边界测试内容");
        req.setFileIds(List.of());

        submissionService.submit(claimId, claimerId, req, null);

        assertFalse(checkRanInsideTransaction.get(),
                "内容安全检测（网络调用）必须在事务之外执行，否则就是长事务");
        assertEquals(ClaimStatus.SUBMITTED.name(),
                taskClaimMapper.selectById(claimId).getStatus(), "事务内写入应已完成");
        assertNotNull(submissionMapper.selectLatestByClaimId(claimId), "凭证应已落库");
    }
}
