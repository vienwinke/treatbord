package com.treatbord.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 密码哈希：SHA-256 + 随机盐 + pepper（MVP 用，免引入 BCrypt 依赖）。
 * 存储格式：base64(salt):base64(hash)，校验用常量时间比较。
 */
@Component
public class PasswordEncoder {

    private static final int SALT_BYTES = 16;
    private final SecureRandom random = new SecureRandom();
    private final String pepper;

    public PasswordEncoder(@Value("${treatbord.security.password-pepper:dev-pepper-change-me}") String pepper) {
        this.pepper = pepper;
    }

    /** 生成加盐哈希 */
    public String encode(String rawPassword) {
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        byte[] hash = digest(salt, rawPassword);
        return Base64.getEncoder().encodeToString(salt) + ":" + Base64.getEncoder().encodeToString(hash);
    }

    /** 校验明文密码是否匹配存储的加盐哈希 */
    public boolean matches(String rawPassword, String encoded) {
        if (rawPassword == null || encoded == null) return false;
        int sep = encoded.indexOf(':');
        if (sep < 0) return false;
        try {
            byte[] salt = Base64.getDecoder().decode(encoded.substring(0, sep));
            byte[] expected = Base64.getDecoder().decode(encoded.substring(sep + 1));
            byte[] actual = digest(salt, rawPassword);
            return MessageDigest.isEqual(actual, expected);
        } catch (Exception e) {
            return false;
        }
    }

    private byte[] digest(byte[] salt, String rawPassword) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt);
            md.update(pepper.getBytes(StandardCharsets.UTF_8));
            md.update(rawPassword.getBytes(StandardCharsets.UTF_8));
            return md.digest();
        } catch (Exception e) {
            throw new IllegalStateException("密码哈希失败", e);
        }
    }
}
