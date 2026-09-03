package com.treatbord.module.user.controller;

import com.treatbord.common.Result;
import com.treatbord.module.auth.dto.LoginResponse;
import com.treatbord.module.auth.service.AuthService;
import com.treatbord.module.user.entity.User;
import com.treatbord.module.user.service.UserService;
import com.treatbord.security.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户接口（docs/API_DESIGN.md §2.3 / 2.4）。
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final AuthService authService;

    /** 2.3 获取当前用户信息 */
    @GetMapping("/me")
    public Result<LoginResponse.UserView> me() {
        User user = userService.getById(UserContext.userId());
        return Result.ok(LoginResponse.UserView.from(user));
    }

    /** 2.4 注销账号（合规强制：匿名化 openid） */
    @DeleteMapping("/me")
    public Result<Void> deleteMe(HttpServletRequest httpReq) {
        UserContext.CurrentUser current = UserContext.get();
        authService.deleteAccount(current.getUserId(), current.getJti(), httpReq);
        return Result.ok();
    }
}