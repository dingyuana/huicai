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

    /**
     * 429 —— 超出配额/频率限制。
     *
     * <p>SPEC-P135（AI 配额治理）引入。刻意与 {@code forbidden} 分开：
     * **403 是「你无权」，429 是「你有权但额度用完」** ——
     * 前端要区别对待（前者该换账号，后者该提示「明日再来」）。</p>
     */
    public static BusinessException tooManyRequests(String msg) {
        return new BusinessException(429, msg);
    }
}