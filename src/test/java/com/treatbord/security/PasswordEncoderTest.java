package com.treatbord.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 密码哈希测试：BCrypt 新格式、旧 SHA-256 兼容与升级标记、空密码拒绝。
 */
class PasswordEncoderTest {

    private final PasswordEncoder encoder = new PasswordEncoder("unit-test-pepper");

    @Test
    @DisplayName("新密码产出 BCrypt($2a$) 且可校验")
    void bcryptEncodeAndMatch() {
        String hash = encoder.encode("Passw0rd!");
        assertTrue(hash.startsWith("$2a$"), "应为 BCrypt 格式: " + hash);
        assertTrue(encoder.matches("Passw0rd!", hash));
        assertFalse(encoder.matches("wrong-password", hash));
        assertFalse(encoder.needsUpgrade(hash), "BCrypt 不需要升级");
    }

    @Test
    @DisplayName("同一密码两次哈希不同（加盐）")
    void saltMakesHashUnique() {
        assertNotEquals(encoder.encode("same-password"), encoder.encode("same-password"));
    }

    @Test
    @DisplayName("旧格式哈希可校验，且被标记为需要升级")
    void legacyHashIsVerifiableAndUpgradable() {
        String legacy = encoder.encodeLegacy("Passw0rd!");
        assertFalse(legacy.startsWith("$2a$"), "旧格式不是 BCrypt");
        assertTrue(encoder.matches("Passw0rd!", legacy), "旧格式必须能登录");
        assertFalse(encoder.matches("wrong-password", legacy));
        assertTrue(encoder.needsUpgrade(legacy), "旧格式应触发登录时自动升级");

        String upgraded = encoder.encode("Passw0rd!");
        assertTrue(encoder.matches("Passw0rd!", upgraded));
        assertFalse(encoder.needsUpgrade(upgraded));
    }

    @Test
    @DisplayName("不同 pepper 无法互相校验（防止换 pepper 后旧密码被接受）")
    void pepperIsPartOfSecret() {
        String hash = encoder.encode("Passw0rd!");
        PasswordEncoder other = new PasswordEncoder("another-pepper");
        assertFalse(other.matches("Passw0rd!", hash));
    }

    @Test
    @DisplayName("空密码拒绝、非法哈希不抛异常只返回 false")
    void edgeCases() {
        assertThrows(IllegalArgumentException.class, () -> encoder.encode(""));
        assertThrows(IllegalArgumentException.class, () -> encoder.encode(null));
        assertFalse(encoder.matches(null, "$2a$10$abcdefghijklmnopqrstuv"));
        assertFalse(encoder.matches("x", null));
        assertFalse(encoder.matches("x", ""));
        assertFalse(encoder.matches("x", "not-a-valid-hash"));
    }
}
