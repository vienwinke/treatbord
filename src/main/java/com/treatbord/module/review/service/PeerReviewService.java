package com.treatbord.module.review.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.treatbord.common.BusinessException;
import com.treatbord.common.ResultCode;
import com.treatbord.module.audit.service.AuditService;
import com.treatbord.module.review.dto.ReviewCreateRequest;
import com.treatbord.module.review.entity.Review;
import com.treatbord.module.review.mapper.ReviewMapper;
import com.treatbord.module.security.service.ContentSecurityService;
import com.treatbord.module.task.entity.Task;
import com.treatbord.module.task.entity.TaskClaim;
import com.treatbord.module.task.enums.ClaimStatus;
import com.treatbord.module.task.mapper.TaskClaimMapper;
import com.treatbord.module.task.mapper.TaskMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 互评服务（docs/API_DESIGN.md §9.1）。
 */
@Service
@RequiredArgsConstructor
public class PeerReviewService {

    private final ReviewMapper reviewMapper;
    private final TaskClaimMapper taskClaimMapper;
    private final TaskMapper taskMapper;
    private final ContentSecurityService contentSecurityService;
    private final AuditService auditService;

    /**
     * 提交互评：claim 双方、任务完成后可评；唯一约束防重复。
     */
    public void create(ReviewCreateRequest req, Long fromUserId, HttpServletRequest httpReq) {
        // 1. 归属校验：仅 claim 双方（接取者 / 发布者）可参与互评
        TaskClaim claim = taskClaimMapper.selectById(req.getClaimId());
        if (claim == null) {
            throw new BusinessException(ResultCode.CLAIM_NOT_FOUND);
        }
        Task task = taskMapper.selectById(claim.getTaskId());
        boolean isClaimer = claim.getUserId().equals(fromUserId);
        boolean isPublisher = task != null && task.getPublisherId().equals(fromUserId);
        if (!isClaimer && !isPublisher) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权评价该接取");
        }
        // 被评人必须是另一方
        boolean toIsOther = req.getToUserId().equals(claim.getUserId())
                || (task != null && req.getToUserId().equals(task.getPublisherId()));
        if (!toIsOther || req.getToUserId().equals(fromUserId)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "评价对象必须是接取另一方");
        }

        // 2. 任务完成后才可评
        boolean completed = claim.getStatus().equals(ClaimStatus.APPROVED.name())
                || (task != null && "SETTLED".equals(task.getStatus()));
        if (!completed) {
            throw new BusinessException(ResultCode.CONFLICT, "任务完成后才能互评");
        }

        // 3. 内容安全
        contentSecurityService.checkText(req.getContent(), "review");

        // 4. 入库（唯一索引防重复，冲突抛 409）
        Review review = new Review();
        review.setClaimId(req.getClaimId());
        review.setFromUserId(fromUserId);
        review.setToUserId(req.getToUserId());
        review.setScore(req.getScore());
        review.setContent(req.getContent());
        reviewMapper.insert(review);

        auditService.record(fromUserId, "REVIEW", "claim", req.getClaimId(),
                "互评 score=" + req.getScore(), httpReq);
    }
}