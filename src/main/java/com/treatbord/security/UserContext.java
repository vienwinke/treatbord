package com.treatbord.security;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 当前登录用户上下文（ThreadLocal 存储，拦截器填充，请求结束清理）。
 */
public final class UserContext {

    private static final ThreadLocal<CurrentUser> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(CurrentUser user) {
        HOLDER.set(user);
    }

    /** 获取当前用户；未登录（匿名访问）返回 null */
    public static CurrentUser get() {
        return HOLDER.get();
    }

    /** 获取当前用户 id（已由拦截器保证登录） */
    public static Long userId() {
        CurrentUser u = HOLDER.get();
        return u == null ? null : u.getUserId();
    }

    public static boolean isAdmin() {
        CurrentUser u = HOLDER.get();
        return u != null && Integer.valueOf(1).equals(u.getRole());
    }

    public static void clear() {
        HOLDER.remove();
    }

    /** 当前用户快照（最小化数据，避免把整个对象塞进 ThreadLocal） */
    @Data
    @AllArgsConstructor
    public static class CurrentUser {
        private Long userId;
        private Integer role;
        private Integer status;
        private String jti;
    }
}