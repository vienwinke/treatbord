package com.treatbord.module.settlement.entity;

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
 * 结算（settlement 表，MVP 预留：只跑状态位，不打款）。
 */
@Data
@TableName("settlement")
public class Settlement {

    /** 状态：0=待结算 1=已结算 */
    public static final int STATUS_PENDING = 0;
    public static final int STATUS_SETTLED = 1;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long claimId;

    private Long taskId;

    private Long userId;

    /** 金额 = claim.reward 结算快照 */
    private BigDecimal amount;

    private Integer status;

    private LocalDateTime settleTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}