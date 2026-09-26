package com.treatbord.module.file.service;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.treatbord.common.BusinessException;
import com.treatbord.common.ResultCode;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 阿里云 OSS 存储实现（生产环境，docs/STANDARDIZATION_PLAN.md P0-3）。
 *
 * <p>启用方式：{@code treatbord.storage.type=oss} + 注入
 * OSS_ENDPOINT / OSS_BUCKET / OSS_ACCESS_KEY_ID / OSS_ACCESS_KEY_SECRET。
 * 对象 key 规则与本地实现一致：{@code biz/{type}/{yyyyMM}/{uuid}.ext}（禁用户文件名）。
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "treatbord.storage.type", havingValue = "oss")
public class AliOssStorageService implements StorageService {

    @Value("${treatbord.storage.oss.endpoint:}")
    private String endpoint;

    @Value("${treatbord.storage.oss.bucket:}")
    private String bucket;

    @Value("${treatbord.storage.oss.access-key-id:}")
    private String accessKeyId;

    @Value("${treatbord.storage.oss.access-key-secret:}")
    private String accessKeySecret;

    /** 可选：CDN 加速域名或自定义域名；为空则由 endpoint 推导 */
    @Value("${treatbord.storage.base-url:}")
    private String baseUrl;

    private volatile OSS client;

    private OSS client() {
        OSS local = client;
        if (local == null) {
            synchronized (this) {
                if (client == null) {
                    if (endpoint.isBlank() || bucket.isBlank()
                            || accessKeyId.isBlank() || accessKeySecret.isBlank()) {
                        throw new BusinessException(ResultCode.INTERNAL_ERROR, "OSS 配置不完整");
                    }
                    client = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
                }
                local = client;
            }
        }
        return local;
    }

    /** 允许的业务目录白名单（与 FileController 一致，防 key 逃逸） */
    private static final java.util.Set<String> ALLOWED_BIZ_TYPES =
            java.util.Set.of("submission", "avatar");

    @Override
    public String store(MultipartFile file, String bizType) throws IOException {
        if (bizType == null || !ALLOWED_BIZ_TYPES.contains(bizType)) {
            throw new IOException("不支持的 bizType: " + bizType);
        }
        String ext = extensionOf(file.getOriginalFilename());
        String yyyyMM = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        String key = "biz/" + bizType + "/" + yyyyMM + "/"
                + UUID.randomUUID().toString().replace("-", "") + "." + ext;

        try (InputStream in = file.getInputStream()) {
            client().putObject(bucket, key, in);
        } catch (Exception e) {
            log.error("[OSS] 上传失败 key={}", key, e);
            throw new IOException("OSS 上传失败: " + e.getMessage(), e);
        }
        log.info("[OSS] 已上传 key={} size={}", key, file.getSize());
        return key;
    }

    @Override
    public String toUrl(String storageKey) {
        if (baseUrl != null && !baseUrl.isBlank()) {
            return baseUrl.endsWith("/") ? baseUrl + storageKey : baseUrl + "/" + storageKey;
        }
        // 默认：https://{bucket}.{endpoint}/{key}
        String host = endpoint.replaceFirst("^https?://", "");
        return "https://" + bucket + "." + host + "/" + storageKey;
    }

    @PreDestroy
    public void shutdown() {
        if (client != null) {
            client.shutdown();
            log.info("[OSS] 客户端已关闭");
        }
    }

    private String extensionOf(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "bin";
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }
}