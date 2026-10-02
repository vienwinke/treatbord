package com.treatbord.module.security.controller;

import com.treatbord.module.auth.config.WxProperties;
import com.treatbord.module.security.service.WxMessagePushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 微信消息推送回调入口（内容安全异步结果）。
 *
 * <p>路径刻意放在 {@code /wx/**} 而不是 {@code /api/**}：
 * 鉴权拦截器与限流拦截器都只注册在 /api/**（见 WebConfig），
 * 微信不可能带 JWT 来，也不该被按 IP 限流（微信出口 IP 很少，限流会掐掉正常推送）。
 * 它的安全性由**签名校验**保证，而不是拦截器。
 *
 * <p>响应约定：验签通过后一律回 {@code success}（微信据此停止重推）；
 * 事件不认识、报文不合规都记日志后返回 success —— 重推不会让坏报文变好。
 * 但**验签失败 / Token 未配置**必须返回错误码，不能假装成功。
 */
@Slf4j
@RestController
@RequestMapping("/wx")
@RequiredArgsConstructor
public class WxMessagePushController {

    private final WxMessagePushService pushService;
    private final WxProperties wxProperties;

    @PostMapping(value = "/message-push", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> receive(@RequestParam(required = false) String signature,
                                          @RequestParam(required = false) String timestamp,
                                          @RequestParam(required = false) String nonce,
                                          @RequestBody(required = false) String body) {
        String token = wxProperties.getMessagePushToken();
        if (token == null || token.isBlank()) {
            // fail-closed：没配 Token 就无法验签。宁可拒收，也不能开一个匿名可写库的入口。
            log.error("[WX-PUSH] 未配置 treatbord.wx.message-push-token，回调一律拒收（fail-closed）");
            return ResponseEntity.status(503).body("message push token not configured");
        }
        if (!WxMessagePushService.verifySignature(token, timestamp, nonce, signature)) {
            log.warn("[WX-PUSH] 签名校验失败，丢弃该请求（timestamp={} nonce={}）", timestamp, nonce);
            return ResponseEntity.status(403).body("bad signature");
        }

        WxMessagePushService.Outcome outcome = pushService.handle(body);
        log.debug("[WX-PUSH] 处理完成 outcome={}", outcome);
        return ResponseEntity.ok("success");
    }
}
