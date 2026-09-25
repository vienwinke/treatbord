package com.treatbord.module.report.dto;

import com.treatbord.module.report.entity.Report;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 举报视图（对外输出，隐藏 deleted 等内部字段）。
 */
@Data
@Builder
public class ReportVO {

    private Long id;
    private Long reporterId;
    private String targetType;
    private Long targetId;
    private String reason;
    /** 0=待处理 1=已处理 2=驳回 */
    private Integer status;
    private Long handlerId;
    private LocalDateTime createTime;

    public static ReportVO from(Report r) {
        if (r == null) {
            return null;
        }
        return ReportVO.builder()
                .id(r.getId())
                .reporterId(r.getReporterId())
                .targetType(r.getTargetType())
                .targetId(r.getTargetId())
                .reason(r.getReason())
                .status(r.getStatus())
                .handlerId(r.getHandlerId())
                .createTime(r.getCreateTime())
                .build();
    }
}