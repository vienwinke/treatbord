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
import org.springframework.test.web.servlet.MvcResult;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 限流覆盖面验收（B4-1）：写接口（举报/互评/发布任务）被限流后应返回 429。
 * 通过把 app_config 阈值临时改成 1，验证"第二次请求被拦"。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RateLimitCoverageTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private StringRedisTemplate redisTemplate;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private UserMapper userMapper;

    private Long userId;

    @BeforeEach
    void setUp() {
        User u = new User();
        u.setOpenid("it-rl-" + UUID.randomUUID());
        u.setUsername("itrl" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        u.setNickname("限流测试用户");
        u.setCreditScore(100);
        u.setRole(0);
        u.setStatus(0);
        userMapper.insert(u);
        userId = u.getId();
        jdbcTemplate.update("INSERT INTO app_config (config_key, config_value) VALUES (?, ?) "
                        + "ON DUPLICATE KEY UPDATE config_value = VALUES(config_value)",
                "report.rate.limit.per.minute", "1");
        clearKeys("ratelimit:report:*");
    }

    @AfterEach
    void cleanUp() {
        // 恢复为 V7 种子值（不是删除，避免影响其它用例/环境）
        jdbcTemplate.update("INSERT INTO app_config (config_key, config_value) VALUES (?, ?) "
                        + "ON DUPLICATE KEY UPDATE config_value = VALUES(config_value)",
                "report.rate.limit.per.minute", "5");
        jdbcTemplate.update("DELETE FROM user WHERE id = ?", userId);
        clearKeys("ratelimit:report:*");
    }

    @Test
    @DisplayName("POST /api/reports 超过阈值返回 429")
    void reportEndpointIsRateLimited() throws Exception {
        String token = jwtUtil.generate(userId, 0, 0)[0];
        String body = "{\"targetType\":\"task\",\"targetId\":1,\"reason\":\"限流测试\"}";

        MvcResult first = mockMvc.perform(post("/api/reports")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)).andReturn();
        MvcResult second = mockMvc.perform(post("/api/reports")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)).andReturn();

        int firstCode = jsonCode(first.getResponse().getContentAsString());
        int secondCode = jsonCode(second.getResponse().getContentAsString());

        assertNotEquals(429, firstCode, "第 1 次不应被限流");
        assertEquals(429, secondCode, "第 2 次应被限流返回 429，实际=" + secondCode
                + " body=" + second.getResponse().getContentAsString());
    }

    private int jsonCode(String json) {
        int i = json.indexOf("\"code\":");
        if (i < 0) {
            return -1;
        }
        int start = i + 7;
        int end = start;
        while (end < json.length() && Character.isDigit(json.charAt(end))) {
            end++;
        }
        return Integer.parseInt(json.substring(start, end));
    }

    private void clearKeys(String pattern) {
        Set<String> keys = redisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }
}
