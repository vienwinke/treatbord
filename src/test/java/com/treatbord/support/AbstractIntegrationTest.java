package com.treatbord.support;

import com.treatbord.module.task.entity.Task;
import com.treatbord.module.task.enums.TaskStatus;
import com.treatbord.module.task.mapper.TaskClaimMapper;
import com.treatbord.module.task.mapper.TaskMapper;
import com.treatbord.module.user.entity.User;
import com.treatbord.module.user.mapper.UserMapper;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 集成测试基类：真实 MySQL(treatbord_test) + Redis，测试数据自动清理。
 * 运行前需注入环境变量（.env.local）：MYSQL_PASSWORD / MYSQL_MIGRATE_PASSWORD。
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    @Autowired protected UserMapper userMapper;
    @Autowired protected TaskMapper taskMapper;
    @Autowired protected TaskClaimMapper taskClaimMapper;
    @Autowired protected com.treatbord.module.submission.mapper.TaskSubmissionMapper submissionMapper;
    @Autowired protected com.treatbord.module.task.mapper.ClaimStatusLogMapper claimStatusLogMapper;
    @Autowired protected JdbcTemplate jdbcTemplate;

    private final List<Long> createdUserIds = new ArrayList<>();
    private final List<Long> createdTaskIds = new ArrayList<>();

    /** 造一个信用分 100 的正常用户 */
    protected Long createUser(String nickname) {
        User u = new User();
        u.setOpenid("it-" + UUID.randomUUID());
        u.setUsername("it" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        u.setNickname(nickname);
        u.setCreditScore(100);
        u.setRole(0);
        u.setStatus(0);
        userMapper.insert(u);
        createdUserIds.add(u.getId());
        return u.getId();
    }

    /** 造一个可接取的任务（截止时间在未来，避免被定时任务过期） */
    protected Long createTask(Long publisherId, int quota) {
        Task t = new Task();
        t.setPublisherId(publisherId);
        t.setTitle("IT-" + UUID.randomUUID().toString().substring(0, 8));
        t.setDescription("integration test task");
        t.setReward(new BigDecimal("1.00"));
        t.setQuota(quota);
        t.setClaimedCount(0);
        t.setClaimDeadline(LocalDateTime.now().plusDays(7));
        t.setDeadline(LocalDateTime.now().plusDays(14));
        t.setStatus(TaskStatus.OPEN.name());
        t.setVersion(0);
        taskMapper.insert(t);
        createdTaskIds.add(t.getId());
        return t.getId();
    }

    @AfterEach
    void cleanUpTestData() {
        for (Long taskId : createdTaskIds) {
            jdbcTemplate.update("DELETE FROM settlement WHERE task_id = ?", taskId);
            jdbcTemplate.update("DELETE FROM notification WHERE biz_id = ?", taskId);
            jdbcTemplate.update("DELETE FROM task_status_log WHERE task_id = ?", taskId);
            jdbcTemplate.update("DELETE FROM task_claim WHERE task_id = ?", taskId);
            jdbcTemplate.update("DELETE FROM task WHERE id = ?", taskId);
        }
        for (Long userId : createdUserIds) {
            jdbcTemplate.update("DELETE FROM claim_status_log WHERE operator_id = ?", userId);
            jdbcTemplate.update("DELETE FROM task_status_log WHERE operator_id = ?", userId);
            jdbcTemplate.update("DELETE FROM audit_log WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM file WHERE uploader_id = ?", userId);
            jdbcTemplate.update("DELETE FROM user WHERE id = ?", userId);
        }
        createdTaskIds.clear();
        createdUserIds.clear();
    }
}
