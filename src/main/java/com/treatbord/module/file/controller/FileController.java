package com.treatbord.module.file.controller;

import com.treatbord.common.Result;
import com.treatbord.module.file.entity.FileRecord;
import com.treatbord.module.file.service.FileService;
import com.treatbord.security.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件接口（docs/API_DESIGN.md §7.1）。
 */
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    /** 7.1 上传文件（multipart，字段名 file） */
    @PostMapping
    public Result<FileRecord> upload(@RequestParam("file") MultipartFile file,
                                     @RequestParam(value = "bizType", defaultValue = "submission") String bizType,
                                     HttpServletRequest httpReq) {
        FileRecord record = fileService.upload(file, bizType, UserContext.userId(), httpReq);
        return Result.ok(record);
    }
}