package com.treatbord.module.user.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户（user 表）。
 */
@Data
@TableName("user")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 微信 openid（注销时匿名化为 DEL_<uuid>） */
    private String openid;

    /** 登录账号（唯一；微信登录自动生成或用户自填） */
    private String username;

    /** 密码哈希（SHA-256 + 随机盐 + pepper；null 表示未设置账密） */
    private String passwordHash;

    /** 预留 */
    private String unionid;

    private String nickname;

    private String avatar;

    /** 信用分，默认 100 */
    private Integer creditScore;

    /** 0=USER 1=ADMIN */
    private Integer role;

    /** 0=正常 1=封禁 */
    private Integer status;

    private LocalDateTime registerTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}