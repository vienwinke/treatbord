package com.treatbord.module.file.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 本地磁盘存储实现（开发环境默认）。
 * 目录：{upload-dir}/biz/{type}/{yyyyMM}/{uuid}.ext
 * 生产切换 OSS：设置 treatbord.storage.type=oss（见 AliOssStorageService）。
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "treatbord.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalStorageService implements StorageService {

    @Value("${treatbord.storage.local-dir:./uploads}")
    private String localDir;

    @Value("${treatbord.storage.base-url:http://127.0.0.1:8080/files}")
    private String baseUrl;

    /** 允许的业务目录白名单（与 FileController 一致，双保险防目录逃逸） */
    private static final java.util.Set<String> ALLOWED_BIZ_TYPES =
            java.util.Set.of("submission", "avatar");

    @Override
    public String store(MultipartFile file, String bizType) throws IOException {
        if (bizType == null || !ALLOWED_BIZ_TYPES.contains(bizType)) {
            throw new IOException("不支持的 bizType: " + bizType);
        }
        String ext = extensionOf(file.getOriginalFilename());
        String yyyyMM = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        String uuid = UUID.randomUUID().toString().replace("-", "");
        String key = "biz/" + bizType + "/" + yyyyMM + "/" + uuid + "." + ext;

        // 路径归属校验：normalize 后必须仍在 upload-dir 之内
        Path baseDir = Paths.get(localDir).toAbsolutePath().normalize();
        Path dir = baseDir.resolve("biz").resolve(bizType).resolve(yyyyMM).normalize();
        if (!dir.startsWith(baseDir)) {
            throw new IOException("非法的存储路径");
        }
        Files.createDirectories(dir);
        Path target = dir.resolve(uuid + "." + ext).normalize();
        if (!target.startsWith(baseDir)) {
            throw new IOException("非法的存储路径");
        }
        file.transferTo(target.toAbsolutePath());
        log.info("[STORAGE] 已存储 key={} size={}", key, file.getSize());
        return key;
    }

    @Override
    public String toUrl(String storageKey) {
        return baseUrl + "/" + storageKey;
    }

    private String extensionOf(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "bin";
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }
}