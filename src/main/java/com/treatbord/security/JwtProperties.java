package com.treatbord.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT 配置项（application.yml: treatbord.jwt）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "treatbord.jwt")
public class JwtProperties {

    /** 签名密钥（≥32 字节，生产走环境变量） */
    private String secret;

    /** 过期时间（秒），默认 7 天 */
    private long expireSeconds = 604800L;

    /** 签发者 */
    private String issuer = "treatbord";
}