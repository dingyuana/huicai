package com.huicai.base.voucher.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 结账序列中单张凭证的展示视图（Drawer 卡片数据源）。
 *
 * <p>P84 第 2 步 Drawer 凭证卡片需要 id/no/类型名/借贷合计/分录数/状态/时间，
 * {@link VoucherVO} 的 {@code voucherTypeName} 在列表查询时才填充，此处单独
 * 收敛为只含卡片所需字段的轻量 VO，避免为拿一个类型名而依赖完整 VoucherVO 装配。
 *
 * @param voucherId      凭证 id
 * @param voucherNo      凭证号（如 DEPR-202609 / CLOSE-202609）
 * @param voucherTypeName 凭证类型名
 * @param totalDebit     借方合计
 * @param totalCredit    贷方合计
 * @param entryCount     分录行数
 * @param status         凭证状态 DRAFT/AUDITED/POSTED...
 * @param createdAt      生成时间
 * @author Hermes
 */
public record SequenceVoucherVO(
        Long voucherId,
        String voucherNo,
        String voucherTypeName,
        BigDecimal totalDebit,
        BigDecimal totalCredit,
        Integer entryCount,
        String status,
        LocalDateTime createdAt
) {
}
