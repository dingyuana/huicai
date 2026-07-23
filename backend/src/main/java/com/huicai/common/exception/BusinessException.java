package com.huicai.common.exception;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(String message) {
        super(message);
        this.code = 500;
    }

    public static BusinessException badRequest(String msg) {
        return new BusinessException(400, msg);
    }

    public static BusinessException notFound(String msg) {
        return new BusinessException(404, msg);
    }

    public static BusinessException conflict(String msg) {
        return new BusinessException(409, msg);
    }

    public static BusinessException unauthorized(String msg) {
        return new BusinessException(401, msg);
    }

    public static BusinessException forbidden(String msg) {
        return new BusinessException(403, msg);
    }
}