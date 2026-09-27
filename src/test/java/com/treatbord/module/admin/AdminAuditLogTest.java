package com.treatbord.module.admin;

import com.treatbord.module.user.entity.User;
import com.treatbord.module.user.mapper.UserMapper;
import com.treatbord.security.JwtUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * 管理端审计查询验收（B5a-3）：普通用户 403（@RequireAdmin），管理员可查询到记录。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminAuditLogTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserMapper userMapper;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private JdbcTemplate jdbcTemplate;

    private Long userId;
    private Long adminId;

    @BeforeEach
    void setUp() {
        userId = createUser(0);
        adminId = createUser(1);
        jdbcTemplate.update("INSERT INTO audit_log (user_id, action, target_type, target_id, detail, ip, create_time) "
                + "VALUES (?, ?, ?, ?, ?, ?, NOW())", adminId, "IT_AUDIT_CHECK", "task", 1L, "审计查询用例", "127.0.0.1");
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM audit_log WHERE action = ?", "IT_AUDIT_CHECK");
        jdbcTemplate.update("DELETE FROM audit_log WHERE user_id IN (?, ?)", userId, adminId);
        jdbcTemplate.update("DELETE FROM user WHERE id IN (?, ?)", userId, adminId);
    }

    private Long createUser(int role) {
        User u = new User();
        u.setOpenid("it-audit-" + UUID.randomUUID());
        u.setUsername("itaudit" + UUID.randomUUID().toString().replace("-", "").substring(0, 10));
        u.setNickname("审计用例用户");
        u.setCreditScore(100);
        u.setRole(role);
        u.setStatus(0);
        userMapper.insert(u);
        return u.getId();
    }

    @Test
    @DisplayName("普通用户访问审计接口 → 403；管理员 → 200 且能查到记录")
    void onlyAdminCanQueryAuditLogs() throws Exception {
        String userToken = jwtUtil.generate(userId, 0, 0)[0];
        String adminToken = jwtUtil.generate(adminId, 1, 0)[0];

        int forbidden = statusOf(userToken);
        assertEquals(403, forbidden, "普通用户应被 @RequireAdmin 拦截");

        int ok = statusOf(adminToken);
        assertEquals(0, ok, "管理员应可访问（统一响应体 code=0 表示成功）");
    }

    private int statusOf(String token) throws Exception {
        String json = mockMvc.perform(get("/api/admin/audit-logs")
                        .param("action", "IT_AUDIT_CHECK")
                        .header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString();
        int i = json.indexOf("\"code\":");
        int start = i + 7;
        int end = start;
        while (end < json.length() && Character.isDigit(json.charAt(end))) {
            end++;
        }
        int code = Integer.parseInt(json.substring(start, end));
        if (code == 0) {
            assertTrue(json.contains("IT_AUDIT_CHECK"), "响应应包含审计记录: " + json);
        }
        return code;
    }
}
