package com.treatbord.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 慢接口日志：统计请求处理耗时，超过阈值打 WARN。
 * 阈值：{@code treatbord.observability.slow-api-ms}（默认 500ms）。
 *
 * <p>用 Filter 而非拦截器，是为了把 /files/** 直读、/actuator/** 也纳入统计（拦截器只覆盖 /api/**）。
 */
@Slf4j
@Component
public class SlowRequestLoggingFilter extends OncePerRequestFilter {

    @Value("${treatbord.observability.slow-api-ms:500}")
    private long slowApiMs;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long start = System.currentTimeMillis();
        try {
            chain.doFilter(request, response);
        } finally {
            long cost = System.currentTimeMillis() - start;
            if (cost >= slowApiMs) {
                log.warn("[SLOW-API] {} ms {} {} status={}",
                        cost, request.getMethod(), request.getRequestURI(), response.getStatus());
            }
        }
    }
}
