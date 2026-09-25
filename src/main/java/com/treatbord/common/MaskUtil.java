package com.treatbord.common;

/**
 * 敏感信息脱敏工具（docs/SECURITY_REVIEW.md D2/D4）。
 * 日志与对外输出统一使用，禁止明文打印 openid / 手机号 / token。
 */
public final class MaskUtil {

    private MaskUtil() {
    }

    /**
     * openid 脱敏：保留首字符与末 4 位，中间以 **** 代替。
     * 例：openid_test_abc123xyz -> o****3xyz
     */
    public static String maskOpenid(String openid) {
        if (openid == null || openid.isEmpty()) {
            return openid;
        }
        if (openid.length() <= 6) {
            return "****";
        }
        return openid.charAt(0) + "****" + openid.substring(openid.length() - 4);
    }

    /** 手机号脱敏：138****8000 */
    public static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone == null ? null : "****";
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    /** 邮箱脱敏：ab****@example.com */
    public static String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return email == null ? null : "****";
        }
        int at = email.indexOf('@');
        String name = email.substring(0, at);
        if (name.length() <= 2) {
            return "****" + email.substring(at);
        }
        return name.substring(0, 2) + "****" + email.substring(at);
    }

    /** 通用：保留前 prefixLen 与后 suffixLen 位 */
    public static String maskMiddle(String value, int prefixLen, int suffixLen) {
        if (value == null || value.length() <= prefixLen + suffixLen) {
            return value == null ? null : "****";
        }
        return value.substring(0, prefixLen) + "****"
                + value.substring(value.length() - suffixLen);
    }
}