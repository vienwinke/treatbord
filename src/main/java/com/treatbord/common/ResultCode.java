package com.treatbord.common;

import lombok.Getter;

/**
 * 业务错误码（与 docs/API_DESIGN.md §1.2 对应）。
 */
@Getter
public enum ResultCode {

    SUCCESS(0, "ok"),
    // 通用
    BAD_REQUEST(400, "参数错误"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "无权限操作"),
    NOT_FOUND(404, "资源不存在"),
    CONFLICT(409, "状态冲突，非法流转"),
    TOO_MANY_REQUESTS(429, "请求过于频繁，请稍后再试"),
    INTERNAL_ERROR(500, "服务器内部错误"),

    // 业务（1001+）
    WX_LOGIN_FAILED(1001, "微信登录凭证无效"),
    USER_BANNED(1002, "账号已被封禁"),
    USER_NOT_FOUND(1003, "用户不存在"),
    CREDIT_NOT_ENOUGH(1004, "信用分不足，无法接取"),

    // 任务（2001+）
    TASK_NOT_FOUND(2001, "任务不存在"),
    TASK_NOT_CLAIMABLE(2002, "任务当前不可接取"),
    TASK_FULL(2003, "任务名额已满"),
    TASK_CANCEL_NOT_ALLOWED(2004, "当前状态不允许取消"),
    SELF_CLAIM_FORBIDDEN(2005, "不能接取自己发布的任务"),

    // 接取/凭证（3001+）
    CLAIM_NOT_FOUND(3001, "接取记录不存在"),
    CLAIM_DUPLICATE(3002, "您已接取过该任务"),
    CLAIM_NOT_SUBMITTABLE(3003, "当前状态不允许提交"),
    CLAIM_NOT_REVIEWABLE(3004, "当前状态不允许审核"),
    CLAIM_CANCEL_NOT_ALLOWED(3005, "当前状态不允许取消"),

    // 文件（4001+）
    FILE_TYPE_NOT_ALLOWED(4001, "文件类型不被允许"),
    FILE_TOO_LARGE(4002, "文件大小超过限制"),
    FILE_CONTENT_ILLEGAL(4003, "文件内容未通过安全检查"),

    // 内容安全（5001+）
    CONTENT_ILLEGAL(5001, "内容包含违规信息");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}