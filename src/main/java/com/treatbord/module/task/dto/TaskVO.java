package com.treatbord.module.task.dto;

import com.treatbord.module.task.entity.Task;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 任务视图（列表/详情通用）。
 */
@Data
@Builder
public class TaskVO {

    private Long id;
    private String title;
    private String description;
    private BigDecimal reward;
    private Integer quota;
    private Integer claimedCount;
    private LocalDateTime claimDeadline;
    private LocalDateTime deadline;
    private String status;
    private LocalDateTime createTime;

    /** 发布者信息（级联字段） */
    private Long publisherId;
    private String publisherNickname;

    /** 当前用户是否已接取（详情接口登录态返回） */
    private Boolean claimedByMe;

    public static TaskVO from(Task t) {
        return TaskVO.builder()
                .id(t.getId())
                .title(t.getTitle())
                .description(t.getDescription())
                .reward(t.getReward())
                .quota(t.getQuota())
                .claimedCount(t.getClaimedCount())
                .claimDeadline(t.getClaimDeadline())
                .deadline(t.getDeadline())
                .status(t.getStatus())
                .createTime(t.getCreateTime())
                .publisherId(t.getPublisherId())
                .build();
    }
}