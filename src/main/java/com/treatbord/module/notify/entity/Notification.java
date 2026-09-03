package com.treatbord.module.notify.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 站内通知（notification 表）。
 */
@Data
@TableName("notification")
public class Notification {

    /** 通知类型常量 */
    public static final String TYPE_CLAIMED = "CLAIMED";        // 有人接取了你的任务
    public static final String TYPE_SUBMITTED = "SUBMITTED";    // 有人提交了凭证
    public static final String TYPE_REVIEW_RESULT = "REVIEW_RESULT"; // 审核结果
    public static final String TYPE_AUTO_APPROVED = "AUTO_APPROVED"; // 超时自动通过
    public static final String TYPE_TASK_EXPIRED = "TASK_EXPIRED";   // 任务过期
    public static final String TYPE_TASK_OFFLINE = "TASK_OFFLINE";   // 管理端下架

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String type;

    private String title;

    private String content;

    /** 关联业务 id（任务/接取 id） */
    private Long bizId;

    /** 0=未读 1=已读 */
    private Integer isRead;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}