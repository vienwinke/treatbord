package com.treatbord.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 客户端 IP 解析：默认只信 TCP 对端地址（remoteAddr）。
 *
 * <p>仅当显式开启 {@code treatbord.security.trust-forwarded-for=true}
 * （服务置于可信代理之后，且代理会覆盖该请求头）时才读 X-Forwarded-For；
 * 否则客户端可伪造该头绕过按 IP 的限流与审计。
 */
@Component
public class ClientIpResolver {

    @Value("${treatbord.security.trust-forwarded-for:false}")
    private boolean trustForwardedFor;

    public String resolve(HttpServletRequest req) {
        if (req == null) {
            return null;
        }
        if (trustForwardedFor) {
            String xff = req.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) {
                return xff.split(",")[0].trim();
            }
        }
        return req.getRemoteAddr();
    }
}
