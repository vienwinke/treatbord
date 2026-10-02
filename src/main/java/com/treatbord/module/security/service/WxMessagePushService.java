package com.treatbord.module.security.service;

import com.treatbord.module.file.entity.FileRecord;
import com.treatbord.module.file.mapper.FileRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 微信「消息推送」回调处理：把 mediaCheckAsync 的异步检测结果回填到 file.sec_status。
 *
 * <p>为什么必须有它：{@code wxa/media_check_async} 是**异步**接口，它**只提交不返回结果**，
 * 结果由微信通过消息推送（事件 {@code wxa_media_check}）回调到小程序后台配置的服务器地址。
 * 此前仓库里既没有回调入口、也没把 trace_id 落库，于是 sec_status 永远停在 0（待检测），
 * {@code FileService}/{@code SubmissionService} 里 sec_status=2 的违规过滤成了死代码 ——
 * 所谓「检测 → 违规处置闭环」其实是开环。
 *
 * <p>安全约定：
 * <ul>
 *   <li>只接受**明文模式**推送（安全模式的 {@code Encrypt} 未实现解密，遇到即明确拒绝并告警）；</li>
 *   <li>Token 未配置时调用方（Controller）必须 fail-closed 拒收；</li>
 *   <li>XML 解析关闭 DTD / 外部实体，避免 XXE；</li>
 *   <li>结果回填**不可降级**：一旦判为违规，后续推送（重放/整理）不得把状态改回去。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WxMessagePushService {

    /** mediaCheckAsync 结果事件名 */
    private static final String EVENT_MEDIA_CHECK = "wxa_media_check";

    private final FileRecordMapper fileRecordMapper;

    /** 处理结果，便于调用方与测试断言（也用于日志分级）。 */
    public enum Outcome {
        /** 结果已回填 */
        APPLIED,
        /** 事件与内容安全无关，忽略 */
        IGNORED,
        /** 报文不合规（缺 trace_id 等） */
        MALFORMED,
        /** 安全模式（加密报文）未支持 */
        UNSUPPORTED_ENCRYPTED,
        /** trace_id 在库里找不到对应文件（或已被判违规不可降级） */
        NO_TARGET
    }

    /**
     * 验签：微信明文模式的 signature = sha1(sort(token, timestamp, nonce) 拼接)。
     * 用常量时间比较，避免时序侧信道（与 JWT 的校验风格保持一致）。
     */
    public static boolean verifySignature(String token, String timestamp, String nonce, String signature) {
        if (token == null || token.isBlank() || timestamp == null || nonce == null || signature == null) {
            return false;
        }
        return constantTimeEquals(sign(token, timestamp, nonce), signature);
    }

    /** 计算签名（暴露为 public static 便于单测独立复算） */
    public static String sign(String token, String timestamp, String nonce) {
        List<String> parts = new ArrayList<>(List.of(token, timestamp, nonce));
        Collections.sort(parts);
        return sha1Hex(String.join("", parts));
    }

    /**
     * 处理一条推送报文：解析 → 判定 → 回填。
     * 返回 Outcome 供调用方决定日志与响应，不抛异常（回调路径要稳）。
     */
    public Outcome handle(String body) {
        if (body == null || body.isBlank()) {
            return Outcome.MALFORMED;
        }
        Document doc;
        try {
            doc = parseXml(body);
        } catch (Exception e) {
            log.warn("[WX-PUSH] 报文解析失败: {}", e.getMessage());
            return Outcome.MALFORMED;
        }
        if (doc == null) {
            return Outcome.MALFORMED;
        }
        Element root = doc.getDocumentElement();

        if (text(root, "Encrypt") != null) {
            // 安全模式的 AES 解密未实现：明确拒绝并留下可排查的日志，绝不静默丢弃
            log.error("[WX-PUSH] 收到加密报文（安全模式）：本服务只支持明文模式，"
                    + "请在小程序后台把消息推送加密方式改为明文，或先实现 AES 解密");
            return Outcome.UNSUPPORTED_ENCRYPTED;
        }

        String event = text(root, "Event");
        if (event == null || !EVENT_MEDIA_CHECK.equalsIgnoreCase(event.trim())) {
            log.debug("[WX-PUSH] 非内容安全事件，忽略: Event={}", event);
            return Outcome.IGNORED;
        }

        String traceId = text(root, "trace_id");
        if (traceId == null || traceId.isBlank()) {
            log.warn("[WX-PUSH] wxa_media_check 事件缺少 trace_id，无法定位文件");
            return Outcome.MALFORMED;
        }
        traceId = traceId.trim();

        String errcode = text(root, "errcode");
        if (errcode != null && !"0".equals(errcode.trim())) {
            // 微信侧出错：保持"待检测"，人工或重推再处理（不回填假结果）
            log.warn("[WX-PUSH] 微信返回错误 errcode={} errmsg={} traceId={}",
                    errcode, text(root, "errmsg"), traceId);
            return Outcome.IGNORED;
        }

        String suggest = worstSuggest(root);
        int secStatus;
        if ("risky".equals(suggest)) {
            secStatus = FileRecord.SEC_CHECK_REJECT;
        } else if ("pass".equals(suggest)) {
            secStatus = FileRecord.SEC_CHECK_PASS;
        } else {
            // review，或「报文里根本拿不到结论」：一律保持待检测等人工复核。
            // 内容安全上绝不能因为"解析不到"就默认放行 —— 那是最坏的一种 fail-open。
            secStatus = FileRecord.SEC_CHECK_PENDING;
        }

        try {
            int updated = fileRecordMapper.updateSecResultByTraceId(traceId, secStatus);
            if (updated == 0) {
                log.warn("[WX-PUSH] trace_id={} 没有可回填的文件（可能已被判违规不可降级，或 trace_id 不匹配）",
                        traceId);
                return Outcome.NO_TARGET;
            }
            if (secStatus == FileRecord.SEC_CHECK_REJECT) {
                log.warn("[WX-PUSH] ★ 内容安全判为违规，已下架 traceId={} label={}", traceId, label(root));
            } else {
                log.info("[WX-PUSH] 内容安全结果已回填 traceId={} suggest={} status={}",
                        traceId, suggest, secStatus);
            }
            return Outcome.APPLIED;
        } catch (Exception e) {
            log.error("[WX-PUSH] 回填失败 traceId={}", traceId, e);
            return Outcome.MALFORMED;
        }
    }

    // ---------- 内部 ----------

    /**
     * 取「最严重」的 suggest：detail 是 JSON 数组（各检测策略一条），另有顶层 suggest 兜底。
     * risky > review > pass。
     */
    private String worstSuggest(Element root) {
        List<String> all = new ArrayList<>();
        String top = text(root, "suggest");
        if (top != null) {
            all.add(top.trim());
        }
        String detail = text(root, "detail");
        if (detail != null) {
            // 不引 JSON 依赖：detail 里只关心 suggest 字段，用宽松匹配即可，
            // 同时对格式变化保持容错（拿不到就退化为"无结论"→ 按 pass 处理会不安全，
            // 因此这里若无任何 suggest，调用方会落到 default 分支，需人工确认）
            var m = java.util.regex.Pattern.compile("\"suggest\"\\s*:\\s*\"([A-Za-z_]+)\"").matcher(detail);
            while (m.find()) {
                all.add(m.group(1));
            }
        }
        if (all.isEmpty()) {
            log.warn("[WX-PUSH] 报文里没有可识别的 suggest 字段：保持待检测交人工确认（不默认放行）");
            return null;
        }
        if (all.stream().anyMatch(s -> "risky".equalsIgnoreCase(s))) {
            return "risky";
        }
        if (all.stream().anyMatch(s -> "review".equalsIgnoreCase(s))) {
            return "review";
        }
        return "pass";
    }

    private String label(Element root) {
        String l = text(root, "label");
        return l == null ? "-" : l.trim();
    }

    /** 关闭 DTD 与外部实体（防 XXE），再解析报文。 */
    private Document parseXml(String body) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
    }

    /** 按标签名取子元素的文本（不区分大小写；微信报文标签大小写并不完全统一）。 */
    private String text(Element root, String tag) {
        NodeList children = root.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (n.getNodeType() == Node.ELEMENT_NODE && tag.equalsIgnoreCase(n.getNodeName())) {
                String v = n.getTextContent();
                return v == null ? null : v;
            }
        }
        return null;
    }

    private static String sha1Hex(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-1 不可用", e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }
        int r = 0;
        for (int i = 0; i < a.length(); i++) {
            r |= Character.toLowerCase(a.charAt(i)) ^ Character.toLowerCase(b.charAt(i));
        }
        return r == 0;
    }
}
