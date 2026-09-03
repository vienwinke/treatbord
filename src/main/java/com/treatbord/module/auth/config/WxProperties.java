package com.treatbord.module.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 微信登录配置（application.yml: treatbord.wx）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "treatbord.wx")
public class WxProperties {

    /** 是否走真实微信登录；false 时使用本地测试 openid 映射 */
    private boolean enabled = false;

    private String appid;

    private String secret;

    private String code2sessionUrl;
}