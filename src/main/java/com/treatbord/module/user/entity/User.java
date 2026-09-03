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