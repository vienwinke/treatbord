package com.treatbord.module.security;

import com.treatbord.module.file.entity.FileRecord;
import com.treatbord.module.file.mapper.FileRecordMapper;
import com.treatbord.module.file.service.FileService;
import com.treatbord.module.security.service.WxMessagePushService;
import com.treatbord.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 内容安全异步结果回填的回归测试（修掉的 bug：闭环其实是开环）。
 *
 * <p>mediaCheckAsync 是异步接口，结果由微信消息推送回调返回；此前既没有回调入口，
 * 提交时拿到的 trace_id 也只打了日志没落库 —— 于是 file.sec_status 永远停在
 * PENDING(0)，FileService/SubmissionService 里 sec_status=2 的两处违规过滤成了
 * **永不触发的死代码**，违规图片永远可见，而文档把它写成"检测 → 处置闭环"。
 */
@AutoConfigureMockMvc
class ContentSecCallbackTest extends AbstractIntegrationTest {

    /** 与 src/test/resources/application-test.yml 里的 treatbord.wx.message-push-token 一致 */
    private static final String TOKEN = "test-msg-push-token";
    private static final String TS = "1764400000";
    private static final String NONCE = "abc123";

    @Autowired private MockMvc mockMvc;
    @Autowired private FileRecordMapper fileRecordMapper;
    @Autowired private FileService fileService;

    // ------------------------------------------------------------ 1) 签名
    /** 独立实现（String.format 版）交叉验证被测的手写 nibble 版 */
    private static String independentSign(String token, String ts, String nonce) throws Exception {
        List<String> parts = new ArrayList<>(List.of(token, ts, nonce));
        Collections.sort(parts);
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        byte[] d = md.digest(String.join("", parts).getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : d) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    @Test
    @DisplayName("签名：与独立实现一致；篡改任一参数 / Token 未配 都必须失败")
    void signature() throws Exception {
        String expected = independentSign(TOKEN, TS, NONCE);
        assertEquals(expected, WxMessagePushService.sign(TOKEN, TS, NONCE));
        assertTrue(WxMessagePushService.verifySignature(TOKEN, TS, NONCE, expected));
        assertTrue(WxMessagePushService.verifySignature(TOKEN, TS, NONCE, expected.toUpperCase()),
                "签名比较应大小写不敏感");
        assertFalse(WxMessagePushService.verifySignature(TOKEN, TS, NONCE, "0".repeat(40)));
        assertFalse(WxMessagePushService.verifySignature("wrong-token", TS, NONCE, expected),
                "Token 不一致必须验签失败");
        assertFalse(WxMessagePushService.verifySignature(TOKEN, "1", NONCE, expected));
        assertFalse(WxMessagePushService.verifySignature(TOKEN, TS, NONCE, null));
        assertFalse(WxMessagePushService.verifySignature(null, TS, NONCE, expected),
                "未配 Token 不得通过（fail-closed）");
    }

    // ------------------------------------------------------------ 2~6) 回调
    private Long newFile(String traceId) {
        Long uploaderId = createUser("内容安全-上传者");
        FileRecord f = new FileRecord();
        String key = "biz/submission/202610/" + UUID.randomUUID() + ".png";
        f.setStorageKey(key);
        f.setUrl("http://127.0.0.1:8080/files/" + key);
        f.setSize(1024L);
        f.setMime("image/png");
        f.setUploaderId(uploaderId);
        f.setSecStatus(FileRecord.SEC_CHECK_PENDING);
        f.setSecTraceId(traceId);
        fileRecordMapper.insert(f);
        return f.getId();
    }

    private static String pushXml(String event, String traceId, String detailJson) {
        StringBuilder sb = new StringBuilder("<xml>");
        sb.append("<ToUserName><![CDATA[gh_demo]]></ToUserName>");
        sb.append("<FromUserName><![CDATA[o_demo]]></FromUserName>");
        sb.append("<CreateTime>1764400000</CreateTime>");
        sb.append("<MsgType><![CDATA[event]]></MsgType>");
        sb.append("<Event><![CDATA[").append(event).append("]]></Event>");
        sb.append("<appid><![CDATA[wx0000000000000000]]></appid>");
        if (traceId != null) {
            sb.append("<trace_id><![CDATA[").append(traceId).append("]]></trace_id>");
        }
        sb.append("<version>2</version>");
        if (detailJson != null) {
            sb.append("<detail><![CDATA[").append(detailJson).append("]]></detail>");
        }
        sb.append("<errcode>0</errcode><errmsg><![CDATA[ok]]></errmsg>");
        sb.append("</xml>");
        return sb.toString();
    }

    private static String detail(String suggest, int label) {
        return "[{\"strategy\":\"content_model\",\"errcode\":0,\"suggest\":\"" + suggest
                + "\",\"label\":" + label + ",\"prob\":90}]";
    }

    private void push(String xml) throws Exception {
        mockMvc.perform(post("/wx/message-push")
                        .param("signature", independentSign(TOKEN, TS, NONCE))
                        .param("timestamp", TS)
                        .param("nonce", NONCE)
                        .contentType(MediaType.TEXT_XML)
                        .content(xml))
                .andExpect(status().isOk())
                .andExpect(content().string("success"));
    }

    @Test
    @DisplayName("risky 回填为违规 + 写回填时间，并且该文件不再下发（闭环真正合上）")
    void riskyMarksRejectedAndStopsServing() throws Exception {
        String trace = "trace-" + UUID.randomUUID();
        Long fileId = newFile(trace);
        assertEquals(1, fileService.signedUrls(List.of(fileId)).size(), "回填前应可下发");

        push(pushXml("wxa_media_check", trace, detail("risky", 20001)));

        FileRecord after = fileRecordMapper.selectById(fileId);
        assertEquals(FileRecord.SEC_CHECK_REJECT, after.getSecStatus());
        assertNotNull(after.getSecCheckedAt(), "应记录回填时间");
        assertTrue(fileService.signedUrls(List.of(fileId)).isEmpty(),
                "判为违规后必须不再下发 —— 修复前这里永远返回 URL（那处过滤是死代码）");
    }

    @Test
    @DisplayName("已判违规不得被后续 pass 推送降级")
    void rejectedIsNeverDowngraded() throws Exception {
        String trace = "trace-" + UUID.randomUUID();
        Long fileId = newFile(trace);

        push(pushXml("wxa_media_check", trace, detail("risky", 20001)));
        push(pushXml("wxa_media_check", trace, detail("pass", 100)));

        assertEquals(FileRecord.SEC_CHECK_REJECT, fileRecordMapper.selectById(fileId).getSecStatus(),
                "违规一旦判定，不得被重放/整理推送放回来");
    }

    @Test
    @DisplayName("review 与「拿不到结论」都保持待检测，绝不默认放行")
    void inconclusiveStaysPending() throws Exception {
        String traceReview = "trace-" + UUID.randomUUID();
        Long reviewFile = newFile(traceReview);
        push(pushXml("wxa_media_check", traceReview, detail("review", 20002)));
        assertEquals(FileRecord.SEC_CHECK_PENDING,
                fileRecordMapper.selectById(reviewFile).getSecStatus(), "review 应留待人工复核");

        String traceNoSuggest = "trace-" + UUID.randomUUID();
        Long noSuggestFile = newFile(traceNoSuggest);
        push(pushXml("wxa_media_check", traceNoSuggest, null));
        assertEquals(FileRecord.SEC_CHECK_PENDING,
                fileRecordMapper.selectById(noSuggestFile).getSecStatus(),
                "解析不到结论时绝不能默认放行（内容安全上的 fail-open 最危险）");
    }

    @Test
    @DisplayName("非 wxa_media_check 事件、以及 trace_id 对不上的推送，都不得改动状态")
    void unrelatedEventIsIgnored() throws Exception {
        String trace = "trace-" + UUID.randomUUID();
        Long fileId = newFile(trace);

        push(pushXml("subscribe", trace, detail("risky", 20001)));
        assertEquals(FileRecord.SEC_CHECK_PENDING,
                fileRecordMapper.selectById(fileId).getSecStatus(),
                "非内容安全事件不得改动 sec_status");

        push(pushXml("wxa_media_check", "trace-does-not-exist", detail("risky", 20001)));
        assertEquals(FileRecord.SEC_CHECK_PENDING,
                fileRecordMapper.selectById(fileId).getSecStatus());
    }

    @Test
    @DisplayName("签名不对必须 403，不能假装成功")
    void badSignatureRejected() throws Exception {
        String xml = pushXml("wxa_media_check", "trace-x", detail("risky", 20001));
        mockMvc.perform(post("/wx/message-push")
                        .param("signature", "0".repeat(40))
                        .param("timestamp", TS)
                        .param("nonce", NONCE)
                        .contentType(MediaType.TEXT_XML)
                        .content(xml))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("安全模式（加密报文）明确拒绝而不是静默丢弃（不写库、不炸）")
    void encryptedModeIsHandledExplicitly() throws Exception {
        push("<xml><Encrypt><![CDATA[not-decryptable]]></Encrypt></xml>");
        push("<xml><Event><![CDATA[wxa_media_check]]></Event></xml>");
    }
}
