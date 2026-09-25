package com.treatbord.module.user.dto;

import lombok.Data;

/**
 * 设置/修改账密请求（POST /api/users/me/credentials，登录态）。
 */
@Data
public class SetCredentialsRequest {

    /** 登录账号；为空表示不改 */
    private String username;

    /** 密码；为空表示不改 */
    private String password;
}
