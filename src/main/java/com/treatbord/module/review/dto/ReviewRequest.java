package com.treatbord.module.review.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 审核请求（docs/API_DESIGN.md §6.1）。
 */
@Data
public class ReviewRequest {

    @NotBlank(message = "审核动作不能为空")
    @Pattern(regexp = "approve|reject", message = "审核动作仅支持 approve/reject")
    private String action;

    @Size(max = 200, message = "审核备注不能超过200字")
    private String note;
}