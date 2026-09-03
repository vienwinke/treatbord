package com.treatbord.module.task.controller;

import com.treatbord.common.PageResult;
import com.treatbord.common.Result;
import com.treatbord.module.task.dto.ClaimVO;
import com.treatbord.module.task.service.ClaimService;
import com.treatbord.security.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 接取接口（docs/API_DESIGN.md §4）。
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ClaimController {

    private final ClaimService claimService;

    /** 4.1 接取任务 */
    @PostMapping("/tasks/{id}/claim")
    public Result<Long> claim(@PathVariable Long id, HttpServletRequest httpReq) {
        Long claimId = claimService.claim(id, UserContext.userId(), httpReq);
        return Result.ok(claimId);
    }

    /** 4.2 取消接取 */
    @DeleteMapping("/claims/{id}")
    public Result<Void> cancel(@PathVariable Long id, HttpServletRequest httpReq) {
        claimService.cancelClaim(id, UserContext.userId(), httpReq);
        return Result.ok();
    }

    /** 4.3 我接取的任务 */
    @GetMapping("/me/claims")
    public Result<PageResult<ClaimVO>> myClaims(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long pageSize) {
        return Result.ok(claimService.myClaims(UserContext.userId(), page, pageSize));
    }
}