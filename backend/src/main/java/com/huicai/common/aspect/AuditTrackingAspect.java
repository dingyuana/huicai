package com.huicai.common.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huicai.common.annotation.Auditable;
import com.huicai.module.system.entity.AuditLogEntity;
import com.huicai.module.system.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.Objects;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditTrackingAspect {

    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;
    private final org.springframework.context.ApplicationContext applicationContext;

    private static final ThreadLocal<Boolean> AUDITING = ThreadLocal.withInitial(() -> false);

    @Around("@annotation(auditable)")
    public Object aroundAuditable(ProceedingJoinPoint pjp, Auditable auditable) throws Throwable {
        if (AUDITING.get()) {
            return pjp.proceed();
        }

        AUDITING.set(true);
        try {
            long startTime = System.currentTimeMillis();

            MethodSignature signature = (MethodSignature) pjp.getSignature();

            String operation = auditable.operation();
            String module = auditable.module();

            String username = getCurrentUsername();

            String requestParams = serializeArgs(pjp.getArgs());

            String status = "success";
            String responseResult = "";
            String oldSnapshot = null;
            String newSnapshot = null;
            long executionTime = 0;

            try {
                Object[] args = pjp.getArgs();
                if (auditable.trackSnapshot() && args.length > 0) {
                    Object target = args[0];
                    if (target != null) {
                        Object idValue = getIdValue(target);
                        if (idValue != null) {
                            Object oldEntity = selectOldEntity(target, idValue);
                            if (oldEntity != null) {
                                oldSnapshot = serializeEntity(oldEntity);
                            }
                        }
                    }
                }

                Object result = pjp.proceed();
                executionTime = System.currentTimeMillis() - startTime;

                try {
                    responseResult = objectMapper.writeValueAsString(result);
                } catch (Exception e) {
                    responseResult = "{}";
                }

                if (auditable.trackSnapshot() && args.length > 0) {
                    Object target = args[0];
                    if (target != null) {
                        newSnapshot = serializeEntity(target);
                    }
                }

                return result;
            } catch (Throwable throwable) {
                executionTime = System.currentTimeMillis() - startTime;
                status = "fail";
                responseResult = throwable.getMessage();
                throw throwable;
            } finally {
                AuditLogEntity auditLog = new AuditLogEntity();
                auditLog.setUsername(username);
                auditLog.setOperation(operation);
                auditLog.setMethod(signature.getName());
                auditLog.setRequestParams(requestParams);
                auditLog.setResponseResult(responseResult);
                auditLog.setOldSnapshot(oldSnapshot);
                auditLog.setNewSnapshot(newSnapshot);
                auditLog.setExecutionTimeMs((int) executionTime);
                auditLog.setStatus(status);
                auditLog.setModule(module);

                auditLogService.saveAsync(auditLog);
            }
        } finally {
            AUDITING.set(false);
        }
    }

    @Around("execution(* com.baomidou.mybatisplus.core.mapper.BaseMapper.insert(..)) && args(entity) && !args(com.huicai.module.system.entity.AuditLogEntity)")
    public Object aroundInsert(ProceedingJoinPoint pjp, Object entity) throws Throwable {
        if (AUDITING.get()) {
            return pjp.proceed();
        }

        String entityClassName = entity.getClass().getName();
        if (!isBusinessModuleEntity(entityClassName)) {
            return pjp.proceed();
        }

        AUDITING.set(true);
        try {
            String module = extractModuleFromEntity(entity);
            String newSnapshot = serializeEntity(entity);

            Object result = pjp.proceed();

            AuditLogEntity auditLog = new AuditLogEntity();
            auditLog.setUsername(getCurrentUsername());
            auditLog.setOperation("CREATE");
            auditLog.setMethod("insert");
            auditLog.setRequestParams(newSnapshot);
            auditLog.setNewSnapshot(newSnapshot);
            auditLog.setStatus("success");
            auditLog.setModule(module);

            auditLogService.saveAsync(auditLog);

            return result;
        } finally {
            AUDITING.set(false);
        }
    }

    @Around("execution(* com.baomidou.mybatisplus.core.mapper.BaseMapper.deleteById(..)) && args(id)")
    public Object aroundDeleteById(ProceedingJoinPoint pjp, Object id) throws Throwable {
        if (AUDITING.get()) {
            return pjp.proceed();
        }

        String mapperClassName = pjp.getTarget().getClass().getName();
        if (!isBusinessModuleMapper(mapperClassName)) {
            return pjp.proceed();
        }

        AUDITING.set(true);
        try {
            String module = extractModuleFromMapper(pjp);

            AuditLogEntity auditLog = new AuditLogEntity();
            auditLog.setUsername(getCurrentUsername());
            auditLog.setOperation("DELETE");
            auditLog.setMethod("deleteById");
            auditLog.setRequestParams("{\"id\":" + id + "}");
            auditLog.setStatus("success");
            auditLog.setModule(module);

            try {
                Object result = pjp.proceed();
                auditLogService.saveAsync(auditLog);
                return result;
            } catch (Throwable throwable) {
                auditLog.setStatus("fail");
                auditLog.setResponseResult(throwable.getMessage());
                auditLogService.saveAsync(auditLog);
                throw throwable;
            }
        } finally {
            AUDITING.set(false);
        }
    }

    private boolean isBusinessModuleEntity(String className) {
        return className.startsWith("com.huicai.module.finance.entity.")
                || className.startsWith("com.huicai.sme.arap.entity.")
                || className.startsWith("com.huicai.sme.tax.entity.");
    }

    private boolean isBusinessModuleMapper(String className) {
        return className.startsWith("com.huicai.module.finance.mapper.")
                || className.startsWith("com.huicai.sme.arap.mapper.")
                || className.startsWith("com.huicai.sme.tax.mapper.");
    }

    private String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            return auth.getName();
        }
        return "anonymous";
    }

    private String serializeArgs(Object[] args) {
        try {
            String json = objectMapper.writeValueAsString(args);
            json = json.replaceAll("\"password\"\\s*:\\s*\"[^\"]*\"", "\"password\":\"******\"");
            json = json.replaceAll("\"newPassword\"\\s*:\\s*\"[^\"]*\"", "\"newPassword\":\"******\"");
            return json;
        } catch (Exception e) {
            return "{}";
        }
    }

    private String serializeEntity(Object entity) {
        try {
            return objectMapper.writeValueAsString(entity);
        } catch (Exception e) {
            log.warn("序列化实体失败: {}", e.getMessage());
            return "{}";
        }
    }

    private Object getIdValue(Object entity) throws Exception {
        for (Field f : entity.getClass().getDeclaredFields()) {
            if (f.isAnnotationPresent(com.baomidou.mybatisplus.annotation.TableId.class)) {
                f.setAccessible(true);
                return f.get(entity);
            }
        }
        try {
            Field idField = entity.getClass().getDeclaredField("id");
            idField.setAccessible(true);
            return idField.get(entity);
        } catch (NoSuchFieldException e) {
            return null;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Object selectOldEntity(Object entity, Object idValue) {
        try {
            String entityClassName = entity.getClass().getSimpleName();
            String mapperName = entityClassName.replace("Entity", "Mapper");
            String mapperBeanName = Character.toLowerCase(mapperName.charAt(0)) + mapperName.substring(1);

            Object mapper = applicationContext.getBean(mapperBeanName);
            if (mapper instanceof com.baomidou.mybatisplus.core.mapper.BaseMapper) {
                return ((com.baomidou.mybatisplus.core.mapper.BaseMapper) mapper).selectById((java.io.Serializable) idValue);
            }
            return null;
        } catch (Exception e) {
            log.warn("无法获取旧 Entity 数据，跳过快照: {}", e.getMessage());
            return null;
        }
    }

    private String extractModuleFromEntity(Object entity) {
        String className = entity.getClass().getSimpleName();
        if (className.endsWith("Entity")) {
            return className.substring(0, className.length() - 6).toUpperCase();
        }
        return className.toUpperCase();
    }

    private String extractModuleFromMapper(ProceedingJoinPoint pjp) {
        String className = pjp.getTarget().getClass().getSimpleName();
        if (className.contains("$")) {
            className = className.substring(0, className.indexOf("$"));
        }
        if (className.endsWith("Mapper")) {
            return className.substring(0, className.length() - 6).toUpperCase();
        }
        return className.toUpperCase();
    }
}