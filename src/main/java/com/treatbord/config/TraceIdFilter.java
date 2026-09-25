package com.treatbord.config;

import com.treatbord.common.TraceContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 请求级 traceId：生成短 traceId → 写 MDC（贯穿日志）→ 写响应头 → 结束清理。
 * 优先级最早执行，保证后续日志都带 traceId。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Trace-Id";
    public static final String MDC_KEY = "traceId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = shortTraceId();
        MDC.put(MDC_KEY, traceId);
        TraceContext.set(traceId);
        response.setHeader(HEADER, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            TraceContext.clear();
            MDC.remove(MDC_KEY);
        }
    }

    private String shortTraceId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 24);
    }
}
