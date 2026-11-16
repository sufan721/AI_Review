package com.aireview.common;

public record Result<T>(int code, String message, T data) {
    public static <T> Result<T> ok(T data) {
        return new Result<>(0, "OK", data);
    }

    public static <T> Result<T> ok() {
        return new Result<>(0, "OK", null);
    }

    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null);
    }
}
