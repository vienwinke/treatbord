package com.treatbord.module.admin.dto;

import com.treatbord.module.audit.entity.AuditLog;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 审计日志视图（管理端查询用；不暴露实体）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogVO {

    private Long id;
    private Long userId;
    private String action;
    private String targetType;
    private Long targetId;
    private String detail;
    private String ip;
    private LocalDateTime createTime;

    public static AuditLogVO from(AuditLog e) {
        if (e == null) {
            return null;
        }
        return AuditLogVO.builder()
                .id(e.getId())
                .userId(e.getUserId())
                .action(e.getAction())
                .targetType(e.getTargetType())
                .targetId(e.getTargetId())
                .detail(e.getDetail())
                .ip(e.getIp())
                .createTime(e.getCreateTime())
                .build();
    }
}
