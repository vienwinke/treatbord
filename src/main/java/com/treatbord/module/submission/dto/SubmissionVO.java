package com.treatbord.module.submission.dto;

import com.treatbord.module.submission.entity.TaskSubmission;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 凭证视图（含文件 URL 列表，联查 file 表）。
 */
@Data
@Builder
public class SubmissionVO {

    private Long id;
    private Long claimId;
    private String content;

    /** 文件 URL 列表（由 fileIds → file 表解析） */
    private java.util.List<String> fileUrls;

    private java.util.List<Long> fileIds;
    private LocalDateTime submitTime;

    public static SubmissionVO from(TaskSubmission s) {
        if (s == null) {
            return null;
        }
        return SubmissionVO.builder()
                .id(s.getId())
                .claimId(s.getClaimId())
                .content(s.getContent())
                .submitTime(s.getSubmitTime())
                .build();
    }
}