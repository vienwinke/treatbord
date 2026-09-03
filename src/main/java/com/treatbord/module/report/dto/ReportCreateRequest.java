package com.treatbord.module.report.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 举报请求（docs/API_DESIGN.md §10.1）。
 */
@Data
public class ReportCreateRequest {

    @NotBlank(message = "举报对象类型不能为空")
    @Pattern(regexp = "task|claim|review|user", message = "举报对象类型仅支持 task/claim/review/user")
    private String targetType;

    @NotNull(message = "举报对象 id 不能为空")
    private Long targetId;

    @NotBlank(message = "举报原因不能为空")
    @Size(max = 500, message = "举报原因不能超过500字")
    private String reason;
}