package com.huicai.sme.asset.dto;

import java.math.BigDecimal;

/**
 * DEPR 折旧制证结果（P85-C）.
 *
 * @param voucherId    凭证ID
 * @param voucherNo    凭证号（形如 DEPR-202608）
 * @param totalDebit   借方合计
 * @param totalCredit  贷方合计
 * @param detailCount  纳入制证的折旧明细行数（t_asset_depreciation 条数）
 * @param status       凭证状态（固定 DRAFT，需人工审核）
 */
public record DepreciationVoucherResult(
        Long voucherId,
        String voucherNo,
        BigDecimal totalDebit,
        BigDecimal totalCredit,
        int detailCount,
        String status) {
}
