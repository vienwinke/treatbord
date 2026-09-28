package com.treatbord.module.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改个人资料请求（PUT /api/users/me，登录态）。
 * 目前仅支持昵称；长度与 user.nickname varchar(30) 对齐。
 */
@Data
public class UpdateProfileRequest {

    /** 新昵称：1~30 个字符（首尾空白会被去除） */
    @NotBlank(message = "昵称不能为空")
    @Size(max = 30, message = "昵称最长 30 个字符")
    private String nickname;
}
