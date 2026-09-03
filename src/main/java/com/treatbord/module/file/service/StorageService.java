package com.treatbord.module.file.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 对象存储抽象（docs/SECURITY_REVIEW.md C5 / AGENTS.md §8.5）。
 * MVP：本地磁盘实现；后续切 COS/OSS/MinIO 只需新增实现。
 */
public interface StorageService {

    /**
     * 存储文件。
     *
     * @param file   上传的文件
     * @param bizType 业务类型（submission/avatar）
     * @return 存储 key（格式 biz/{type}/{yyyyMM}/{uuid}.ext）
     */
    String store(MultipartFile file, String bizType) throws IOException;

    /**
     * 由存储 key 生成可访问 URL。
     */
    String toUrl(String storageKey);
}