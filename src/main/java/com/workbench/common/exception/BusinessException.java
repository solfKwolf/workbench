package com.workbench.common.exception;

import lombok.Getter;

/** 业务异常：code 同时作为 HTTP 状态码 */
@Getter
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }
}
