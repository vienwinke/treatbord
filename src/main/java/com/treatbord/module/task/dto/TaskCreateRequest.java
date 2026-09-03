package com.treatbord.module.task.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 发布任务请求（docs/API_DESIGN.md §3.3）。
 */
@Data
public class TaskCreateRequest {

    @NotBlank(message = "标题不能为空")
    @Size(max = 50, message = "标题不能超过50字")
    private String title;

    @Size(max = 2000, message = "描述不能超过2000字")
    private String description;

    @NotNull(message = "报酬不能为空")
    @DecimalMin(value = "0.00", message = "报酬不能为负")
    @Digits(integer = 8, fraction = 2, message = "报酬最多2位小数")
    private BigDecimal reward;

    @NotNull(message = "名额不能为空")
    @Min(value = 1, message = "名额至少1个")
    @Max(value = 100, message = "名额最多100个")
    private Integer quota;

    @NotNull(message = "接取截止时间不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime claimDeadline;

    @NotNull(message = "完成截止时间不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime deadline;
}