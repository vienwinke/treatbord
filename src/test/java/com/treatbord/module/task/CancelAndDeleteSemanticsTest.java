package com.treatbord.module.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.treatbord.common.BusinessException;
import com.treatbord.common.ResultCode;
import com.treatbord.module.task.entity.TaskClaim;
import com.treatbord.module.task.enums.ClaimStatus;
import com.treatbord.module.task.service.ClaimService;
import com.treatbord.module.task.service.TaskService;
import com.treatbord.module.user.entity.User;
import com.treatbord.module.user.service.UserService;
import com.treatbord.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 三处「文档承诺 vs 代码事实」的回归测试（此前文档写了、代码没做）。
 *
 * 1. `API_DESIGN §4`：取消任务时应同步取消名下未完成的接取 —— 否则 claim 永久悬挂；
 * 2. `API_DESIGN §4`：已过接取截止不可取消接取 —— 否则会把已锁定的名额放走；
 * 3. `SECURITY_REVIEW §2`：注销 = 个人信息不可再关联 —— 此前只改了 openid。
 */
class CancelAndDeleteSemanticsTest extends AbstractIntegrationTest {

    @Autowired private ClaimService claimService;
    @Autowired private TaskService taskService;
    @Autowired private UserService userService;

    @Test
    @DisplayName("取消任务时，名下未完成的接取一并被取消（不留悬挂 claim）")
    void cancelTaskCascadesToPendingClaims() {
        Long publisherId = createUser("级联-发布者");
        Long claimerId = createUser("级联-接取者");
        Long taskId = createTask(publisherId, 3);
        Long claimId = claimService.claim(taskId, claimerId, null);

        taskService.cancel(taskId, publisherId, null);

        assertEquals(ClaimStatus.CANCELLED.name(), taskClaimMapper.selectById(claimId).getStatus(),
                "任务被取消后，未完成的接取必须一起取消");
        assertTrue(claimStatusLogMapperCount(claimId) >= 2, "取消级联也要留状态审计");
    }

    @Test
    @DisplayName("已过接取截止时间后不允许取消接取（名额已锁定）")
    void cannotCancelClaimAfterDeadline() {
        Long publisherId = createUser("截止-发布者");
        Long claimerId = createUser("截止-接取者");
        Long taskId = createTask(publisherId, 2);
        Long claimId = claimService.claim(taskId, claimerId, null);

        // 把接取截止拨到过去（模拟"时间已过"），再尝试取消
        jdbcTemplate.update("UPDATE task SET claim_deadline = DATE_SUB(NOW(), INTERVAL 1 HOUR) "
                + "WHERE id = ?", taskId);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> claimService.cancelClaim(claimId, claimerId, null));
        assertEquals(ResultCode.CLAIM_CANCEL_NOT_ALLOWED.getCode(), ex.getCode());
        assertEquals(ClaimStatus.CLAIMED.name(), taskClaimMapper.selectById(claimId).getStatus(),
                "被拒绝的取消不得改动状态");
    }

    @Test
    @DisplayName("注销账号：openid 之外的昵称/头像/账密哈希也一并清除")
    void deleteUserAnonymizesMoreThanOpenid() {
        Long userId = createUser("待注销用户");
        jdbcTemplate.update("UPDATE user SET password_hash = ?, avatar = ? WHERE id = ?",
                "legacy-hash-value", "http://example.com/a.png", userId);

        userService.deleteUser(userId);

        // ⚠️ 不能再用 selectById：注销是逻辑删除（deleted=1），@TableLogic 会把它过滤掉，
        //    查出来是 null —— 那样就等于"没验"。直接读原行。
        java.util.Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT openid, nickname, avatar, password_hash, username, status, credit_score "
                        + "FROM user WHERE id = ?", userId);

        assertTrue(String.valueOf(row.get("openid")).startsWith("DEL_"), "openid 应被替换以破坏唯一性");
        assertEquals("已注销用户", row.get("nickname"));
        assertNull(row.get("avatar"), "头像应被清除");
        assertNull(row.get("password_hash"), "账密哈希应被清除");
        assertTrue(String.valueOf(row.get("username")).startsWith("del_"),
                "用户名应被替换，实际=" + row.get("username"));
        // ⚠️ MySQL 把 TINYINT(1) 映射成 Boolean（Connector/J 的 tinyInt1isBit 默认 true），
        //    所以这里不能直接强转 Number —— 兼容两种返回类型。
        Object status = row.get("status");
        int statusInt = status instanceof Boolean b ? (b ? 1 : 0) : ((Number) status).intValue();
        assertEquals(1, statusInt, "注销后状态应置为封禁态");
    }

    private long claimStatusLogMapperCount(Long claimId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM claim_status_log WHERE claim_id = ?", Long.class, claimId);
    }
}
