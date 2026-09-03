package com.treatbord.module.task.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 接取记录（task_claim 表）。
 */
@Data
@TableName("task_claim")
public class TaskClaim {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskId;

    private Long userId;

    /** CLAIMED/SUBMITTED/APPROVED/REJECTED/CANCELLED */
    private String status;

    /** 结算快照（接取时固化） */
    private BigDecimal reward;

    private LocalDateTime submittedAt;

    private LocalDateTime reviewedAt;

    private LocalDateTime reviewDeadline;

    private String reviewNote;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}