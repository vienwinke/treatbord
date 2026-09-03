package com.treatbord.module.auth.controller;

import com.treatbord.common.Result;
import com.treatbord.module.auth.dto.LoginRequest;
import com.treatbord.module.auth.dto.LoginResponse;
import com.treatbord.module.auth.service.AuthService;
import com.treatbord.security.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口（docs/API_DESIGN.md §2）。
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** 2.1 用户登录 */
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest req,
                                       HttpServletRequest httpReq) {
        return Result.ok(authService.login(req, httpReq));
    }

    /** 2.2 退出登录（jti 入黑名单） */
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest httpReq) {
        UserContext.CurrentUser current = UserContext.get();
        authService.logout(current.getUserId(), current.getJti(), httpReq);
        return Result.ok();
    }
}