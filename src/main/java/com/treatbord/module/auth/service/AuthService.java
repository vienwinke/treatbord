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
 * 认证服务：登录（微信 code2session / 账密 → 签发 JWT）、登出（jti 黑名单）、注销。
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
        String openid = wxAuthService.code2Openid(req.getCode());
        User user = userService.getOrCreate(openid);
        // 封禁校验
        if (Integer.valueOf(1).equals(user.getStatus())) {
            auditService.recordLogin(user.getId(), false, "USER_BANNED", httpReq);
            throw new BusinessException(ResultCode.USER_BANNED);
        }
        return issueToken(user, "微信登录", httpReq);
    }

    /**
     * 账密登录：账号 + 密码 → 校验 → 签发 token（与微信登录同一用户，openid 绑定）。
     */
    public LoginResponse accountLogin(String username, String password, HttpServletRequest httpReq) {
        User user = userService.verifyAccount(username, password);
        if (Integer.valueOf(1).equals(user.getStatus())) {
            auditService.recordLogin(user.getId(), false, "USER_BANNED", httpReq);
            throw new BusinessException(ResultCode.USER_BANNED);
        }
        return issueToken(user, "账密登录", httpReq);
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
     * 注销账号：匿名化 openid + **撤销该用户全部会话**
     * （只拉黑当前 token 会让其它设备继续可用到 7 天过期）。
     */
    public void deleteAccount(Long userId, String jti, HttpServletRequest httpReq) {
        userService.deleteUser(userId);
        if (jti != null) {
            tokenBlacklistService.blacklist(jti);
        }
        revokeAllSessions(userId);
        auditService.record(userId, "DELETE_ACCOUNT", "user", userId, "注销账号(openid 已匿名化)", httpReq);
    }

    /** 撤销某用户全部会话：读取登录时登记的 jti 集合，逐个拉黑后删除集合。 */
    private void revokeAllSessions(Long userId) {
        String key = "user:" + userId + ":jtis";
        try {
            var jtis = redisTemplate.opsForSet().members(key);
            if (jtis != null) {
                for (Object j : jtis) {
                    tokenBlacklistService.blacklist(String.valueOf(j));
                }
            }
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("撤销全部会话失败 userId={}", userId, e);
        }
    }

    /** 签发 JWT + 注册 jti + 审计，返回登录响应。 */
    private LoginResponse issueToken(User user, String method, HttpServletRequest httpReq) {
        String[] tokenAndJti = jwtUtil.generate(user.getId(), user.getRole(), user.getStatus());
        try {
            if (tokenAndJti[1] != null) {
                redisTemplate.opsForSet().add("user:" + user.getId() + ":jtis", tokenAndJti[1]);
                redisTemplate.expire("user:" + user.getId() + ":jtis",
                        java.time.Duration.ofSeconds(jwtUtil.expireSeconds()));
            }
        } catch (Exception e) {
            log.warn("jti 注册失败 userId={}", user.getId(), e);
        }
        auditService.recordLogin(user.getId(), true, null, httpReq);
        auditService.record(user.getId(), "LOGIN", "user", user.getId(), method, httpReq);
        return LoginResponse.builder()
                .token(tokenAndJti[0])
                .expiresIn(jwtUtil.expireSeconds())
                .user(LoginResponse.UserView.from(user))
                .build();
    }
}
