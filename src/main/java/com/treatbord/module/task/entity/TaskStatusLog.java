package com.treatbord.module.task.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 任务状态审计（task_status_log 表）。
 */
@Data
@TableName("task_status_log")
public class TaskStatusLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskId;

    private String fromStatus;

    private String toStatus;

    private Long operatorId;

    private String reason;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}