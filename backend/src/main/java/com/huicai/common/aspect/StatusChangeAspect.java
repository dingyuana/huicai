package com.huicai.common.aspect;

import com.huicai.common.annotation.StatusChangeable;
import com.huicai.base.system.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.Objects;

/**
 * AOP 切面：拦截 MyBatis-Plus BaseMapper.updateById，
 * 检测 Entity 中 {@link StatusChangeable} 标记的字段值变化，
 * 自动写入 t_audit_log。
 *
 * 需求文档：docs/需求分析书_发票与凭证状态机_V1.0.md §6
 * SPEC：docs/specs/P24-audit-tracking.md
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class StatusChangeAspect {

    private final AuditLogService auditLogService;
    private final ApplicationContext applicationContext;

    /**
     * 拦截所有 BaseMapper.updateById 调用。
     * 如果 Entity 含 @StatusChangeable 字段且值发生变化，写入审计日志。
     */
    @Around("execution(* com.baomidou.mybatisplus.core.mapper.BaseMapper.updateById(..))" +
            " && args(entity)")
    public Object aroundUpdateById(ProceedingJoinPoint pjp, Object entity) throws Throwable {
        // 1. 查找 @StatusChangeable 字段
        Field[] fields = entity.getClass().getDeclaredFields();
        Field statusField = null;
        StatusChangeable annotation = null;
        for (Field f : fields) {
            StatusChangeable a = f.getAnnotation(StatusChangeable.class);
            if (a != null) {
                statusField = f;
                annotation = a;
                break;
            }
        }

        // 2. 没有标记的字段，直接放过
        if (statusField == null || annotation == null) {
            return pjp.proceed();
        }

        // 3. 取新值
        statusField.setAccessible(true);
        Object newValue = statusField.get(entity);

        // 4. 查旧值（通过 selectById 在 update 前拿到）
        Object idValue = getIdValue(entity);
        if (idValue == null) {
            // 无 ID 无法查旧值，直接放过
            return pjp.proceed();
        }

        Object oldEntity = selectOldEntity(entity, idValue);
        Object oldValue = null;
        if (oldEntity != null) {
            Field oldField = oldEntity.getClass().getDeclaredField(statusField.getName());
            oldField.setAccessible(true);
            oldValue = oldField.get(oldEntity);
        }

        // 5. 值相同 → 不记录
        if (Objects.equals(oldValue, newValue)) {
            return pjp.proceed();
        }

        // 6. 执行原 update
        Object result = pjp.proceed();

        // 7. 写审计日志（同事务，写入失败会让业务事务回滚）
        try {
            Long entityId = idValue instanceof Long ? (Long) idValue : Long.valueOf(idValue.toString());
            auditLogService.recordStatusChange(
                    annotation.entity(),
                    entityId,
                    annotation.fieldName(),
                    oldValue != null ? String.valueOf(oldValue) : null,
                    newValue != null ? String.valueOf(newValue) : null
            );
        } catch (Exception e) {
            log.error("状态变更审计日志写入失败，业务事务将回滚: entity={}, id={}",
                    annotation.entity(), idValue, e);
            throw e;
        }

        return result;
    }

    private Object getIdValue(Object entity) throws Exception {
        // 优先取 @TableId 标注的字段，退而求其次找 "id" 字段
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
            log.warn("无法获取旧 Entity 数据，跳过审计: {}", e.getMessage());
            return null;
        }
    }
}