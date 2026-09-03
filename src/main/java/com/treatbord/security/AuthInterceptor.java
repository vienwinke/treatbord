package com.treatbord.security;

import com.treatbord.common.BusinessException;
import com.treatbord.common.ResultCode;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 登录鉴权拦截器：
 * 1. 白名单路径（登录接口等）直接放行；
 * 2. 校验 Authorization: Bearer <JWT>；
 * 3. 校验 jti 是否在黑名单（登出/封禁）；
 * 4. 填充 UserContext；@RequireAdmin 标注的接口额外校验 ADMIN。
 */
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;
    private final TokenBlacklistService blacklistService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 放行 CORS 预检
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }

        String header = request.getHeader("Authorization");

        // 匿名可浏览：GET /api/tasks 与 GET /api/tasks/{id}（有 token 则继续走鉴权填充上下文）
        if (header == null && isPublicGet(request)) {
            return true;
        }

        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        String token = header.substring(BEARER_PREFIX.length());

        Claims claims;
        try {
            claims = jwtUtil.parse(token);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }

        String jti = claims.getId();
        if (blacklistService.isBlacklisted(jti)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "登录已失效，请重新登录");
        }

        Long userId = claims.get("userId", Long.class);
        Integer role = claims.get("role", Integer.class);
        Integer status = claims.get("status", Integer.class);

        // 封禁踢人：token 中 status=1 直接拒绝
        if (Integer.valueOf(1).equals(status)) {
            throw new BusinessException(ResultCode.USER_BANNED);
        }

        UserContext.set(new UserContext.CurrentUser(userId, role, status, jti));

        // 是否需要 ADMIN 权限（处理器上的 @RequireAdmin）
        if (handler instanceof org.springframework.web.method.HandlerMethod hm) {
            if (hm.hasMethodAnnotation(RequireAdmin.class)
                    || hm.getBeanType().isAnnotationPresent(RequireAdmin.class)) {
                if (!UserContext.isAdmin()) {
                    throw new BusinessException(ResultCode.FORBIDDEN);
                }
            }
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        UserContext.clear();
    }

    /** 匿名可浏览的 GET 路径（任务列表/详情） */
    private boolean isPublicGet(HttpServletRequest request) {
        if (!HttpMethod.GET.matches(request.getMethod())) {
            return false;
        }
        String uri = request.getRequestURI();
        // /api/tasks 或 /api/tasks/{id}（不含 /api/tasks/{id}/claim 等写操作子路径）
        if (uri.matches("/api/tasks(?:/\\d+)?/?")) {
            return true;
        }
        return false;
    }
}