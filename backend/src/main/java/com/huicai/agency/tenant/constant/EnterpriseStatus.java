package com.huicai.agency.tenant.constant;

/**
 * 企业状态枚举
 */
public enum EnterpriseStatus {
    PENDING("PENDING", "待激活"),
    ACTIVE("ACTIVE", "已激活"),
    SUSPENDED("SUSPENDED", "已暂停"),
    TERMINATED("TERMINATED", "已终止");

    private final String code;
    private final String label;

    EnterpriseStatus(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() { return code; }
    public String getLabel() { return label; }

    public static boolean isValidTransition(String from, String to) {
        return switch (from) {
            case "PENDING" -> "ACTIVE".equals(to);
            case "ACTIVE" -> "SUSPENDED".equals(to);
            case "SUSPENDED" -> "ACTIVE".equals(to) || "TERMINATED".equals(to);
            default -> false;
        };
    }
}
