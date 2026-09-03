package com.treatbord.module.auth.service;

import com.treatbord.common.BusinessException;
import com.treatbord.common.ResultCode;
import com.treatbord.module.audit.service.AuditService;
import com.treatbord.module.auth.dto.LoginRequest;
import com.treatbord.module.auth.dto.LoginResponse;
import com.treatbord.module.user.entity.User;
import com.treatbord.module.user.service.UserService;
import com.treatbord.security.JwtUtil;
import com.treatbord.security.TokenBlacklistService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 认证服务：登录（code2session → 签发 JWT）、登出（jti 黑名单）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final WxAuthService wxAuthService;
    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final TokenBlacklistService tokenBlacklistService;
    private final AuditService auditService;
    private final StringRedisTemplate redisTemplate;

    /**
     * 微信登录：code → openid → 查/建用户 → 签发 token。
     */
    public LoginResponse login(LoginRequest req, HttpServletRequest httpReq) {
        // 1. code 换 openid
        String openid = wxAuthService.code2Openid(req.getCode());

        // 2. 查/建用户
        User user = userService.getOrCreate(openid);

        // 3. 封禁校验
        if (Integer.valueOf(1).equals(user.getStatus())) {
            auditService.recordLogin(user.getId(), false, "USER_BANNED", httpReq);
            throw new BusinessException(ResultCode.USER_BANNED);
        }

        // 4. 签发 JWT（含 userId/role/status/jti）
        String[] tokenAndJti = jwtUtil.generate(user.getId(), user.getRole(), user.getStatus());

        // 4.1 注册 jti 到用户会话集合（供封禁踢人下线：admin 按 user 扫描踢除）
        try {
            if (tokenAndJti[1] != null) {
                redisTemplate.opsForSet().add("user:" + user.getId() + ":jtis", tokenAndJti[1]);
                redisTemplate.expire("user:" + user.getId() + ":jtis",
                        java.time.Duration.ofSeconds(jwtUtil.expireSeconds()));
            }
        } catch (Exception e) {
            log.warn("jti 注册失败 userId={}", user.getId(), e);
        }

        // 5. 审计
        auditService.recordLogin(user.getId(), true, null, httpReq);
        auditService.record(user.getId(), "LOGIN", "user", user.getId(), "微信登录", httpReq);

        return LoginResponse.builder()
                .token(tokenAndJti[0])
                .expiresIn(jwtUtil.expireSeconds())
                .user(LoginResponse.UserView.from(user))
                .build();
    }

    /**
     * 登出：当前 token 的 jti 加入黑名单，并从用户会话集合移除。
     */
    public void logout(Long userId, String jti, HttpServletRequest httpReq) {
        if (jti != null) {
            tokenBlacklistService.blacklist(jti);
            try {
                redisTemplate.opsForSet().remove("user:" + userId + ":jtis", jti);
            } catch (Exception e) {
                log.warn("jti 移除失败 userId={}", userId, e);
            }
        }
        auditService.record(userId, "LOGOUT", "user", userId, "登出", httpReq);
    }

    /**
     * 注销账号：匿名化 openid + 当前 token 失效。
     */
    public void deleteAccount(Long userId, String jti, HttpServletRequest httpReq) {
        userService.deleteUser(userId);
        if (jti != null) {
            tokenBlacklistService.blacklist(jti);
        }
        auditService.record(userId, "DELETE_ACCOUNT", "user", userId, "注销账号(openid 已匿名化)", httpReq);
    }
}