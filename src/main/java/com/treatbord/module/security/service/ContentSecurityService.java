package com.treatbord.module.security.service;

import com.treatbord.common.BusinessException;
import com.treatbord.common.ResultCode;
import com.treatbord.module.config.service.AppConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 内容安全检测（docs/SECURITY_REVIEW.md §2.3）。
 * MVP：开关默认关闭（content.security.enabled=false 直接通过）；
 * 开启后接入微信 security.msgSecCheck（文本）。
 * 占位实现：可插拔，后续补真实微信调用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContentSecurityService {

    private final AppConfigService appConfigService;

    /**
     * 文本内容安全检测：违规抛 CONTENT_ILLEGAL。
     */
    public void checkText(String content, String scene) {
        if (content == null || content.isBlank()) {
            return;
        }
        boolean enabled = appConfigService.getBool("content.security.enabled",
                true);
        if (!enabled) {
            return;
        }
        // TODO: 接入微信 security.msgSecCheck（openid + scene + content）
        // MVP 占位：默认通过，仅记录
        log.debug("[CONTENT-SEC] scene={} len={} (mock 通过)", scene, content.length());
    }

    /**
     * 图片内容安全检测：违规抛 CONTENT_ILLEGAL。
     * MVP 占位：直接通过（真实接入 mediaCheckAsync 见 P3 文件模块）。
     */
    public void checkImage(Long fileId) {
        boolean enabled = appConfigService.getBool("content.security.enabled", true);
        if (!enabled) {
            return;
        }
        // TODO: 接入微信 mediaCheckAsync（异步） —— P3 文件模块实现回查闭环
        log.debug("[CONTENT-SEC] image fileId={} (mock 通过)", fileId);
    }
}