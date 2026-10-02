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

    /**
     * 微信「消息推送」配置里填的 Token，用于校验回调签名（明文模式）。
     * 为空时 /wx/message-push 一律拒收（fail-closed）——
     * 绝不能因为没配 Token 就放出一个匿名可写库的回调入口。
     */
    private String messagePushToken;
}