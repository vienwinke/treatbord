package com.treatbord.module.auth.service;

import com.treatbord.common.BusinessException;
import com.treatbord.common.ResultCode;
import com.treatbord.module.auth.config.WxProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * 微信 code2session：用 wx.login 的 code 换取 openid。
 * WX_LOGIN_ENABLED=false 时（本地开发），code 直接映射为测试 openid，便于联调。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WxAuthService {

    private static final String TEST_OPENID_PREFIX = "openid_test_";

    private final WxProperties wxProperties;
    private final RestClient.Builder restClientBuilder;

    /**
     * 换取 openid。
     *
     * @param code wx.login 临时 code
     * @return openid
     */
    public String code2Openid(String code) {
        if (!wxProperties.isEnabled()) {
            // 本地测试模式：测试 openid 必须可复现（同 code 同 openid），方便重复登录
            String openid = TEST_OPENID_PREFIX + code;
            // 日志脱敏（docs/SECURITY_REVIEW.md D4）
            log.info("[WX-MOCK] code={} -> openid={}", code,
                    com.treatbord.common.MaskUtil.maskOpenid(openid));
            return openid;
        }

        // 真实模式：调用微信 code2session
        Map<?, ?> resp = restClientBuilder.build()
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path(wxProperties.getCode2sessionUrl())
                        .queryParam("appid", wxProperties.getAppid())
                        .queryParam("secret", wxProperties.getSecret())
                        .queryParam("js_code", code)
                        .queryParam("grant_type", "authorization_code")
                        .build())
                .retrieve()
                .body(Map.class);

        if (resp == null) {
            throw new BusinessException(ResultCode.WX_LOGIN_FAILED);
        }
        if (resp.containsKey("errcode") && !"0".equals(String.valueOf(resp.get("errcode")))) {
            // 只记录错误码与描述，避免打印完整响应（可能含 session_key 等敏感信息）
            log.warn("code2session 失败: errcode={} errmsg={}",
                    resp.get("errcode"), resp.get("errmsg"));
            throw new BusinessException(ResultCode.WX_LOGIN_FAILED);
        }
        String openid = (String) resp.get("openid");
        if (openid == null || openid.isBlank()) {
            throw new BusinessException(ResultCode.WX_LOGIN_FAILED);
        }
        return openid;
    }
}