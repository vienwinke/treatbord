package com.treatbord.module.file.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Paths;

/**
 * 静态文件访问：GET /files/biz/{type}/{yyyyMM}/{uuid}.ext
 * （凭证图 / 头像 URL 渲染用，MVP 本地磁盘直读）。
 */
@RestController
@RequiredArgsConstructor
public class FileViewController {

    private final org.springframework.core.env.Environment env;

    @GetMapping("/files/biz/{type}/{yyyyMM}/{name}")
    public ResponseEntity<Resource> view(@PathVariable String type,
                                         @PathVariable String yyyyMM,
                                         @PathVariable String name) {
        String dir = env.getProperty("treatbord.storage.local-dir", "./uploads");
        java.nio.file.Path path = Paths.get(dir, "biz", type, yyyyMM, name).toAbsolutePath().normalize();
        if (!path.toFile().exists()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(guessMediaType(name))
                .body(new FileSystemResource(path));
    }

    private MediaType guessMediaType(String name) {
        String lower = name.toLowerCase();
        if (lower.endsWith(".png")) return MediaType.IMAGE_PNG;
        if (lower.endsWith(".webp")) return MediaType.parseMediaType("image/webp");
        return MediaType.IMAGE_JPEG;
    }
}