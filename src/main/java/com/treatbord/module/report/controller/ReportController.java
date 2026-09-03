package com.treatbord.module.report.controller;

import com.treatbord.common.Result;
import com.treatbord.module.report.dto.ReportCreateRequest;
import com.treatbord.module.report.service.ReportService;
import com.treatbord.security.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 举报接口（docs/API_DESIGN.md §10.1）。
 */
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    /** 10.1 提交举报 */
    @PostMapping
    public Result<Void> create(@Valid @RequestBody ReportCreateRequest req,
                               HttpServletRequest httpReq) {
        reportService.create(req, UserContext.userId(), httpReq);
        return Result.ok();
    }
}