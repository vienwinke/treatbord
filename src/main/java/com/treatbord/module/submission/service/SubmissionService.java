package com.treatbord.module.submission.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.treatbord.common.BusinessException;
import com.treatbord.common.ResultCode;
import com.treatbord.module.file.entity.FileRecord;
import com.treatbord.module.file.mapper.FileRecordMapper;
import com.treatbord.module.security.service.ContentSecurityService;
import com.treatbord.module.submission.dto.SubmissionRequest;
import com.treatbord.module.submission.dto.SubmissionVO;
import com.treatbord.module.submission.entity.TaskSubmission;
import com.treatbord.module.submission.mapper.TaskSubmissionMapper;
import com.treatbord.module.task.entity.Task;
import com.treatbord.module.task.entity.TaskClaim;
import com.treatbord.module.task.enums.ClaimStatus;
import com.treatbord.module.task.mapper.TaskMapper;
import com.treatbord.module.task.service.ClaimService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * 凭证服务（docs/API_DESIGN.md §5）。
 *
 * <p>事务边界说明：本类**不含** {@code @Transactional}。
 * `submit()` 是"非事务编排"——先在事务外完成校验与**内容安全检测（网络调用）**，
 * 再把数据库写入交给独立 Bean {@link SubmissionTxService}，避免长事务（详见该类的类注释）。
 */
@Service
@RequiredArgsConstructor
public class SubmissionService {

    /** 可提交凭证的任务状态（跨聚合校验用） */
    private static final Set<String> SUBMITTABLE_TASK_STATUSES =
            Set.of("OPEN", "IN_PROGRESS", "REVIEWING");

    private final TaskSubmissionMapper submissionMapper;
    private final TaskMapper taskMapper;
    private final FileRecordMapper fileRecordMapper;
    private final ClaimService claimService;
    private final ContentSecurityService contentSecurityService;
    private final SubmissionTxService submissionTxService;
    private final SubmissionViewAssembler submissionViewAssembler;
    private final ObjectMapper objectMapper;

    /**
     * 提交完成凭证：CLAIMED → SUBMITTED（CAS），审核前可覆盖。
     *
     * <p>三段式：
     * ① 事务外预校验（归属/状态/任务状态/文件）→ 快速失败，避免为无效请求调用内容安全接口；
     * ② 事务外内容安全检测（**网络调用**，不占用数据库连接与行锁）；
     * ③ 事务内写入（{@link SubmissionTxService#persistSubmission}，独立 Bean 使 @Transactional 生效）。
     */
    public void submit(Long claimId, Long userId, SubmissionRequest req, HttpServletRequest httpReq) {
        // ①-1 归属校验（IDOR）：只能提交自己的接取
        TaskClaim claim = claimService.requireClaim(claimId);
        if (!claim.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只能提交自己的接取凭证");
        }

        // ①-2 状态机：仅 CLAIMED / SUBMITTED（覆盖）可提交
        ClaimStatus cur = ClaimStatus.of(claim.getStatus());
        if (!(cur == ClaimStatus.CLAIMED || cur == ClaimStatus.SUBMITTED)) {
            throw new BusinessException(ResultCode.CLAIM_NOT_SUBMITTABLE, "当前状态不可提交凭证");
        }

        // ①-3 跨聚合校验：任务必须仍处于可提交阶段（防对已取消/已过期任务提交凭证）
        Task task = taskMapper.selectById(claim.getTaskId());
        if (task == null) {
            throw new BusinessException(ResultCode.TASK_NOT_FOUND);
        }
        if (!SUBMITTABLE_TASK_STATUSES.contains(task.getStatus())) {
            throw new BusinessException(ResultCode.CLAIM_NOT_SUBMITTABLE,
                    "任务当前状态（" + task.getStatus() + "）不可提交凭证");
        }

        // ①-4 凭证文件校验：必须存在、属于本人、且未被内容安全判定违规
        validateFileIds(req.getFileIds(), userId);

        // ② 内容安全（文本；图片已在上传时通过 mediaCheckAsync 异步检测）——放在事务之外
        contentSecurityService.checkText(req.getContent(), "submission", userId);

        // ③ 事务内写入（独立 Bean 调用 → 代理生效）
        submissionTxService.persistSubmission(claimId, userId, req, httpReq);
    }

    /**
     * 查看接取详情（含最新凭证）。
     * 归属：claim.user_id == 当前用户 或 task.publisher_id == 当前用户。
     */
    public TaskClaimAndSubmission detail(Long claimId, Long currentUserId) {
        TaskClaim claim = claimService.requireClaim(claimId);
        Task task = taskMapper.selectById(claim.getTaskId());

        boolean isClaimer = claim.getUserId().equals(currentUserId);
        boolean isPublisher = task != null && task.getPublisherId().equals(currentUserId);
        if (!isClaimer && !isPublisher) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权查看该接取记录");
        }

        TaskSubmission latest = submissionMapper.selectLatestByClaimId(claimId);
        // 含签名文件 URL；授权已由上面的归属校验保证
        return new TaskClaimAndSubmission(claim, task, submissionViewAssembler.assemble(latest));
    }

    /**
     * 校验凭证引用的文件：必须存在（未逻辑删除）、属于当前用户、且未被内容安全判定违规。
     * 防「引用他人文件」与「引用违规文件绕过内容安全」。
     */
    private void validateFileIds(java.util.List<Long> fileIds, Long userId) {
        if (fileIds == null || fileIds.isEmpty()) {
            return;
        }
        java.util.Set<Long> distinct = new java.util.LinkedHashSet<>(fileIds);
        if (distinct.contains(null)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "fileIds 含空元素");
        }
        java.util.List<FileRecord> files = fileRecordMapper.selectBatchIds(distinct);
        if (files.size() != distinct.size()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "存在无效或已删除的 fileId");
        }
        for (FileRecord f : files) {
            if (!userId.equals(f.getUploaderId())) {
                throw new BusinessException(ResultCode.FORBIDDEN, "只能引用自己上传的文件");
            }
            if (Integer.valueOf(FileRecord.SEC_CHECK_REJECT).equals(f.getSecStatus())) {
                throw new BusinessException(ResultCode.FILE_CONTENT_ILLEGAL);
            }
        }
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

    /** 承载 claim + 关联任务 + 凭证的聚合视图 */
    @lombok.Value
    public static class TaskClaimAndSubmission {
        TaskClaim claim;
        Task task;
        SubmissionVO submission;
    }
}
