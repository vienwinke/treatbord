package com.treatbord.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 密码哈希（标准化升级版，docs/STANDARDIZATION_PLAN.md P0-4）。
 *
 * <p>存储格式：
 * <ul>
 *   <li><b>BCrypt</b>（新写入）：{@code $2a$10$...}，慢哈希抗暴力破解</li>
 *   <li><b>旧格式</b>（兼容校验）：{@code base64(salt):base64(sha256(salt+pepper+raw))}，
 *       登录成功后由 UserService 自动升级为 BCrypt</li>
 * </ul>
 *
 * <p>密码先经 {@code sha256(pepper + raw)} 预哈希再交给 BCrypt：
 * 既保留 pepper 的额外防护，又消除 BCrypt 的 72 字节长度限制。
 */
@Component
public class PasswordEncoder {

    private static final int SALT_BYTES = 16;
    private static final int BCRYPT_STRENGTH = 10;
    private static final String LEGACY_SEPARATOR = ":";

    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder(BCRYPT_STRENGTH);
    private final SecureRandom random = new SecureRandom();
    private final String pepper;

    public PasswordEncoder(@Value("${treatbord.security.password-pepper:dev-pepper-change-me}") String pepper) {
        this.pepper = pepper;
    }

    /** 生成 BCrypt 哈希（新密码一律走这里）。 */
    public String encode(String rawPassword) {
        if (rawPassword == null || rawPassword.isEmpty()) {
            throw new IllegalArgumentException("密码不能为空");
        }
        return bcrypt.encode(preHash(rawPassword));
    }

    /**
     * 校验明文密码：自动识别 BCrypt 与旧格式（常量时间比较）。
     */
    public boolean matches(String rawPassword, String encoded) {
        if (rawPassword == null || encoded == null || encoded.isEmpty()) {
            return false;
        }
        if (isBcrypt(encoded)) {
            return bcrypt.matches(preHash(rawPassword), encoded);
        }
        return matchesLegacy(rawPassword, encoded);
    }

    /** 是否为需要升级的旧格式哈希（登录成功后自动迁移为 BCrypt）。 */
    public boolean needsUpgrade(String encoded) {
        return encoded != null && !encoded.isEmpty() && !isBcrypt(encoded);
    }

    // ---------- 内部实现 ----------

    private boolean isBcrypt(String encoded) {
        return encoded.startsWith("$2a$") || encoded.startsWith("$2b$") || encoded.startsWith("$2y$");
    }

    /** 预哈希：sha256(pepper + raw) -> base64（固定 44 字符，规避 BCrypt 72 字节限制）。 */
    private String preHash(String rawPassword) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(pepper.getBytes(StandardCharsets.UTF_8));
            md.update(rawPassword.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(md.digest());
        } catch (Exception e) {
            throw new IllegalStateException("密码预哈希失败", e);
        }
    }

    /** 旧格式校验：base64(salt):base64(sha256(salt + pepper + raw))。 */
    private boolean matchesLegacy(String rawPassword, String encoded) {
        int sep = encoded.indexOf(LEGACY_SEPARATOR);
        if (sep < 0) {
            return false;
        }
        try {
            byte[] salt = Base64.getDecoder().decode(encoded.substring(0, sep));
            byte[] expected = Base64.getDecoder().decode(encoded.substring(sep + 1));
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt);
            md.update(pepper.getBytes(StandardCharsets.UTF_8));
            md.update(rawPassword.getBytes(StandardCharsets.UTF_8));
            return MessageDigest.isEqual(md.digest(), expected);
        } catch (Exception e) {
            return false;
        }
    }

    /** 保留：旧格式生成能力（仅供测试/历史数据构造使用，业务不再调用）。 */
    @Deprecated
    public String encodeLegacy(String rawPassword) {
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt);
            md.update(pepper.getBytes(StandardCharsets.UTF_8));
            md.update(rawPassword.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(salt) + LEGACY_SEPARATOR
                    + Base64.getEncoder().encodeToString(md.digest());
        } catch (Exception e) {
            throw new IllegalStateException("旧格式哈希失败", e);
        }
    }
}