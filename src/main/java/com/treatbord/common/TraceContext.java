package com.treatbord.common;

/**
 * traceId 上下文（ThreadLocal，一次请求一个）。
 * 由 TraceIdFilter 在请求进入时设置、结束时清理；Result 装配 traceId 时读取。
 */
public final class TraceContext {

    private static final ThreadLocal<String> TRACE = new ThreadLocal<>();

    private TraceContext() {}

    public static void set(String traceId) {
        TRACE.set(traceId);
    }

    public static String get() {
        return TRACE.get();
    }

    public static void clear() {
        TRACE.remove();
    }
}
