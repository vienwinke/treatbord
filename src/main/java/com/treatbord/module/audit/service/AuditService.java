package com.treatbord.module.audit.service;

import com.treatbord.module.audit.entity.AuditLog;
import com.treatbord.module.audit.entity.LoginLog;
import com.treatbord.module.audit.mapper.AuditLogMapper;
import com.treatbord.module.audit.mapper.LoginLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 审计日志服务：写 audit_log / login_log。
 * 注意：登录日志写库失败不影响登录主流程（try-catch 包裹）。
 */
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogMapper auditLogMapper;
    private final LoginLogMapper loginLogMapper;

    /** 记录登录（成功/失败） */
    public void recordLogin(Long userId, boolean success, String failReason, HttpServletRequest req) {
        try {
            LoginLog log = new LoginLog();
            log.setUserId(userId);
            log.setSuccess(success ? 1 : 0);
            log.setFailReason(failReason);
            log.setUserAgent(truncate(req.getHeader("User-Agent"), 255));
            log.setIp(clientIp(req));
            loginLogMapper.insert(log);
        } catch (Exception e) {
            // 审计失败不阻塞主流程
        }
    }

    /** 记录关键操作 */
    public void record(String userId, String action, String targetType, Long targetId,
                       String detail, HttpServletRequest req) {
        try {
            AuditLog log = new AuditLog();
            log.setUserId(userId == null ? null : Long.parseLong(userId));
            log.setAction(action);
            log.setTargetType(targetType);
            log.setTargetId(targetId);
            log.setDetail(truncate(detail, 500));
            log.setIp(req == null ? null : clientIp(req));
            auditLogMapper.insert(log);
        } catch (Exception e) {
            // 审计失败不阻塞主流程
        }
    }

    public void record(Long userId, String action, String targetType, Long targetId,
                       String detail, HttpServletRequest req) {
        record(userId == null ? null : String.valueOf(userId), action, targetType, targetId, detail, req);
    }

    private String clientIp(HttpServletRequest req) {
        if (req == null) return null;
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}