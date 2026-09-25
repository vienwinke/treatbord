package com.treatbord.security;

import com.treatbord.common.BusinessException;
import com.treatbord.common.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 接口限流拦截器：对登录/接取/提交/上传按 IP 限流（阈值读 app_config）。
 * 注册于 AuthInterceptor 之前，匿名登录也在限流范围内。
 */
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimitService rateLimitService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String scope = resolveScope(request.getRequestURI());
        if (scope == null) {
            return true;
        }
        if (!rateLimitService.tryAcquire(scope, clientIp(request))) {
            throw new BusinessException(ResultCode.TOO_MANY_REQUESTS);
        }
        return true;
    }

    private String resolveScope(String uri) {
        if (uri.equals("/api/auth/login") || uri.equals("/api/auth/account/login")) {
            return "login";
        }
        if (uri.matches("/api/tasks/\\d+/claim")) {
            return "claim";
        }
        if (uri.matches("/api/claims/\\d+/submit")) {
            return "submit";
        }
        if (uri.equals("/api/files")) {
            return "upload";
        }
        return null;
    }

    private String clientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        return (xff == null || xff.isBlank()) ? req.getRemoteAddr() : xff.split(",")[0].trim();
    }
}
