package com.treatbord.module.audit.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 登录记录（login_log 表）。
 */
@Data
@TableName("login_log")
public class LoginLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String ip;

    private String userAgent;

    /** 0=失败 1=成功 */
    private Integer success;

    private String failReason;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}