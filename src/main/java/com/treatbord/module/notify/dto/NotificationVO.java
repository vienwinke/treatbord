package com.treatbord.module.notify.dto;

import com.treatbord.module.notify.entity.Notification;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通知视图（对外输出，隐藏内部字段）。
 */
@Data
@Builder
public class NotificationVO {

    private Long id;
    private String type;
    private String title;
    private String content;
    private Long bizId;
    /** 0=未读 1=已读 */
    private Integer isRead;
    private LocalDateTime createTime;

    public static NotificationVO from(Notification n) {
        if (n == null) {
            return null;
        }
        return NotificationVO.builder()
                .id(n.getId())
                .type(n.getType())
                .title(n.getTitle())
                .content(n.getContent())
                .bizId(n.getBizId())
                .isRead(n.getIsRead())
                .createTime(n.getCreateTime())
                .build();
    }
}