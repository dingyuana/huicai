package com.huicai.module.finance.constant;

/**
 * 凭证类型常量，与 t_voucher_type 表 ID 对应。
 *
 * <pre>
 *   id = 1 → JZ (记账凭证)
 *   id = 2 → SK (收款凭证)
 *   id = 3 → FK (付款凭证)
 *   id = 4 → ZZ (转账凭证)
 * </pre>
 */
public final class VoucherType {
    /** 记账凭证 */
    public static final long JZ = 1L;
    /** 收款凭证 */
    public static final long SK = 2L;
    /** 付款凭证 */
    public static final long FK = 3L;
    /** 转账凭证 */
    public static final long ZZ = 4L;

    private VoucherType() {}
}