package com.treatbord.module.report.service;

import com.treatbord.common.ResultCode;
import com.treatbord.module.audit.service.AuditService;
import com.treatbord.module.report.dto.ReportCreateRequest;
import com.treatbord.module.report.entity.Report;
import com.treatbord.module.report.mapper.ReportMapper;
import com.treatbord.module.security.service.ContentSecurityService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 举报服务（docs/API_DESIGN.md §10）。
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportMapper reportMapper;
    private final ContentSecurityService contentSecurityService;
    private final AuditService auditService;

    /**
     * 提交举报：入库待处理。
     */
    public void create(ReportCreateRequest req, Long reporterId, HttpServletRequest httpReq) {
        contentSecurityService.checkText(req.getReason(), "report");

        Report report = new Report();
        report.setReporterId(reporterId);
        report.setTargetType(req.getTargetType());
        report.setTargetId(req.getTargetId());
        report.setReason(req.getReason());
        report.setStatus(Report.STATUS_PENDING);
        reportMapper.insert(report);

        auditService.record(reporterId, "REPORT", req.getTargetType(), req.getTargetId(),
                "举报: " + req.getReason(), httpReq);
    }
}