package com.huicai.common.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * P23 统一校验拦截器.
 * <p>
 * 通过 AOP 切面拦截所有 Service 层的 create/update/submit/audit/post/reverse 方法，
 * 在方法执行前统一记录校验日志，在方法执行后统一捕获并规范化异常。
 * </p>
 * <p>
 * 注意：此切面不替代业务层的手工校验（如 validateEntries、assertPeriodOpen），
 * 而是提供统一的校验日志记录和异常规范化能力。
 * </p>
 */
@Slf4j
@Aspect
@Component
public class ValidationAspect {

    /**
     * 拦截所有 Service 层的关键写操作方法。
     * 匹配规则: 所有 module 下的 service 包中，方法名为 create/update/submit/audit/post/reverse/confirm/reject/close/reopen 的方法。
     */
    @Around("execution(* com.huicai.module..service..*.create(..)) || " +
            "execution(* com.huicai.module..service..*.update(..)) || " +
            "execution(* com.huicai.module..service..*.submit(..)) || " +
            "execution(* com.huicai.module..service..*.audit(..)) || " +
            "execution(* com.huicai.module..service..*.post(..)) || " +
            "execution(* com.huicai.module..service..*.reverse(..)) || " +
            "execution(* com.huicai.module..service..*.confirm(..)) || " +
            "execution(* com.huicai.module..service..*.reject(..)) || " +
            "execution(* com.huicai.module..service..*.close(..)) || " +
            "execution(* com.huicai.module..service..*.reopen(..))")
    public Object aroundValidation(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().getName();
        String className = joinPoint.getSignature().getDeclaringTypeName();

        log.debug("P23 校验拦截: {}.{}() 参数={}", className, methodName,
                Arrays.toString(joinPoint.getArgs()));

        try {
            Object result = joinPoint.proceed();
            log.debug("P23 校验通过: {}.{}()", className, methodName);
            return result;
        } catch (com.huicai.common.exception.BusinessException e) {
            log.warn("P23 业务校验失败: {}.{}() -> {}", className, methodName, e.getMessage());
            throw e;
        }
    }
}