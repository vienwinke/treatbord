package com.treatbord.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 启动配置校验（docs/STANDARDIZATION_PLAN.md P0-2）。
 *
 * <p>生产环境（prod profile）下校验必需密钥与强度，缺失/过弱直接拒绝启动，
 * 避免"带默认密钥上线"这类高危事故。开发环境跳过。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StartupValidator implements InitializingBean {

    /** 生产必需的非空环境变量 */
    private static final List<String> REQUIRED_IN_PROD = List.of(
            "JWT_SECRET",
            "PASSWORD_PEPPER",
            "WX_APPID",
            "WX_SECRET",
            "MYSQL_HOST",
            "MYSQL_USER",
            "MYSQL_PASSWORD",
            "MYSQL_MIGRATE_USER",
            "MYSQL_MIGRATE_PASSWORD",
            "REDIS_HOST"
    );

    /** 开发默认密钥前缀：生产出现即视为未配置 */
    private static final List<String> DEV_PLACEHOLDER_PREFIXES = List.of(
            "dev-only", "dev-pepper", "dev-pepper-change-me"
    );

    private static final int JWT_MIN_BYTES = 32;

    private final Environment env;

    @Override
    public void afterPropertiesSet() {
        boolean isProd = Arrays.asList(env.getActiveProfiles()).contains("prod");
        if (!isProd) {
            log.info("[StartupValidator] 非生产环境（profiles={}），跳过密钥强校验",
                    Arrays.toString(env.getActiveProfiles()));
            return;
        }

        List<String> problems = new ArrayList<>();

        // 1. 必需项非空
        for (String key : REQUIRED_IN_PROD) {
            String value = env.getProperty(key);
            if (value == null || value.isBlank()) {
                problems.add("缺少环境变量 " + key);
            }
        }

        // 2. JWT 密钥强度
        String jwtSecret = env.getProperty("JWT_SECRET", "");
        if (!jwtSecret.isBlank()) {
            if (jwtSecret.getBytes(StandardCharsets.UTF_8).length < JWT_MIN_BYTES) {
                problems.add("JWT_SECRET 长度不足 " + JWT_MIN_BYTES + " 字节");
            }
            for (String prefix : DEV_PLACEHOLDER_PREFIXES) {
                if (jwtSecret.startsWith(prefix)) {
                    problems.add("JWT_SECRET 仍为开发默认值，生产必须更换");
                    break;
                }
            }
        }

        // 3. pepper 不得为开发默认值
        String pepper = env.getProperty("PASSWORD_PEPPER", "");
        for (String prefix : DEV_PLACEHOLDER_PREFIXES) {
            if (pepper.startsWith(prefix)) {
                problems.add("PASSWORD_PEPPER 仍为开发默认值，生产必须更换");
                break;
            }
        }

        // 4. 存储类型为 oss 时校验 OSS 配置
        String storageType = env.getProperty("treatbord.storage.type", "local");
        if ("oss".equalsIgnoreCase(storageType)) {
            for (String key : List.of("OSS_ENDPOINT", "OSS_BUCKET", "OSS_ACCESS_KEY_ID", "OSS_ACCESS_KEY_SECRET")) {
                String v = env.getProperty(key);
                if (v == null || v.isBlank()) {
                    problems.add("存储类型为 oss 但缺少 " + key);
                }
            }
        }

        if (!problems.isEmpty()) {
            String msg = "\n========================================\n"
                    + "生产环境配置校验失败，拒绝启动：\n  - "
                    + String.join("\n  - ", problems)
                    + "\n请通过环境变量注入后重试。\n"
                    + "========================================";
            log.error(msg);
            throw new IllegalStateException("生产环境配置不完整: " + problems);
        }

        log.info("[StartupValidator] 生产环境配置校验通过（storage={}）", storageType);
    }
}