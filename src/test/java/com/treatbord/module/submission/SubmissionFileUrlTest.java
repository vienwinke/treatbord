package com.treatbord.module.submission;

import com.treatbord.common.BusinessException;
import com.treatbord.module.file.entity.FileRecord;
import com.treatbord.module.file.service.FileService;
import com.treatbord.module.submission.dto.SubmissionRequest;
import com.treatbord.module.submission.dto.SubmissionVO;
import com.treatbord.module.submission.service.SubmissionService;
import com.treatbord.module.task.service.ClaimService;
import com.treatbord.module.task.service.TaskService;
import com.treatbord.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 凭证文件【读时签名】验收（B5b）：
 * 上传者/接取者/发布者能拿到签名 URL；陌生人连详情都拿不到（403）；
 * 上传响应本身也返回签名 URL（解决 dev 下 127.0.0.1 写死问题）。
 */
class SubmissionFileUrlTest extends AbstractIntegrationTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};

    @Autowired private FileService fileService;
    @Autowired private SubmissionService submissionService;
    @Autowired private ClaimService claimService;
    @Autowired private TaskService taskService;

    @Test
    @DisplayName("接取者与发布者能拿到签名文件 URL，陌生人 403")
    void signedFileUrlsOnlyForParticipants() {
        Long publisherId = createUser("B5-发布者");
        Long claimerId = createUser("B5-接取者");
        Long strangerId = createUser("B5-陌生人");
        Long taskId = createTask(publisherId, 1);
        Long claimId = claimService.claim(taskId, claimerId, null);

        // 走真实上传链路
        MockMultipartFile mf = new MockMultipartFile("file", "proof.png", "image/png", PNG);
        FileRecord file = fileService.upload(mf, "submission", claimerId, null);
        assertNotNull(file.getUrl());
        assertTrue(file.getUrl().contains("exp="), "上传响应应返回带 exp 的签名 URL");
        assertTrue(file.getUrl().contains("sig="), "上传响应应返回带 sig 的签名 URL");

        SubmissionRequest req = new SubmissionRequest();
        req.setContent("完成凭证");
        req.setFileIds(List.of(file.getId()));
        submissionService.submit(claimId, claimerId, req, null);

        // 接取者本人可见（签名 URL）
        SubmissionVO asClaimer = submissionService.detail(claimId, claimerId).getSubmission();
        assertNotNull(asClaimer);
        assertFalse(asClaimer.getFileUrls().isEmpty(), "接取者应拿到签名 URL");
        assertTrue(asClaimer.getFileUrls().get(0).contains("sig="));

        // 发布者审核时必须能看到图
        SubmissionVO asPublisher = submissionService.detail(claimId, publisherId).getSubmission();
        assertFalse(asPublisher.getFileUrls().isEmpty(), "发布者应拿到签名 URL（否则无法审核）");

        // 发布者查看任务下接取列表：同样带签名 URL
        var claims = taskService.listClaimsOfTask(taskId, publisherId);
        assertFalse(claims.isEmpty());
        assertNotNull(claims.get(0).getSubmission());
        assertTrue(claims.get(0).getSubmission().getFileUrls().get(0).contains("sig="));

        // 陌生人：连凭证详情都拿不到
        assertThrows(BusinessException.class, () -> submissionService.detail(claimId, strangerId));
    }
}
