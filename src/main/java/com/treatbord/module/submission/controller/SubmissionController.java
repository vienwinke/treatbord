package com.treatbord.module.submission.controller;

import com.treatbord.common.Result;
import com.treatbord.module.submission.dto.SubmissionRequest;
import com.treatbord.module.submission.service.SubmissionService;
import com.treatbord.security.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 凭证接口（docs/API_DESIGN.md §5）。
 */
@RestController
@RequestMapping("/api/claims")
@RequiredArgsConstructor
public class SubmissionController {

    private final SubmissionService submissionService;

    /** 5.1 提交完成凭证 */
    @PostMapping("/{id}/submit")
    public Result<Void> submit(@PathVariable Long id,
                               @Valid @RequestBody SubmissionRequest req,
                               HttpServletRequest httpReq) {
        submissionService.submit(id, UserContext.userId(), req, httpReq);
        return Result.ok();
    }

    /** 5.2 查看接取详情（含凭证，claim 双方可见） */
    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        SubmissionService.TaskClaimAndSubmission d =
                submissionService.detail(id, UserContext.userId());
        Map<String, Object> resp = new HashMap<>();
        resp.put("claim", d.getClaim());
        resp.put("task", d.getTask());
        resp.put("submission", d.getSubmission());
        return Result.ok(resp);
    }
}