package com.treatbord.module.task.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.treatbord.common.BusinessException;
import com.treatbord.common.PageResult;
import com.treatbord.common.ResultCode;
import com.treatbord.module.audit.service.AuditService;
import com.treatbord.module.task.dto.TaskCreateRequest;
import com.treatbord.module.task.dto.TaskVO;
import com.treatbord.module.task.entity.Task;
import com.treatbord.module.task.entity.TaskClaim;
import com.treatbord.module.task.entity.TaskStatusLog;
import com.treatbord.module.task.enums.TaskStatus;
import com.treatbord.module.task.mapper.TaskClaimMapper;
import com.treatbord.module.task.mapper.TaskMapper;
import com.treatbord.module.task.mapper.TaskStatusLogMapper;
import com.treatbord.module.user.entity.User;
import com.treatbord.module.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 任务服务：发布 / 列表 / 详情 / 取消 + 惰性过期（docs/API_DESIGN.md §3）。
 */
@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskMapper taskMapper;
    private final TaskStatusLogMapper taskStatusLogMapper;
    private final TaskClaimMapper taskClaimMapper;
    private final com.treatbord.module.submission.mapper.TaskSubmissionMapper submissionMapper;
    private final UserService userService;
    private final AuditService auditService;

    public static final String STATUS_OPEN = "OPEN";

    /**
     * 发布任务：校验截止时间合理性，入库 OPEN。
     */
    public Long create(TaskCreateRequest req, Long publisherId, HttpServletRequest httpReq) {
        // 截止时间合法性
        if (!req.getClaimDeadline().isBefore(req.getDeadline())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "完成截止必须晚于接取截止");
        }
        if (!req.getClaimDeadline().isAfter(java.time.LocalDateTime.now())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "接取截止必须晚于当前时间");
        }

        Task task = new Task();
        task.setPublisherId(publisherId);
        task.setTitle(req.getTitle());
        task.setDescription(req.getDescription());
        task.setReward(req.getReward());
        task.setQuota(req.getQuota());
        task.setClaimedCount(0);
        task.setClaimDeadline(req.getClaimDeadline());
        task.setDeadline(req.getDeadline());
        task.setStatus(STATUS_OPEN);
        task.setVersion(0);
        taskMapper.insert(task);

        auditService.record(publisherId, "CREATE_TASK", "task", task.getId(), "发布任务: " + req.getTitle(), httpReq);
        return task.getId();
    }

    /**
     * 我发布的任务（分页，docs/API_DESIGN.md §6.2）。
     */
    public PageResult<TaskVO> myTasks(Long publisherId, long page, long pageSize) {
        Page<Task> p = new Page<>(page, pageSize);
        LambdaQueryWrapper<Task> qw = new LambdaQueryWrapper<Task>()
                .eq(Task::getPublisherId, publisherId)
                .orderByDesc(Task::getCreateTime);
        Page<Task> result = taskMapper.selectPage(p, qw);

        List<TaskVO> vos = result.getRecords().stream()
                .map(TaskVO::from)
                .collect(Collectors.toList());
        // 发布者就是自己，直接填昵称
        if (!vos.isEmpty()) {
            User me = userService.getById(publisherId);
            vos.forEach(v -> v.setPublisherNickname(me.getNickname()));
        }
        return PageResult.of(vos, result.getTotal(), page, pageSize);
    }

    /**
     * 任务列表（分页/状态筛选/关键词搜索）。
     * 查询前惰性兜底：过期的 OPEN 任务置 EXPIRED。
     */
    public PageResult<TaskVO> list(String status, String keyword, long page, long pageSize) {
        // 惰性过期（定时任务之外的查询兜底，docs/API_DESIGN.md §3.1）
        taskMapper.expireOpenTasks();

        Page<Task> p = new Page<>(page, pageSize);
        LambdaQueryWrapper<Task> qw = new LambdaQueryWrapper<Task>()
                .eq(status != null && !status.isBlank(), Task::getStatus, status)
                .and(keyword != null && !keyword.isBlank(), w -> w
                        .like(Task::getTitle, keyword)
                        .or()
                        .like(Task::getDescription, keyword))
                .orderByDesc(Task::getCreateTime);

        Page<Task> result = taskMapper.selectPage(p, qw);

        // 填充发布者昵称
        List<TaskVO> vos = result.getRecords().stream()
                .map(TaskVO::from)
                .collect(Collectors.toList());
        fillPublisherNickname(vos);

        return PageResult.of(vos, result.getTotal(), page, pageSize);
    }

    /**
     * 任务详情。
     *
     * @param currentUserId 当前登录用户（可空：匿名浏览）
     */
    public TaskVO detail(Long taskId, Long currentUserId) {
        Task task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(ResultCode.TASK_NOT_FOUND);
        }
        TaskVO vo = TaskVO.from(task);
        User publisher = userService.getById(task.getPublisherId());
        vo.setPublisherNickname(publisher.getNickname());

        if (currentUserId != null) {
            int active = taskClaimMapper.countActiveClaim(taskId, currentUserId);
            vo.setClaimedByMe(active > 0);
        }
        return vo;
    }

    /**
     * 发布者取消任务：仅 OPEN/IN_PROGRESS 可取消。
     */
    @Transactional
    public void cancel(Long taskId, Long operatorId, HttpServletRequest httpReq) {
        Task task = requireTask(taskId);
        // 归属校验（IDOR 防护）
        if (!task.getPublisherId().equals(operatorId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只能取消自己发布的任务");
        }
        TaskStatus from = TaskStatus.of(task.getStatus());
        TaskStatus.validateTransition(from, TaskStatus.CANCELLED);

        // CAS 防竞态
        int updated = taskMapper.casStatus(taskId, task.getStatus(), TaskStatus.CANCELLED.name());
        if (updated == 0) {
            throw new BusinessException(ResultCode.TASK_CANCEL_NOT_ALLOWED, "任务状态已变化，请刷新");
        }
        writeTaskLog(taskId, task.getStatus(), TaskStatus.CANCELLED.name(), operatorId, "发布者取消");
        auditService.record(operatorId, "CANCEL_TASK", "task", taskId, "取消任务", httpReq);
    }

    /** 写任务状态审计 */
    public void writeTaskLog(Long taskId, String from, String to, Long operatorId, String reason) {
        TaskStatusLog log = new TaskStatusLog();
        log.setTaskId(taskId);
        log.setFromStatus(from);
        log.setToStatus(to);
        log.setOperatorId(operatorId);
        log.setReason(reason);
        taskStatusLogMapper.insert(log);
    }

    public Task requireTask(Long taskId) {
        Task task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(ResultCode.TASK_NOT_FOUND);
        }
        return task;
    }

    private void fillPublisherNickname(List<TaskVO> vos) {
        List<Long> publisherIds = vos.stream().map(TaskVO::getPublisherId).distinct().toList();
        Map<Long, User> users = userService.listByIds(publisherIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        vos.forEach(v -> {
            User u = users.get(v.getPublisherId());
            v.setPublisherNickname(u == null ? null : u.getNickname());
        });
    }
    /**
     * 发布者查看任务下所有接取（含最新凭证），docs/API_DESIGN.md §6.2 延伸。
     */
    public java.util.List<com.treatbord.module.task.dto.ClaimWithSubmissionVO> listClaimsOfTask(
            Long taskId, Long currentUserId) {
        Task task = requireTask(taskId);
        // 归属校验（IDOR）
        if (!task.getPublisherId().equals(currentUserId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只能查看自己发布任务的接取");
        }
        List<TaskClaim> claims = taskClaimMapper.selectList(new LambdaQueryWrapper<TaskClaim>()
                .eq(TaskClaim::getTaskId, taskId)
                .orderByDesc(TaskClaim::getCreateTime));

        // 联查用户昵称 + 最新凭证
        List<Long> userIds = claims.stream().map(TaskClaim::getUserId).distinct().toList();
        Map<Long, User> users = userIds.isEmpty() ? Map.of()
                : userService.listByIds(userIds).stream().collect(Collectors.toMap(User::getId, Function.identity()));
        Map<Long, com.treatbord.module.submission.entity.TaskSubmission> subs =
                claims.stream()
                        .map(c -> submissionMapper.selectLatestByClaimId(c.getId()))
                        .filter(java.util.Objects::nonNull)
                        .collect(Collectors.toMap(com.treatbord.module.submission.entity.TaskSubmission::getClaimId, Function.identity()));

        return claims.stream().map(c -> {
            var vo = com.treatbord.module.task.dto.ClaimWithSubmissionVO.from(c);
            User u = users.get(c.getUserId());
            vo.setUserNickname(u == null ? null : u.getNickname());
            var sub = subs.get(c.getId());
            if (sub != null) {
                vo.setSubmission(com.treatbord.module.submission.dto.SubmissionVO.from(sub));
            }
            return vo;
        }).collect(Collectors.toList());
    }
}
