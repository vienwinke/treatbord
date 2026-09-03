package com.treatbord.module.task.controller;

import com.treatbord.common.PageResult;
import com.treatbord.common.Result;
import com.treatbord.module.task.dto.TaskCreateRequest;
import com.treatbord.module.task.dto.TaskVO;
import com.treatbord.module.task.service.TaskService;
import com.treatbord.security.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 任务接口（docs/API_DESIGN.md §3、§6.2）。
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    /** 3.1 任务列表（分页/筛选/搜索） */
    @GetMapping("/tasks")
    public Result<PageResult<TaskVO>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        return Result.ok(taskService.list(status, keyword, page, pageSize));
    }

    /** 3.2 任务详情（登录态返回是否已接取） */
    @GetMapping("/tasks/{id}")
    public Result<TaskVO> detail(@PathVariable Long id) {
        return Result.ok(taskService.detail(id, UserContext.userId()));
    }

    /** 3.3 发布任务 */
    @PostMapping("/tasks")
    public Result<Long> create(@Valid @RequestBody TaskCreateRequest req,
                               HttpServletRequest httpReq) {
        Long taskId = taskService.create(req, UserContext.userId(), httpReq);
        return Result.ok(taskId);
    }

    /** 3.4 发布者取消任务 */
    @PostMapping("/tasks/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id, HttpServletRequest httpReq) {
        taskService.cancel(id, UserContext.userId(), httpReq);
        return Result.ok();
    }

    /** 6.2 延伸：发布者查看任务下所有接取（含最新凭证） */
    @GetMapping("/tasks/{id}/claims")
    public Result<java.util.List<com.treatbord.module.task.dto.ClaimWithSubmissionVO>> taskClaims(
            @PathVariable Long id) {
        return Result.ok(taskService.listClaimsOfTask(id, UserContext.userId()));
    }

    /** 6.2 我发布的任务（带接取进度） */
    @GetMapping("/me/tasks")
    public Result<PageResult<TaskVO>> myTasks(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long pageSize) {
        return Result.ok(taskService.myTasks(UserContext.userId(), page, pageSize));
    }
}