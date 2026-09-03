package com.treatbord.module.submission.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 提交凭证（task_submission 表）。
 * 评审前可覆盖提交（MVP 保留最新一条有效提交）。
 */
@Data
@TableName("task_submission")
public class TaskSubmission {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long claimId;

    /** 文字凭证 ≤2000 */
    private String content;

    /** 凭证图片 file.id 数组（JSON） */
    private String fileIds;

    private LocalDateTime submitTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}