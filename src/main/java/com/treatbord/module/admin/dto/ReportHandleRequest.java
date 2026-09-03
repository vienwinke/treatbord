package com.treatbord.module.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 举报处理请求（docs/API_DESIGN.md §11）。
 */
@Data
public class ReportHandleRequest {

    /** 1=已处理 2=驳回 */
    @NotNull(message = "处理状态不能为空")
    @Min(value = 1, message = "状态仅支持 1=已处理 / 2=驳回")
    @Max(value = 2, message = "状态仅支持 1=已处理 / 2=驳回")
    private Integer status;

    private String note;
}