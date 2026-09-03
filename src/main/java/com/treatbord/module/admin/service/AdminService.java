package com.treatbord.module.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.treatbord.common.BusinessException;
import com.treatbord.common.PageResult;
import com.treatbord.common.ResultCode;
import com.treatbord.module.audit.service.AuditService;
import com.treatbord.module.notify.service.NotificationService;
import com.treatbord.module.report.entity.Report;
import com.treatbord.module.report.mapper.ReportMapper;
import com.treatbord.module.settlement.mapper.SettlementMapper;
import com.treatbord.module.task.entity.Task;
import com.treatbord.module.task.entity.TaskClaim;
import com.treatbord.module.task.enums.TaskStatus;
import com.treatbord.module.task.mapper.TaskClaimMapper;
import com.treatbord.module.task.mapper.TaskMapper;
import com.treatbord.module.task.service.TaskService;
import com.treatbord.module.user.entity.User;
import com.treatbord.module.user.mapper.UserMapper;
import com.treatbord.security.TokenBlacklistService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 * 管理端服务（docs/API_DESIGN.md §11，role=ADMIN）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    private final TaskMapper taskMapper;
    private final TaskClaimMapper taskClaimMapper;
    private final TaskService taskService;
    private final ReportMapper reportMapper;
    private final UserMapper userMapper;
    private final SettlementMapper settlementMapper;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final TokenBlacklistService tokenBlacklistService;
    private final StringRedisTemplate redisTemplate;

    /**
     * 下架违规任务（→ CANCELLED + 通知发布者）。
     */
    @Transactional
    public void offlineTask(Long taskId, Long adminId, HttpServletRequest httpReq) {
        Task task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(ResultCode.TASK_NOT_FOUND);
        }
        if (!"IN_PROGRESS".equals(task.getStatus()) && !"OPEN".equals(task.getStatus())) {
            throw new BusinessException(ResultCode.CONFLICT, "当前状态不可下架");
        }
        int cas = taskMapper.casStatus(taskId, task.getStatus(), TaskStatus.CANCELLED.name());
        if (cas == 0) {
            throw new BusinessException(ResultCode.CONFLICT, "任务状态已变化");
        }
        taskService.writeTaskLog(taskId, task.getStatus(), TaskStatus.CANCELLED.name(),
                adminId, "管理端下架违规");
        auditService.record(adminId, "ADMIN_OFFLINE_TASK", "task", taskId, "管理端下架", httpReq);
        notificationService.notify(task.getPublisherId(),
                com.treatbord.module.notify.entity.Notification.TYPE_TASK_OFFLINE,
                "任务被下架",
                "你的任务《" + task.getTitle() + "》因违规被平台下架",
                taskId);
    }

    /**
     * 举报列表（status 筛选）。
     */
    public PageResult<Report> listReports(Integer status, long page, long pageSize) {
        Page<Report> p = new Page<>(page, pageSize);
        LambdaQueryWrapper<Report> qw = new LambdaQueryWrapper<Report>()
                .eq(status != null, Report::getStatus, status)
                .orderByAsc(Report::getStatus)
                .orderByDesc(Report::getCreateTime);
        Page<Report> result = reportMapper.selectPage(p, qw);
        return PageResult.of(result.getRecords(), result.getTotal(), page, pageSize);
    }

    /**
     * 处理举报（1=已处理 / 2=驳回）。
     */
    public void handleReport(Long reportId, Integer status, String note, Long adminId,
                             HttpServletRequest httpReq) {
        Report report = reportMapper.selectById(reportId);
        if (report == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "举报不存在");
        }
        Report up = new Report();
        up.setId(reportId);
        up.setStatus(status);
        up.setHandlerId(adminId);
        reportMapper.updateById(up);
        auditService.record(adminId, "ADMIN_HANDLE_REPORT", "report", reportId,
                "处理举报 status=" + status + (note == null ? "" : " note=" + note), httpReq);
    }

    /**
     * 封禁用户：status 0→1 + 踢下线（将该用户所有 jti 加入黑名单）。
     */
    @Transactional
    public void banUser(Long userId, Long adminId, HttpServletRequest httpReq) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        if (Integer.valueOf(1).equals(user.getStatus())) {
            throw new BusinessException(ResultCode.CONFLICT, "用户已封禁");
        }
        // 1. 置封禁
        User up = new User();
        up.setId(userId);
        up.setStatus(1);
        userMapper.updateById(up);

        // 2. 踢人下线：查该用户当前有效 token 的 jti
        //    session 映射：登录时 jti 记录到 Redis 集合 user:{id}:jtis
        String key = "user:" + userId + ":jtis";
        var jtis = redisTemplate.opsForSet().members(key);
        if (jtis != null) {
            for (Object jti : jtis) {
                tokenBlacklistService.blacklist(String.valueOf(jti));
            }
            redisTemplate.delete(key);
        }
        auditService.record(adminId, "ADMIN_BAN_USER", "user", userId, "封禁并踢下线", httpReq);
        log.info("封禁用户 {} 并踢下线，处理 jti 数={}", userId, jtis == null ? 0 : jtis.size());
    }

    public void unbanUser(Long userId, Long adminId, HttpServletRequest httpReq) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        User up = new User();
        up.setId(userId);
        up.setStatus(0);
        userMapper.updateById(up);
        auditService.record(adminId, "ADMIN_UNBAN_USER", "user", userId, "解封", httpReq);
    }

    /**
     * 用户列表（status 筛选）。
     */
    public PageResult<User> listUsers(Integer status, long page, long pageSize) {
        Page<User> p = new Page<>(page, pageSize);
        LambdaQueryWrapper<User> qw = new LambdaQueryWrapper<User>()
                .eq(status != null, User::getStatus, status)
                .orderByDesc(User::getCreateTime);
        Page<User> result = userMapper.selectPage(p, qw);
        return PageResult.of(result.getRecords(), result.getTotal(), page, pageSize);
    }

    /**
     * 统计：任务/用户/结算/待处理举报。
     */
    public Map<String, Object> summary() {
        Map<String, Object> m = new HashMap<>();
        m.put("taskCount", taskMapper.selectCount(new LambdaQueryWrapper<Task>()));
        m.put("userCount", userMapper.selectCount(new LambdaQueryWrapper<User>()));
        m.put("settlementCount", settlementMapper.selectCount(null));
        m.put("pendingSettlementCount", settlementMapper.selectCount(
                new LambdaQueryWrapper<com.treatbord.module.settlement.entity.Settlement>()
                        .eq(com.treatbord.module.settlement.entity.Settlement::getStatus, 0)));
        m.put("pendingReportCount", reportMapper.selectCount(
                new LambdaQueryWrapper<Report>().eq(Report::getStatus, Report.STATUS_PENDING)));
        m.put("claimCount", taskClaimMapper.selectCount(
                new LambdaQueryWrapper<TaskClaim>()));
        return m;
    }
}