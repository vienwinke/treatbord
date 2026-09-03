package com.treatbord.module.file.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
 * 本地磁盘存储实现（MVP，零依赖）。
 * 目录：{upload-dir}/biz/{type}/{yyyyMM}/{uuid}.ext
 */
@Slf4j
@Service
public class LocalStorageService implements StorageService {

    @Value("${treatbord.storage.local-dir:./uploads}")
    private String localDir;

    @Value("${treatbord.storage.base-url:http://127.0.0.1:8080/files}")
    private String baseUrl;

    @Override
    public String store(MultipartFile file, String bizType) throws IOException {
        String ext = extensionOf(file.getOriginalFilename());
        String yyyyMM = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        String uuid = UUID.randomUUID().toString().replace("-", "");
        String key = "biz/" + bizType + "/" + yyyyMM + "/" + uuid + "." + ext;

        Path dir = Paths.get(localDir, "biz", bizType, yyyyMM);
        Files.createDirectories(dir);
        Path target = dir.resolve(uuid + "." + ext);
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