package com.treatbord.module.admin.controller;

import com.treatbord.common.PageResult;
import com.treatbord.common.Result;
import com.treatbord.module.admin.dto.ReportHandleRequest;
import com.treatbord.module.admin.service.AdminService;
import com.treatbord.module.report.entity.Report;
import com.treatbord.module.task.mapper.TaskMapper;
import com.treatbord.module.user.entity.User;
import com.treatbord.security.RequireAdmin;
import com.treatbord.security.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 管理端接口（docs/API_DESIGN.md §11，role=ADMIN 独立鉴权）。
 */
@RestController
@RequestMapping("/api/admin")
@RequireAdmin
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final TaskMapper taskMapper;

    /** 下架违规任务 */
    @PutMapping("/tasks/{id}/offline")
    public Result<Void> offlineTask(@PathVariable Long id, HttpServletRequest httpReq) {
        adminService.offlineTask(id, UserContext.userId(), httpReq);
        return Result.ok();
    }

    /** 举报列表 */
    @GetMapping("/reports")
    public Result<PageResult<Report>> reports(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) Integer status) {
        return Result.ok(adminService.listReports(status, page, pageSize));
    }

    /** 处理举报 */
    @PutMapping("/reports/{id}")
    public Result<Void> handleReport(@PathVariable Long id,
                                     @Valid @RequestBody ReportHandleRequest req,
                                     HttpServletRequest httpReq) {
        adminService.handleReport(id, req.getStatus(), req.getNote(), UserContext.userId(), httpReq);
        return Result.ok();
    }

    /** 用户列表 */
    @GetMapping("/users")
    public Result<PageResult<User>> users(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) Integer status) {
        return Result.ok(adminService.listUsers(status, page, pageSize));
    }

    /** 封禁用户（踢下线） */
    @PutMapping("/users/{id}/ban")
    public Result<Void> ban(@PathVariable Long id, HttpServletRequest httpReq) {
        adminService.banUser(id, UserContext.userId(), httpReq);
        return Result.ok();
    }

    /** 解封 */
    @PutMapping("/users/{id}/unban")
    public Result<Void> unban(@PathVariable Long id, HttpServletRequest httpReq) {
        adminService.unbanUser(id, UserContext.userId(), httpReq);
        return Result.ok();
    }

    /** 统计 */
    @GetMapping("/stats/summary")
    public Result<Map<String, Object>> summary() {
        return Result.ok(adminService.summary());
    }
}