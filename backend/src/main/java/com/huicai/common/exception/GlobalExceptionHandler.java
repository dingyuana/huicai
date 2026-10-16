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
     * 方法级鉴权拒绝（{@code @PreAuthorize} 抛出）— 403。
     *
     * <p>🔴 <b>此前无此处理器，被兜底的 {@code Exception} 捕获并返回 500「系统繁忙」</b>。
     * 后果：权限拒绝看起来像<b>服务端故障</b>，排障方向被完全带偏 ——
     * 实测（2026-10-16）会计调用受保护的删除端点，日志里是
     * {@code 系统异常: Access Denied}，HTTP 500。
     *
     * <p>⚠️ 注意与<b>过滤器层</b>的 {@code AccessDeniedException} 区分：后者由
     * {@code SecurityConfig} 的 {@code accessDeniedHandler} 处理（返回 403）；
     * 本处理器只补<b>方法级</b>（{@code @PreAuthorize}）这一路。
     */
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public R<Void> handleAccessDenied(org.springframework.security.access.AccessDeniedException e) {
        log.warn("权限不足: {}", e.getMessage());
        return R.fail(HttpStatus.FORBIDDEN.value(), "权限不足，无法执行该操作");
    }

    /**
     * 方法级鉴权时<b>完全没有认证上下文</b>（无 SecurityContext）—— 401。
     *
     * <p>⚠️ 与 {@link #handleAccessDenied} 是<b>两回事</b>：有身份但权限不够 ⇒ 403；
     * <b>根本没身份</b>（如 {@code @AutoConfigureMockMvc(addFilters = false)} 的测试、
     * 或过滤器链被绕过）⇒ Spring Security 抛的是
     * {@code AuthenticationCredentialsNotFoundException}，
     * 它<b>不是</b> {@code AccessDeniedException}，故上一处理器接不住，会落到兜底变 500。
     */
    @ExceptionHandler(org.springframework.security.authentication.AuthenticationCredentialsNotFoundException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public R<Void> handleNoAuthenticationContext(
            org.springframework.security.authentication.AuthenticationCredentialsNotFoundException e) {
        log.warn("缺少认证上下文: {}", e.getMessage());
        return R.fail(HttpStatus.UNAUTHORIZED.value(), "未登录或缺少认证信息");
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