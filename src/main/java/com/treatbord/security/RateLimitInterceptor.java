package com.treatbord.security;

import com.treatbord.common.BusinessException;
import com.treatbord.common.ResultCode;
import com.treatbord.config.BusinessMetrics;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpMethod;
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
    private final ClientIpResolver clientIpResolver;
    private final BusinessMetrics businessMetrics;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String scope = resolveScope(request);
        if (scope == null) {
            return true;
        }
        if (!rateLimitService.tryAcquire(scope, clientIpResolver.resolve(request))) {
            businessMetrics.rateLimitRejected(scope);
            throw new BusinessException(ResultCode.TOO_MANY_REQUESTS);
        }
        return true;
    }

    /**
     * 解析限流场景；返回 null 表示不限流。
     * 注意：{@code /api/tasks} 需要区分方法——GET 是公开浏览（不限流），POST 是发布（限流）。
     */
    private String resolveScope(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String method = request.getMethod();

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
        // 发帖/举报/互评：写操作限流（防刷、反垃圾）
        if (HttpMethod.POST.matches(method) && uri.equals("/api/tasks")) {
            return "taskcreate";
        }
        if (HttpMethod.POST.matches(method) && uri.equals("/api/reports")) {
            return "report";
        }
        if (HttpMethod.POST.matches(method) && uri.equals("/api/reviews")) {
            return "review";
        }
        // 通知查询/已读（小程序会轮询）
        if (uri.startsWith("/api/notifications")) {
            return "notify";
        }
        // 凭证/头像直读路径（无鉴权）也要有限流兜底
        if (uri.startsWith("/files/")) {
            return "fileview";
        }
        return null;
    }
}
