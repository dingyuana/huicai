package com.huicai.common.annotation;

import java.lang.annotation.*;

/**
 * 标记需要审计的状态字段.
 * 被 StatusChangeAspect 拦截，写入 t_audit_log.
 *
 * 用法：
 * <pre>
 * public class OutputInvoiceEntity {
 *     {@code @StatusChangeable(entity = "OUTPUT_INVOICE", fieldName = "status")}
 *     private String status;
 * }
 * </pre>
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface StatusChangeable {

    /** 实体类型，用于 audit_log.module 字段 */
    String entity();

    /** 字段名，用于 audit_log.field_name 字段 */
    String fieldName();
}