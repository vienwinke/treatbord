package com.treatbord.module.file.service;

import com.treatbord.common.BusinessException;
import com.treatbord.common.ResultCode;
import com.treatbord.module.config.service.AppConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

/**
 * 文件上传安全校验（docs/SECURITY_REVIEW.md C1-C3）：
 * 1. 扩展名白名单（jpg/png/webp）
 * 2. magic bytes 校验（文件头，不只信扩展名）
 * 3. 大小限制（默认 5MB，app_config 可配）
 */
@Service
@RequiredArgsConstructor
public class FileValidationService {

    private final AppConfigService appConfigService;

    /** 扩展名 → magic bytes 签名（单段为文件头，双段为分散位置） */
    private static final Map<String, byte[][]> MAGIC_BYTES = Map.of(
            "jpg", new byte[][]{{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}},
            "jpeg", new byte[][]{{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}},
            "png", new byte[][]{
                    {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}},
            "webp", new byte[][]{
                    {'R', 'I', 'F', 'F'},
                    {'W', 'E', 'B', 'P'}});

    /**
     * 校验文件：返回规范化扩展名（小写，去点）。
     * 任一步不过即抛对应错误码。
     */
    public String validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "文件为空");
        }

        // 1. 大小限制
        int maxMb = appConfigService.getInt("file.max.size.mb", 5);
        if (file.getSize() > (long) maxMb * 1024 * 1024) {
            throw new BusinessException(ResultCode.FILE_TOO_LARGE,
                    "文件超过 " + maxMb + "MB 上限");
        }

        // 2. 扩展名白名单
        String ext = extensionOf(file.getOriginalFilename());
        Set<String> allowed = Set.of(
                appConfigService.get("file.allowed.ext", "jpg,png,webp").split(","));
        if (!allowed.contains(ext)) {
            throw new BusinessException(ResultCode.FILE_TYPE_NOT_ALLOWED);
        }

        // 3. magic bytes（文件头）
        if (!matchesMagic(file, ext)) {
            throw new BusinessException(ResultCode.FILE_TYPE_NOT_ALLOWED,
                    "文件内容与类型不符");
        }

        return ext;
    }

    private boolean matchesMagic(MultipartFile file, String ext) {
        byte[][] signatures = MAGIC_BYTES.get(ext);
        if (signatures == null || signatures.length == 0) {
            return false;
        }
        // 单段签名：png（8 字节头）/ jpg（FFD8FF）
        if (signatures.length == 1) {
            return headerStartsWith(file, signatures[0]);
        }
        // 双段签名：webp（RIFF @0-3 + WEBP @8-11）
        return signatureMatched(file, signatures);
    }

    /** webp 特判：RIFF....WEBP（第 9 字节起） */
    private boolean signatureMatched(MultipartFile file, byte[][] parts) {
        if (parts.length < 2) {
            return headerStartsWith(file, parts[0]);
        }
        try (InputStream in = file.getInputStream()) {
            byte[] head = in.readNBytes(12);
            if (head.length < 12) {
                return false;
            }
            // parts[0]=RIFF(0-3) parts[1]=WEBP(8-11)
            return startsWith(head, 0, parts[0])
                    && startsWith(head, 8, parts[1]);
        } catch (IOException e) {
            return false;
        }
    }

    private boolean headerStartsWith(MultipartFile file, byte[] sig) {
        try (InputStream in = file.getInputStream()) {
            byte[] head = in.readNBytes(sig.length);
            return Arrays.equals(head, sig);
        } catch (IOException e) {
            return false;
        }
    }

    private boolean startsWith(byte[] data, int offset, byte[] sig) {
        if (offset + sig.length > data.length) {
            return false;
        }
        for (int i = 0; i < sig.length; i++) {
            if (data[offset + i] != sig[i]) {
                return false;
            }
        }
        return true;
    }

    private String extensionOf(String filename) {
        if (filename == null) {
            return "";
        }
        int idx = filename.lastIndexOf('.');
        return idx < 0 ? "" : filename.substring(idx + 1).toLowerCase();
    }
}