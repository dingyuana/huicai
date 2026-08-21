package com.huicai.sme.tax.constant;

/**
 * 红冲原因枚举 (P36.1).
 * <pre>
 * INVOICE_ERROR: 开票有误（错误金额/信息更正）
 * RETURN:        退货（冲销原购入成本/费用）
 * DISCOUNT:      折让（商业折扣冲减收入/成本）
 * OTHER:         其他
 * </pre>
 */
public final class RedFlushReason {

    public static final String INVOICE_ERROR = "INVOICE_ERROR";
    public static final String RETURN = "RETURN";
    public static final String DISCOUNT = "DISCOUNT";
    public static final String OTHER = "OTHER";

    private RedFlushReason() {
    }

    /**
     * 校验红冲原因枚举合法性，非法返回 false。
     */
    public static boolean isValid(String reason) {
        return INVOICE_ERROR.equals(reason)
                || RETURN.equals(reason)
                || DISCOUNT.equals(reason)
                || OTHER.equals(reason);
    }
}