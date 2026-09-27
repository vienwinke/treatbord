package com.treatbord.module.file.service;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * 文件访问签名（签名 URL）。
 *
 * <p>作用：给 /files/** 这个"带不了 Authorization 头"的直读路径提供
 * 授权 + 时效 + 防篡改：
 * <pre>
 *   sig = Base64Url( HMAC-SHA256(secret, storageKey + ":" + expireAtEpochSecond) )
 *   url = {base}/biz/.../xxx.png?exp=1700000000&amp;sig=xxxx
 * </pre>
 * 校验：未过期 且 HMAC 一致（常量时间比较）。
 *
 * <p>密钥/开关：
 * <ul>
 *   <li>treatbord.storage.sign-secret：签名密钥（生产必须环境变量注入，StartupValidator 校验）</li>
 *   <li>treatbord.storage.signed-url.required：是否强制校验（dev 默认 false 便于联调，prod 必须 true）</li>
 *   <li>treatbord.storage.signed-url.ttl-seconds：有效期，默认 1800s（30 分钟）
 *       —— 覆盖用户浏览/审核的停留时长；泄露风险由"短于会话有效期 + 可换密钥全局失效"控制</li>
 * </ul>
 *
 * <p>注意：签名**不是加密**——不隐藏文件内容，只证明"该 URL 由服务端签发、未过期、未被篡改"。
 */
@Component
@RequiredArgsConstructor
public class FileUrlSigner {

    private static final String DEV_DEFAULT_SECRET = "dev-sign-secret-change-me";
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final Environment env;

    /** 是否需要强制校验签名（生产 true） */
    public boolean required() {
        return Boolean.parseBoolean(env.getProperty("treatbord.storage.signed-url.required", "false"));
    }

    public long ttlSeconds() {
        return Long.parseLong(env.getProperty("treatbord.storage.signed-url.ttl-seconds", "1800"));
    }

    /** 生成签名 URL；base 优先按"当前请求的 Host"推导，解决 dev 下 127.0.0.1 写死的问题 */
    public String signedUrl(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            return null;
        }
        long exp = System.currentTimeMillis() / 1000 + ttlSeconds();
        return baseUrl() + "/" + storageKey + "?exp=" + exp + "&sig=" + sign(storageKey, exp);
    }

    /** 校验签名：未过期 + 常量时间比较 */
    public boolean verify(String storageKey, String expStr, String sig) {
        if (storageKey == null || expStr == null || sig == null || sig.isBlank()) {
            return false;
        }
        long exp;
        try {
            exp = Long.parseLong(expStr.trim());
        } catch (NumberFormatException e) {
            return false;
        }
        if (exp < System.currentTimeMillis() / 1000) {
            return false;
        }
        byte[] expected = sign(storageKey, exp).getBytes(StandardCharsets.UTF_8);
        byte[] actual = sig.trim().getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual);
    }

    String sign(String storageKey, long expireAtSeconds) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] raw = mac.doFinal((storageKey + ":" + expireAtSeconds).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        } catch (Exception e) {
            throw new IllegalStateException("文件签名失败", e);
        }
    }

    private String secret() {
        String s = env.getProperty("treatbord.storage.sign-secret", DEV_DEFAULT_SECRET);
        return (s == null || s.isBlank()) ? DEV_DEFAULT_SECRET : s;
    }

    private String baseUrl() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest req = attrs.getRequest();
                String host = req.getHeader("Host");
                if (host != null && !host.isBlank()) {
                    return req.getScheme() + "://" + host + req.getContextPath() + "/files";
                }
            }
        } catch (Exception ignored) {
            // 无请求上下文（如定时任务）时回落到配置
        }
        String configured = env.getProperty("treatbord.storage.base-url", "");
        return (configured == null || configured.isBlank()) ? "http://127.0.0.1:8080/files" : configured;
    }
}
