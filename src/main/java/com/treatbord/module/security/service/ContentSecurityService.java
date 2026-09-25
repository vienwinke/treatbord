package com.treatbord.module.security.service;

import com.treatbord.common.BusinessException;
import com.treatbord.common.ResultCode;
import com.treatbord.module.config.service.AppConfigService;
import com.treatbord.module.user.entity.User;
import com.treatbord.module.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * 内容安全检测（真实接入微信，docs/STANDARDIZATION_PLAN.md P0-5 / SECURITY_REVIEW §2.3）。
 *
 * <ul>
 *   <li><b>文本</b>：{@code wxa/msg_sec_check}（同步，suggest=risky 直接拦截）</li>
 *   <li><b>图片</b>：{@code wxa/media_check_async}（异步，返回 trace_id 供回查）</li>
 * </ul>
 *
 * <p>开关：{@code content.security.enabled}（app_config）→ 缺省回落 yml
 * {@code treatbord.security.content-check-enabled}（dev 关闭 / prod 强制开启）。
 * 降级：开关关闭或微信接口异常时放行并告警（fail-open，避免检测故障拖垮主链路），
 * 可用 app_config {@code content.check.fail-open=false} 改为强拦截。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContentSecurityService {

    private static final String MSG_SEC_CHECK_URL = "https://api.weixin.qq.com/wxa/msg_sec_check";
    private static final String MEDIA_CHECK_URL = "https://api.weixin.qq.com/wxa/media_check_async";

    /** 微信 scene：1=资料 2=评论 3=论坛 4=社交日志 */
    private static final int DEFAULT_SCENE = 2;

    private final AppConfigService appConfigService;
    private final WxAccessTokenService accessTokenService;
    private final UserMapper userMapper;
    private final RestClient.Builder restClientBuilder;
    private final Environment env;

    /**
     * 文本内容安全检测：违规抛 CONTENT_ILLEGAL。
     *
     * @param content 待检测文本
     * @param scene   业务场景（仅日志标识）
     * @param userId  用户 id（用于取 openid，微信风控要求）
     */
    public void checkText(String content, String scene, Long userId) {
        if (content == null || content.isBlank() || !enabled()) {
            return;
        }
        try {
            String openid = resolveOpenid(userId);
            Map<?, ?> resp = restClientBuilder.build()
                    .post()
                    .uri(uriBuilder -> uriBuilder
                            .path(MSG_SEC_CHECK_URL)
                            .queryParam("access_token", accessTokenService.getAccessToken())
                            .build())
                    .body(Map.of(
                            "version", 2,
                            "openid", openid == null ? "" : openid,
                            "scene", DEFAULT_SCENE,
                            "content", content))
                    .retrieve()
                    .body(Map.class);

            if (resp == null) {
                degrade(scene, "微信无响应", null);
                return;
            }
            Object errcode = resp.get("errcode");
            if (errcode != null && !"0".equals(String.valueOf(errcode))) {
                degrade(scene, "微信返回错误 " + errcode, null);
                return;
            }
            String suggest = extractSuggest(resp);
            if ("risky".equals(suggest)) {
                log.warn("[CONTENT-SEC] 文本违规拦截 scene={} userId={} label={}",
                        scene, userId, extractLabel(resp));
                throw new BusinessException(ResultCode.CONTENT_ILLEGAL);
            }
            if ("review".equals(suggest)) {
                log.info("[CONTENT-SEC] 文本待人工复核 scene={} userId={} label={}",
                        scene, userId, extractLabel(resp));
            }
        } catch (BusinessException e) {
            // 仅"违规拦截"上抛；其他业务异常（如 access_token 配置缺失）走降级
            if (e.getCode() == ResultCode.CONTENT_ILLEGAL.getCode()) {
                throw e;
            }
            degrade(scene, "检测服务异常: " + e.getMessage(), e);
        } catch (Exception e) {
            degrade(scene, "调用异常", e);
        }
    }

    /**
     * 图片内容安全检测（异步提交）：结果由定时任务回查/回调回填。
     *
     * @param fileId   文件 id（日志用）
     * @param mediaUrl 图片公网 HTTPS 地址（本地存储无公网地址时自动跳过）
     * @param userId   上传者 id
     */
    public void checkImage(Long fileId, String mediaUrl, Long userId) {
        if (fileId == null || !enabled()) {
            return;
        }
        if (mediaUrl == null || !mediaUrl.startsWith("https://")) {
            log.debug("[CONTENT-SEC] 图片无公网 HTTPS 地址，跳过异步检测 fileId={}", fileId);
            return;
        }
        try {
            String openid = resolveOpenid(userId);
            Map<?, ?> resp = restClientBuilder.build()
                    .post()
                    .uri(uriBuilder -> uriBuilder
                            .path(MEDIA_CHECK_URL)
                            .queryParam("access_token", accessTokenService.getAccessToken())
                            .build())
                    .body(Map.of(
                            "media_url", mediaUrl,
                            "media_type", 2,
                            "version", 2,
                            "openid", openid == null ? "" : openid,
                            "scene", DEFAULT_SCENE))
                    .retrieve()
                    .body(Map.class);

            if (resp != null && "0".equals(String.valueOf(resp.get("errcode")))) {
                log.info("[CONTENT-SEC] 图片检测已提交 fileId={} traceId={}", fileId, resp.get("trace_id"));
            } else {
                degrade("image", "图片检测提交失败", null);
            }
        } catch (Exception e) {
            degrade("image", "调用异常", e);
        }
    }

    // ---------- 内部 ----------

    private boolean enabled() {
        // 由环境配置决定（dev=false / prod=true）；不读 app_config，避免数据库默认值干扰环境开关
        return Boolean.parseBoolean(
                env.getProperty("treatbord.security.content-check-enabled", "true"));
    }

    /** 降级：默认放行并告警（fail-open） */
    private void degrade(String scene, String reason, Exception e) {
        boolean failOpen = appConfigService.getBool("content.check.fail-open", true);
        if (failOpen) {
            if (e != null) {
                log.warn("[CONTENT-SEC] 检测降级放行 scene={} reason={}", scene, reason, e);
            } else {
                log.warn("[CONTENT-SEC] 检测降级放行 scene={} reason={}", scene, reason);
            }
        } else {
            throw new BusinessException(ResultCode.CONTENT_ILLEGAL, "内容安全检测不可用，请稍后重试");
        }
    }

    private String resolveOpenid(Long userId) {
        if (userId == null) {
            return null;
        }
        User u = userMapper.selectById(userId);
        return u == null ? null : u.getOpenid();
    }

    private String extractSuggest(Map<?, ?> resp) {
        Object result = resp.get("result");
        if (result instanceof Map<?, ?> r) {
            Object suggest = r.get("suggest");
            return suggest == null ? null : String.valueOf(suggest);
        }
        return null;
    }

    private Object extractLabel(Map<?, ?> resp) {
        Object result = resp.get("result");
        if (result instanceof Map<?, ?> r) {
            return r.get("label");
        }
        return null;
    }
}