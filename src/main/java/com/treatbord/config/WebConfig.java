package com.treatbord.config;

import com.treatbord.security.AuthInterceptor;
import com.treatbord.security.RateLimitInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 配置：注册限流 + 鉴权拦截器 + 放行白名单。
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final RateLimitInterceptor rateLimitInterceptor;
    private final AuthInterceptor authInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 限流在前：登录/接取/提交/上传（按 IP），匿名也受限
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/api/**", "/files/**")
                .order(1);

        // 鉴权在后
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        // 登录接口（匿名）
                        "/api/auth/login",
                        "/api/auth/account/login",
                        // 静态资源与文档
                        "/swagger-ui.html",
                        "/swagger-ui/**",
                        "/v3/api-docs",
                        "/v3/api-docs/**",
                        "/error"
                )
                .order(2);
    }
}