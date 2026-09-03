package com.treatbord.module.file.service;

import com.treatbord.module.audit.service.AuditService;
import com.treatbord.module.file.entity.FileRecord;
import com.treatbord.module.file.mapper.FileRecordMapper;
import com.treatbord.module.security.service.ContentSecurityService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 文件上传服务（docs/API_DESIGN.md §7 / SECURITY_REVIEW C1-C5）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileService {

    private final StorageService storageService;
    private final FileValidationService validationService;
    private final FileRecordMapper fileRecordMapper;
    private final AuditService auditService;
    private final ContentSecurityService contentSecurityService;

    /**
     * 上传（安全链路）：白名单 → magic bytes → 大小 → 随机名 → 入库 → 内容安全。
     */
    public FileRecord upload(MultipartFile file, String bizType, Long uploaderId,
                             HttpServletRequest httpReq) {
        // 1. 安全校验（白名单 + magic bytes + 大小）
        String ext = validationService.validate(file);

        // 2. 存储（服务端随机文件名，禁用户原始名）
        String storageKey;
        try {
            storageKey = storageService.store(file, bizType);
        } catch (IOException e) {
            log.error("文件存储失败", e);
            throw new com.treatbord.common.BusinessException(
                    com.treatbord.common.ResultCode.INTERNAL_ERROR, "文件存储失败");
        }

        // 3. 入库
        FileRecord record = new FileRecord();
        record.setStorageKey(storageKey);
        record.setUrl(storageService.toUrl(storageKey));
        record.setSize(file.getSize());
        record.setMime(file.getContentType());
        record.setUploaderId(uploaderId);
        record.setSecStatus(FileRecord.SEC_CHECK_PENDING); // 待异步检测
        fileRecordMapper.insert(record);

        // 4. 内容安全异步检测（占位；真实 mediaCheckAsync 回调回填）
        contentSecurityService.checkImage(record.getId());

        auditService.record(uploaderId, "UPLOAD_FILE", "file", record.getId(),
                "上传文件 size=" + file.getSize() + " type=" + bizType, httpReq);
        return record;
    }
}