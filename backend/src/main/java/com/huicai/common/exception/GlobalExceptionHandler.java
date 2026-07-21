package com.huicai.common.exception;

import com.huicai.common.response.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.NoHandlerFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 参数校验异常
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleValidation(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String msg = fieldError != null ? fieldError.getDefaultMessage() : "参数校验失败";
        log.warn("参数校验异常: {}", msg);
        return R.badRequest(msg);
    }

    /**
     * 认证异常 - 用户名或密码错误
     */
    @ExceptionHandler(BadCredentialsException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleBadCredentials(BadCredentialsException e) {
        log.warn("认证失败: {}", e.getMessage());
        return R.badRequest("用户名或密码错误");
    }

    /**
     * 用户不存在异常
     */
    @ExceptionHandler(UsernameNotFoundException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleUsernameNotFound(UsernameNotFoundException e) {
        log.warn("用户不存在: {}", e.getMessage());
        return R.badRequest("用户名或密码错误");
    }

    /**
     * 业务异常
     */
    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.OK)
    public R<Void> handleBusiness(BusinessException e) {
        log.warn("业务异常: code={}, msg={}", e.getCode(), e.getMessage());
        return R.fail(e.getCode(), e.getMessage());
    }

    /**
     * 路由不存在 (404) — 前端请求了不存在的 API 路径
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public R<Void> handleNoHandler(NoHandlerFoundException e) {
        log.warn("路由不存在: {} {} (来自: {})", e.getHttpMethod(), e.getRequestURL(), e.getHeaders());
        return R.fail(HttpStatus.NOT_FOUND.value(), "接口不存在: " + e.getHttpMethod() + " " + e.getRequestURL());
    }

    /**
     * 未知异常
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public R<Void> handleUnknown(Exception e) {
        log.error("系统异常: {}", e.getMessage(), e);
        return R.fail("系统繁忙，请稍后重试");
    }
}