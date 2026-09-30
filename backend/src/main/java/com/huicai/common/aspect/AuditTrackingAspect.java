package com.huicai.common.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huicai.common.annotation.Auditable;
import com.huicai.base.system.entity.AuditLogEntity;
import com.huicai.base.system.service.AuditLogService;
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
            Long userId = getCurrentUserId();

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
                auditLog.setUserId(userId);
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

                auditLogService.save(auditLog);
            }
        } finally {
            AUDITING.set(false);
        }
    }

    @Around("execution(* com.baomidou.mybatisplus.core.mapper.BaseMapper.insert(..)) && args(entity) && !args(com.huicai.base.system.entity.AuditLogEntity)")
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

            Object result = pjp.proceed();

            // P103：必须在 proceed() **之后**取 id 与序列化快照。
            // id 由数据库自增回填，proceed() 前 entity 里还是 null ——
            // 若在 proceed() 前序列化，审计行的 entity_id 与快照里的 id 全为 null，
            // idx_audit_log_entity 索引依然形同虚设（「有行但关联不回业务对象」）。
            Long entityId = toLongId(getIdValueQuietly(entity));
            String afterSnapshot = serializeEntity(entity);

            AuditLogEntity auditLog = new AuditLogEntity();
            auditLog.setUsername(getCurrentUsername());
            auditLog.setUserId(getCurrentUserId());
            auditLog.setOperation("CREATE");
            auditLog.setMethod("insert");
            auditLog.setStatus("success");
            auditLog.setModule(module);
            auditLog.setEntityType(extractEntityType(entity));
            auditLog.setEntityId(entityId);
            auditLog.setEntityNo(extractEntityNo(entity));
            auditLog.setAfterData(afterSnapshot);
            auditLog.setRequestParams(afterSnapshot);
            auditLog.setNewSnapshot(afterSnapshot);

            auditLogService.save(auditLog);

            return result;
        } finally {
            AUDITING.set(false);
        }
    }

    /**
     * P103 补齐：{@code updateById} 此前<b>完全没有审计</b>，
     * 而「谁在什么时候把什么改成什么」恰恰是审计的核心诉求
     * （原实现只拦 insert 与 deleteById，见 SPEC 缺陷 2）。
     *
     * <p>必须在 {@code proceed()} <b>之前</b>读旧值，否则拿到的已是新数据，
     * before/after 会相同 —— 那正是本类 AT-103-3 要防的假通过。
     */
    @Around("execution(* com.baomidou.mybatisplus.core.mapper.BaseMapper.updateById(..)) && args(entity) && !args(com.huicai.base.system.entity.AuditLogEntity)")
    public Object aroundUpdateById(ProceedingJoinPoint pjp, Object entity) throws Throwable {
        if (AUDITING.get()) {
            return pjp.proceed();
        }

        String entityClassName = entity.getClass().getName();
        if (!isBusinessModuleEntity(entityClassName)) {
            return pjp.proceed();
        }

        AUDITING.set(true);
        try {
            Object idValue = getIdValueQuietly(entity);
            String beforeSnapshot = serializeEntity(selectOldEntity(entity, idValue));
            String module = extractModuleFromEntity(entity);

            Object result = pjp.proceed();

            // after 快照同样要在 proceed() 之后取（MP 的乐观锁/自动填充可能改写实体）
            String afterSnapshot = serializeEntity(entity);

            AuditLogEntity auditLog = new AuditLogEntity();
            auditLog.setUsername(getCurrentUsername());
            auditLog.setUserId(getCurrentUserId());
            auditLog.setOperation("UPDATE");
            auditLog.setMethod("updateById");
            auditLog.setStatus("success");
            auditLog.setModule(module);
            auditLog.setEntityType(extractEntityType(entity));
            auditLog.setEntityId(toLongId(idValue));
            auditLog.setEntityNo(extractEntityNo(entity));
            auditLog.setBeforeData(beforeSnapshot);
            auditLog.setAfterData(afterSnapshot);
            auditLogService.save(auditLog);

            return result;
        } catch (Throwable throwable) {
            try {
                AuditLogEntity failLog = new AuditLogEntity();
                failLog.setUsername(getCurrentUsername());
                failLog.setUserId(getCurrentUserId());
                failLog.setOperation("UPDATE");
                failLog.setMethod("updateById");
                failLog.setStatus("fail");
                failLog.setResponseResult(throwable.getMessage());
                failLog.setModule(extractModuleFromEntity(entity));
                failLog.setEntityType(extractEntityType(entity));
                failLogService(failLog);
            } catch (Exception ignored) {
                log.warn("记录 UPDATE 失败审计时自身异常", ignored);
            }
            throw throwable;
        } finally {
            AUDITING.set(false);
        }
    }

    private void failLogService(AuditLogEntity entity) {
        auditLogService.save(entity);
    }

    private Object getIdValueQuietly(Object entity) {
        try {
            return getIdValue(entity);
        } catch (Exception e) {
            return null;
        }
    }

    private Long toLongId(Object id) {
        if (id instanceof Number n) {
            return n.longValue();
        }
        if (id == null) {
            return null;
        }
        try {
            return Long.parseLong(id.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 实体名去掉 Entity 后缀，如 VoucherEntity → Voucher */
    private String extractEntityType(Object entity) {
        String simple = entity.getClass().getSimpleName();
        return simple.endsWith("Entity") ? simple.substring(0, simple.length() - 6) : simple;
    }

    private String extractEntityTypeFromMapper(ProceedingJoinPoint pjp) {
        try {
            String mapperName = pjp.getTarget().getClass().getSimpleName();
            // XxxMapper -> Xxx（与实体名一致，便于按 entity_type 关联回溯）
            return mapperName.endsWith("Mapper")
                    ? mapperName.substring(0, mapperName.length() - 6)
                    : mapperName;
        } catch (Exception e) {
            return null;
        }
    }

    /** 取业务编号字段（凭证号/单据号等），用于人工按编号检索审计 */
    private String extractEntityNo(Object entity) {
        for (String candidate : new String[]{"voucherNo", "docNo", "statementNo", "code", "number"}) {
            try {
                java.lang.reflect.Field f = entity.getClass().getDeclaredField(candidate);
                f.setAccessible(true);
                Object v = f.get(entity);
                if (v != null) {
                    return String.valueOf(v);
                }
            } catch (NoSuchFieldException ignored) {
                // 换下一个候选
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
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
            auditLog.setUserId(getCurrentUserId());
            auditLog.setOperation("DELETE");
            auditLog.setMethod("deleteById");
            auditLog.setRequestParams("{\"id\":" + id + "}");
            auditLog.setStatus("success");
            auditLog.setModule(module);
            // P103：写入真实列，使 idx_audit_log_entity 可用（此前恒为 NULL，索引形同虚设）
            auditLog.setEntityId(toLongId(id));
            auditLog.setEntityType(extractEntityTypeFromMapper(pjp));
            auditLog.setBeforeData("{\"id\":" + id + "}");

            try {
                Object result = pjp.proceed();
                auditLogService.save(auditLog);
                return result;
            } catch (Throwable throwable) {
                auditLog.setStatus("fail");
                auditLog.setResponseResult(throwable.getMessage());
                auditLogService.save(auditLog);
                throw throwable;
            }
        } finally {
            AUDITING.set(false);
        }
    }

    private boolean isBusinessModuleEntity(String className) {
        return className.startsWith("com.huicai.base.voucher.entity.") ||
               className.startsWith("com.huicai.sme.arap.entity.") ||
               className.startsWith("com.huicai.sme.tax.entity.") ||
               className.startsWith("com.huicai.sme.cash.entity.");
    }

    private boolean isBusinessModuleMapper(String className) {
        return className.startsWith("com.huicai.base.voucher.mapper.") ||
               className.startsWith("com.huicai.sme.arap.mapper.") ||
               className.startsWith("com.huicai.sme.tax.mapper.") ||
               className.startsWith("com.huicai.sme.cash.mapper.");
    }

    private String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            return auth.getName();
        }
        return "anonymous";
    }

    private Long getCurrentUserId() {
        try {
            return com.huicai.base.system.util.SecurityUtils.getCurrentUserId();
        } catch (Exception e) {
            return null;
        }
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
        // ⚠️ 必须沿类继承链向上找：`@TableId` 通常声明在父类 BaseEntity 上，
        // 而 getDeclaredFields() 只返回本类声明的字段 —— 只查本类会永远返回 null，
        // 导致审计行的 entity_id 恒为 NULL（与 AGENTS §4.2 第 20 条同源）。
        Class<?> clazz = entity.getClass();
        while (clazz != null && clazz != Object.class) {
            for (Field f : clazz.getDeclaredFields()) {
                if (f.isAnnotationPresent(com.baomidou.mybatisplus.annotation.TableId.class)) {
                    f.setAccessible(true);
                    Object v = f.get(entity);
                    if (v != null) {
                        return v;
                    }
                }
            }
            clazz = clazz.getSuperclass();
        }
        // 兜底：按字段名找
        clazz = entity.getClass();
        while (clazz != null && clazz != Object.class) {
            try {
                Field idField = clazz.getDeclaredField("id");
                idField.setAccessible(true);
                Object v = idField.get(entity);
                if (v != null) {
                    return v;
                }
            } catch (NoSuchFieldException ignored) {
                // 继续向父类找
            }
            clazz = clazz.getSuperclass();
        }
        return null;
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