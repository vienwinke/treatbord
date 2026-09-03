package com.treatbord.module.task.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 任务（task 表）。
 */
@Data
@TableName("task")
public class Task {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long publisherId;

    private String title;

    private String description;

    /** 报酬 */
    private BigDecimal reward;

    /** 名额 */
    private Integer quota;

    /** 已接取数（原子扣减） */
    private Integer claimedCount;

    private LocalDateTime claimDeadline;

    private LocalDateTime deadline;

    /** OPEN/IN_PROGRESS/REVIEWING/SETTLED/EXPIRED/CANCELLED */
    private String status;

    /** 乐观锁 */
    @Version
    private Integer version;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}