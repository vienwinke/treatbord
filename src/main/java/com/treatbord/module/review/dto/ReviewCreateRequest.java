package com.treatbord.module.review.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 互评请求（docs/API_DESIGN.md §9.1）。
 */
@Data
public class ReviewCreateRequest {

    @NotNull(message = "claimId 不能为空")
    private Long claimId;

    @NotNull(message = "被评价用户不能为空")
    private Long toUserId;

    @NotNull(message = "评分不能为空")
    @Min(value = 1, message = "评分 1-5")
    @Max(value = 5, message = "评分 1-5")
    private Integer score;

    @Size(max = 500, message = "评价内容不能超过500字")
    private String content;
}