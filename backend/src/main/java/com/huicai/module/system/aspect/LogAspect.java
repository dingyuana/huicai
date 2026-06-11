package com.huicai.module.system.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huicai.module.system.entity.AuditLogEntity;
import com.huicai.module.system.service.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;

@Aspect
@Component
@RequiredArgsConstructor
public class LogAspect {

    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    @Around("@annotation(com.huicai.module.system.aspect.Log)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Log logAnnotation = method.getAnnotation(Log.class);

        String operation = logAnnotation.value();
        String module = logAnnotation.module();

        // Get request info
        String ipAddress = "";
        String userAgent = "";
        HttpServletRequest request = null;
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            request = attributes.getRequest();
            ipAddress = request.getRemoteAddr();
            userAgent = request.getHeader("User-Agent");
        }

        // Get user info
        String username = "anonymous";
        Long userId = null;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            username = auth.getName();
        }

        // Capture request params (mask sensitive fields)
        String requestParams = "";
        try {
            Object[] args = joinPoint.getArgs();
            if (args != null && args.length > 0) {
                String json = objectMapper.writeValueAsString(args);
                // Mask password fields
                json = json.replaceAll("\"password\"\\s*:\\s*\"[^\"]*\"", "\"password\":\"******\"");
                json = json.replaceAll("\"newPassword\"\\s*:\\s*\"[^\"]*\"", "\"newPassword\":\"******\"");
                requestParams = json;
            }
        } catch (Exception e) {
            requestParams = "{}";
        }

        String status = "success";
        String responseResult = "";
        long executionTime = 0;

        try {
            Object result = joinPoint.proceed();
            executionTime = System.currentTimeMillis() - startTime;
            try {
                responseResult = objectMapper.writeValueAsString(result);
            } catch (Exception e) {
                responseResult = "{}";
            }

            return result;
        } catch (Throwable throwable) {
            executionTime = System.currentTimeMillis() - startTime;
            status = "fail";
            responseResult = throwable.getMessage();
            throw throwable;
        } finally {
            // Save audit log asynchronously
            AuditLogEntity auditLog = new AuditLogEntity();
            auditLog.setUserId(userId);
            auditLog.setUsername(username);
            auditLog.setOperation(operation);
            auditLog.setMethod(method.getName());
            auditLog.setRequestParams(requestParams);
            auditLog.setResponseResult(responseResult);
            auditLog.setIpAddress(ipAddress);
            auditLog.setUserAgent(userAgent);
            auditLog.setExecutionTimeMs((int) executionTime);
            auditLog.setStatus(status);
            auditLog.setModule(module);

            auditLogService.saveAsync(auditLog);
        }
    }
}
