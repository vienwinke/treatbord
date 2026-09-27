package com.treatbord.config;

import com.treatbord.common.BusinessException;
import com.treatbord.module.review.service.ReviewService;
import com.treatbord.module.schedule.task.ScheduledTasks;
import com.treatbord.module.submission.dto.SubmissionRequest;
import com.treatbord.module.submission.service.SubmissionService;
import com.treatbord.module.task.service.ClaimService;
import com.treatbord.security.JwtUtil;
import com.treatbord.security.RateLimitInterceptor;
import com.treatbord.support.AbstractIntegrationTest;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 业务指标验收（B6-2）：接取结果 / 限流拒绝 / 审核决策与等待时长 / 定时任务执行 都会产生指标。
 */
@AutoConfigureMockMvc
class BusinessMetricsTest extends AbstractIntegrationTest {

    private static final String REPORT_LIMIT_KEY = "report.rate.limit.per.minute";

    @Autowired private ClaimService claimService;
    @Autowired private SubmissionService submissionService;
    @Autowired private ReviewService reviewService;
    @Autowired private ScheduledTasks scheduledTasks;
    @Autowired private RateLimitInterceptor rateLimitInterceptor;
    @Autowired private MeterRegistry registry;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private StringRedisTemplate redisTemplate;
    @Autowired private MockMvc mockMvc;
    @Autowired private JwtUtil jwtUtil;

    private double counter(String name, String... tags) {
        Counter c = registry.find(name).tags(tags).counter();
        return c == null ? 0 : c.count();
    }

    @Test
    @DisplayName("接取结果指标：成功记 success、名额已满记 full（走真实 HTTP 端点）")
    void claimResultMetrics() throws Exception {
        Long publisherId = createUser("METRIC-发布者");
        Long firstClaimer = createUser("METRIC-接取者1");
        Long secondClaimer = createUser("METRIC-接取者2");
        Long taskId = createTask(publisherId, 1);

        double successBefore = counter("treatbord.claim.result", "result", "success");
        double fullBefore = counter("treatbord.claim.result", "result", "full");

        int first = claimViaApi(taskId, firstClaimer);
        assertEquals(0, first, "第 1 次接取应成功");

        int second = claimViaApi(taskId, secondClaimer);
        assertEquals(2003, second, "第 2 次应因名额已满失败（code=2003）");

        assertTrue(counter("treatbord.claim.result", "result", "success") > successBefore,
                "成功接取应产生 result=success 指标");
        assertTrue(counter("treatbord.claim.result", "result", "full") > fullBefore,
                "名额已满应产生 result=full 指标");
    }

    /** 用真实 HTTP 端点接取（指标记录在 Controller 层），返回响应体 code */
    private int claimViaApi(Long taskId, Long claimerId) throws Exception {
        String token = jwtUtil.generate(claimerId, 0, 0)[0];
        MvcResult result = mockMvc.perform(post("/api/tasks/" + taskId + "/claim")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)).andReturn();
        return bodyCode(result.getResponse().getContentAsString());
    }

    private int bodyCode(String json) {
        int i = json.indexOf("\"code\":");
        int start = i + 7;
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) {
            end++;
        }
        return Integer.parseInt(json.substring(start, end));
    }

    @Test
    @DisplayName("限流拒绝产生 treatbord.ratelimit.rejected{scope=report}")
    void rateLimitRejectedMetric() throws Exception {
        jdbcTemplate.update("INSERT INTO app_config (config_key, config_value) VALUES (?, ?) "
                        + "ON DUPLICATE KEY UPDATE config_value = VALUES(config_value)",
                REPORT_LIMIT_KEY, "1");
        clearRateLimitKeys();
        try {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/reports");
            req.setRequestURI("/api/reports");
            req.setRemoteAddr("10.7.7.7");
            MockHttpServletResponse resp = new MockHttpServletResponse();

            double before = counter("treatbord.ratelimit.rejected", "scope", "report");
            assertTrue(rateLimitInterceptor.preHandle(req, resp, new Object()), "第 1 次应放行");
            assertThrows(BusinessException.class,
                    () -> rateLimitInterceptor.preHandle(req, resp, new Object()), "第 2 次应被限流");

            assertTrue(counter("treatbord.ratelimit.rejected", "scope", "report") > before,
                    "被限流应产生 rejected 指标");
        } finally {
            // 恢复 V7 种子值，避免影响其它用例/环境
            jdbcTemplate.update("INSERT INTO app_config (config_key, config_value) VALUES (?, ?) "
                            + "ON DUPLICATE KEY UPDATE config_value = VALUES(config_value)",
                    REPORT_LIMIT_KEY, "5");
            clearRateLimitKeys();
        }
    }

    @Test
    @DisplayName("审核产生决策计数与等待时长 Timer")
    void reviewMetrics() {
        Long publisherId = createUser("METRIC2-发布者");
        Long claimerId = createUser("METRIC2-接取者");
        Long taskId = createTask(publisherId, 1);
        Long claimId = claimService.claim(taskId, claimerId, null);

        SubmissionRequest req = new SubmissionRequest();
        req.setContent("凭证内容");
        req.setFileIds(List.of());
        submissionService.submit(claimId, claimerId, req, null);

        double before = counter("treatbord.review.decision", "result", "APPROVED");
        reviewService.review(claimId, publisherId, "approve", "通过", null);

        assertTrue(counter("treatbord.review.decision", "result", "APPROVED") > before,
                "审核通过应产生决策指标");
        Timer timer = registry.find("treatbord.review.wait").timer();
        assertNotNull(timer, "应记录提交到审核的等待时长");
        assertTrue(timer.count() >= 1, "等待时长应有采样");
    }

    @Test
    @DisplayName("定时任务执行产生 runs{task=reconcile,result=success} 指标")
    void scheduleRunMetrics() {
        double before = counter("treatbord.schedule.runs", "task", "reconcile", "result", "success");
        scheduledTasks.reconcile();
        assertTrue(counter("treatbord.schedule.runs", "task", "reconcile", "result", "success") > before,
                "对账任务应产生 success 指标");
    }

    private void clearRateLimitKeys() {
        Set<String> keys = redisTemplate.keys("ratelimit:report:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }
}
