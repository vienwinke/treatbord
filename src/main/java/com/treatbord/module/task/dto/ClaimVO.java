package com.treatbord.module.task.dto;

import com.treatbord.module.task.entity.TaskClaim;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 接取视图（我的接取列表用，含任务摘要）。
 */
@Data
@Builder
public class ClaimVO {

    private Long id;
    private Long taskId;
    private Long userId;
    private String status;
    private BigDecimal reward;
    private LocalDateTime createTime;

    /** 任务摘要（联查） */
    private String taskTitle;
    private LocalDateTime taskDeadline;
    private String taskStatus;

    public static ClaimVO fromClaim(TaskClaim c) {
        return ClaimVO.builder()
                .id(c.getId())
                .taskId(c.getTaskId())
                .userId(c.getUserId())
                .status(c.getStatus())
                .reward(c.getReward())
                .createTime(c.getCreateTime())
                .build();
    }
}