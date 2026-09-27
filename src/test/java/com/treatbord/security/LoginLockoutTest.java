package com.treatbord.security;

import com.treatbord.module.user.entity.User;
import com.treatbord.module.user.mapper.UserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 登录失败锁定验收（B5a-4）：IP 维度连续失败达阈值后拒绝登录（429）；
 * 账号维度只累计不硬锁（防止"知道用户名就能锁死别人账号"）；登录成功清零。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LoginLockoutTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserMapper userMapper;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private LoginAttemptService loginAttemptService;
    @Autowired private StringRedisTemplate redisTemplate;
    @Autowired private JdbcTemplate jdbcTemplate;

    private String username;
    private Long userId;

    @BeforeEach
    void setUp() {
        username = "itlock" + UUID.randomUUID().toString().substring(0, 8);
        User u = new User();
        u.setOpenid("it-lock-" + UUID.randomUUID());
        u.setUsername(username);
        u.setNickname("锁定测试用户");
        u.setPasswordHash(passwordEncoder.encode("RightPass1"));
        u.setCreditScore(100);
        u.setRole(0);
        u.setStatus(0);
        userMapper.insert(u);
        userId = u.getId();
        clearKeys();
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM login_log WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM audit_log WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM user WHERE id = ?", userId);
        clearKeys();
    }

    private int login(String password) throws Exception {
        String body = "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
        String json = mockMvc.perform(post("/api/auth/account/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn().getResponse().getContentAsString();
        int i = json.indexOf("\"code\":");
        int start = i + 7;
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) {
            end++;
        }
        return Integer.parseInt(json.substring(start, end));
    }

    @Test
    @DisplayName("连续失败 5 次后锁定：即使密码正确也返回 429")
    void lockedAfterThresholdFailures() throws Exception {
        for (int i = 1; i <= 5; i++) {
            int code = login("WrongPass" + i);
            assertNotEquals(429, code, "第 " + i + " 次失败时还不应锁定（阈值 5）");
        }
        int lockedCode = login("RightPass1");
        assertEquals(429, lockedCode, "达到阈值后，即使密码正确也应被锁");
        assertTrueLocked();
    }

    @Test
    @DisplayName("未达阈值时正常登录，成功后计数清零")
    void successLoginClearsCounter() throws Exception {
        login("WrongPass1");
        login("WrongPass2");
        int ok = login("RightPass1");
        assertEquals(0, ok, "未达阈值时正确密码应登录成功");
        assertEquals(0, loginAttemptService.accountFailures(username), "登录成功后账号失败计数应清零");
    }

    private void assertTrueLocked() {
        org.junit.jupiter.api.Assertions.assertTrue(
                loginAttemptService.isLocked("127.0.0.1"), "IP 维度应处于锁定状态");
    }

    private void clearKeys() {
        Set<String> keys = redisTemplate.keys("login:fail:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }
}
