package com.treatbord.module.notify.controller;

import com.treatbord.common.PageResult;
import com.treatbord.common.Result;
import com.treatbord.module.notify.entity.Notification;
import com.treatbord.module.notify.service.NotificationService;
import com.treatbord.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 通知接口（docs/API_DESIGN.md §8）。
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /** 8.1 通知列表 */
    @GetMapping
    public Result<PageResult<Notification>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) Integer isRead) {
        return Result.ok(notificationService.list(UserContext.userId(), isRead, page, pageSize));
    }

    /** 8.2 标记单条已读 */
    @PostMapping("/{id}/read")
    public Result<Void> read(@PathVariable Long id) {
        notificationService.markRead(UserContext.userId(), id);
        return Result.ok();
    }

    /** 8.3 全部已读 */
    @PostMapping("/read-all")
    public Result<String> readAll() {
        int n = notificationService.markAllRead(UserContext.userId());
        return Result.ok("已标记 " + n + " 条");
    }
}