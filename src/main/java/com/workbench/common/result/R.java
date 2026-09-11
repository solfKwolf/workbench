package com.workbench.common.result;

import lombok.Data;

/**
 * 统一响应：{code, message, data}
 * code 与 HTTP 状态码语义一致（200/400/401/404/500）
 */
@Data
public class R<T> {

    private int code;
    private String message;
    private T data;

    private R(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> R<T> ok() {
        return new R<>(200, "success", null);
    }

    public static <T> R<T> ok(T data) {
        return new R<>(200, "success", data);
    }

    public static <T> R<T> fail(int code, String message) {
        return new R<>(code, message, null);
    }
}
