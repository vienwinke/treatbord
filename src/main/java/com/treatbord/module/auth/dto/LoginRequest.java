package com.treatbord.module.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 登录请求（POST /api/auth/login）。
 */
@Data
public class LoginRequest {

    /** wx.login 返回的临时 code */
    @NotBlank(message = "code 不能为空")
    private String code;
}