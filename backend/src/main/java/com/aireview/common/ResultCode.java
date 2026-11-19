package com.aireview.common;

/**
 * 返回码约定：0 成功；4xx 与 HTTP 语义对齐；业务错误自定义 4xxx 段。
 */
public enum ResultCode {
    SUCCESS(0, "OK"),
    BAD_REQUEST(400, "请求参数不合法"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    LOGIN_FAILED(401, "用户名或密码错误"),
    FORBIDDEN(403, "无权访问该资源"),
    NOT_FOUND(404, "资源不存在"),
    CONFLICT(409, "操作冲突，请稍后重试"),
    USERNAME_TAKEN(409, "用户名已被占用"),
    LOCKED(423, "账号已锁定，请稍后再试"),
    TOO_MANY_REQUESTS(429, "请求过于频繁，请稍后再试"),
    INTERNAL_ERROR(500, "服务器内部错误");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int code() {
        return code;
    }

    public String message() {
        return message;
    }
}
