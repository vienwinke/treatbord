package com.treatbord.module.submission.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.treatbord.module.file.service.FileService;
import com.treatbord.module.submission.dto.SubmissionVO;
import com.treatbord.module.submission.entity.TaskSubmission;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 凭证视图组装（含**签名文件 URL**，读时签名）。
 *
 * <p>单独成 Bean 的原因：它同时被 `SubmissionService.detail()`（接取双方查看凭证）和
 * `TaskService.listClaimsOfTask()`（发布者查看接取列表）使用。若直接让 TaskService 依赖
 * SubmissionService，会出现 TaskService → SubmissionService → ClaimService → TaskService 的循环依赖
 * （Spring Boot 2.6+ 默认禁止）。本组装器只依赖 FileService，天然无环。
 *
 * <p>⚠️ 授权说明：本类只负责"签名"，**授权由调用方的业务校验负责**：
 * detail() 已校验"接取者本人或任务发布者"；发布者列表接口已校验任务归属。
 * 谁能看到凭证，谁就能拿到其中图片的签名 URL —— 规则只有一套，不会漂移。
 */
@Component
@RequiredArgsConstructor
public class SubmissionViewAssembler {

    private final FileService fileService;
    private final ObjectMapper objectMapper;

    public SubmissionVO assemble(TaskSubmission sub) {
        SubmissionVO vo = SubmissionVO.from(sub);
        if (vo != null && sub.getFileIds() != null) {
            java.util.List<Long> ids = parseFileIds(sub.getFileIds());
            vo.setFileIds(ids);
            vo.setFileUrls(fileService.signedUrls(ids));
        }
        return vo;
    }

    private java.util.List<Long> parseFileIds(String json) {
        if (json == null || json.isBlank()) {
            return java.util.List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<java.util.List<Long>>() {
            });
        } catch (Exception e) {
            return java.util.List.of();
        }
    }
}
