package com.treatbord.module.file.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文件（file 表）。凭证图/头像共用。
 * storage_key 格式：biz/{type}/{yyyyMM}/{uuid}.ext（禁用户文件名）。
 */
@Data
@TableName("file")
public class FileRecord {

    /** 内容安全状态常量 */
    public static final int SEC_CHECK_PENDING = 0; // 待检测
    public static final int SEC_CHECK_PASS = 1;    // 通过
    public static final int SEC_CHECK_REJECT = 2;  // 不通过

    @TableId(type = IdType.AUTO)
    private Long id;

    private String storageKey;

    private String url;

    private Long size;

    private String mime;

    private Long uploaderId;

    /** 内容安全状态（mediaCheckAsync 回填），0=待检 1=通过 2=违规 */
    private Integer secStatus;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}