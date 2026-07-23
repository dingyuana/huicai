package com.huicai.common.context;

/**
 * 企业上下文持有者 — ThreadLocal 存储当前 enterpriseId
 * <p>
 * 用于多租户数据隔离：请求进入时设置，请求结束时清理。
 * 与 SecurityUtils 配合使用，不替代 SecurityContext。
 * </p>
 */
public class EnterpriseContextHolder {

    private static final ThreadLocal<Long> CONTEXT = new ThreadLocal<>();

    private EnterpriseContextHolder() {}

    public static void set(Long enterpriseId) {
        CONTEXT.set(enterpriseId);
    }

    public static Long get() {
        return CONTEXT.get();
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
