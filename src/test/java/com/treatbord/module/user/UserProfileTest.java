package com.treatbord.module.user;

import com.treatbord.security.JwtUtil;
import com.treatbord.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * 修改昵称验收（PUT /api/users/me）：
 * 成功生效 + 首尾空白去除 + 空/超长拒绝 + 未登录 401 + 写审计日志。
 */
@AutoConfigureMockMvc
class UserProfileTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtUtil jwtUtil;

    private String token(Long userId) {
        return "Bearer " + jwtUtil.generate(userId, 0, 0)[0];
    }

    private String putNickname(Long userId, String body) throws Exception {
        return mockMvc.perform(put("/api/users/me")
                        .header("Authorization", token(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn().getResponse().getContentAsString();
    }

    private static String jsonStr(String json, String key) {
        int i = json.indexOf("\"" + key + "\":");
        if (i < 0) {
            return null;
        }
        int s = json.indexOf('"', i + key.length() + 3) + 1;
        int e = json.indexOf('"', s);
        return json.substring(s, e);
    }

    private static int jsonCode(String json) {
        int i = json.indexOf("\"code\":");
        int s = i + 7;
        int e = s;
        while (e < json.length() && (Character.isDigit(json.charAt(e)) || json.charAt(e) == '-')) {
            e++;
        }
        return Integer.parseInt(json.substring(s, e));
    }

    @Test
    @DisplayName("修改昵称成功：返回值与再次查询都已更新，并写入审计日志")
    void updateNicknameWorks() throws Exception {
        Long userId = createUser("旧昵称");
        String resp = putNickname(userId, "{\"nickname\":\"新昵称\"}");
        assertEquals(0, jsonCode(resp), "应成功: " + resp);
        assertEquals("新昵称", jsonStr(resp, "nickname"));

        String me = mockMvc.perform(get("/api/users/me").header("Authorization", token(userId)))
                .andReturn().getResponse().getContentAsString();
        assertEquals("新昵称", jsonStr(me, "nickname"), "再次查询应返回新昵称");

        Integer audit = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE user_id = ? AND action = 'UPDATE_NICKNAME'",
                Integer.class, userId);
        assertTrue(audit != null && audit >= 1, "应写入 UPDATE_NICKNAME 审计日志");
    }

    @Test
    @DisplayName("首尾空白被去除")
    void nicknameIsTrimmed() throws Exception {
        Long userId = createUser("trim-前");
        String resp = putNickname(userId, "{\"nickname\":\"   空白昵称   \"}");
        assertEquals(0, jsonCode(resp));
        assertEquals("空白昵称", jsonStr(resp, "nickname"));
    }

    @Test
    @DisplayName("空白昵称与超长昵称被拒绝")
    void invalidNicknameRejected() throws Exception {
        Long userId = createUser("校验-前");
        assertNotZero(putNickname(userId, "{\"nickname\":\"   \"}"), "空白昵称应被拒绝");
        assertNotZero(putNickname(userId, "{\"nickname\":\"" + "长".repeat(31) + "\"}"), "超长昵称应被拒绝");
    }

    @Test
    @DisplayName("未登录访问返回 401")
    void unauthenticatedRejected() throws Exception {
        String resp = mockMvc.perform(put("/api/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"匿名\"}"))
                .andReturn().getResponse().getContentAsString();
        assertEquals(401, jsonCode(resp), "未登录应 401: " + resp);
    }

    private void assertNotZero(String resp, String msg) {
        int code = jsonCode(resp);
        assertTrue(code != 0, msg + "，实际 code=" + code + " body=" + resp);
    }
}
