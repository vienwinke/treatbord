package com.treatbord.module.report.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 举报（report 表）。举报闭环：待处理→已处理/驳回。
 */
@Data
@TableName("report")
public class Report {

    /** 状态：0=待处理 1=已处理 2=驳回 */
    public static final int STATUS_PENDING = 0;
    public static final int STATUS_HANDLED = 1;
    public static final int STATUS_REJECTED = 2;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long reporterId;

    /** 被举报对象类型：task/claim/review/user */
    private String targetType;

    private Long targetId;

    private String reason;

    private Integer status;

    /** 处理人（admin） */
    private Long handlerId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}