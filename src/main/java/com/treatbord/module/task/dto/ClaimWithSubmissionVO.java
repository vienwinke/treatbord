package com.treatbord.module.task.dto;

import com.treatbord.module.submission.dto.SubmissionVO;
import com.treatbord.module.task.entity.TaskClaim;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 任务下的接取视图（发布者审核视角，含最新凭证）。
 */
@Data
@Builder
public class ClaimWithSubmissionVO {

    private Long id;
    private Long taskId;
    private Long userId;
    private String userNickname;
    private String status;
    private BigDecimal reward;
    private LocalDateTime submittedAt;
    private LocalDateTime reviewedAt;
    private String reviewNote;
    private LocalDateTime createTime;

    /** 接取者最新提交的凭证 */
    private SubmissionVO submission;

    public static ClaimWithSubmissionVO from(TaskClaim c) {
        return ClaimWithSubmissionVO.builder()
                .id(c.getId())
                .taskId(c.getTaskId())
                .userId(c.getUserId())
                .status(c.getStatus())
                .reward(c.getReward())
                .submittedAt(c.getSubmittedAt())
                .reviewedAt(c.getReviewedAt())
                .reviewNote(c.getReviewNote())
                .createTime(c.getCreateTime())
                .build();
    }
}