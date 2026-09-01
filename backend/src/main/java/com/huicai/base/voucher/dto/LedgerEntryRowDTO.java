package com.huicai.base.voucher.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 明细账分录行投影 — JOIN t_voucher 取凭证号/凭证日期，供明细账展示与滚动余额计算。
 * 日期基于 t_voucher.created_at（当前无凭证日期列，见 SPEC P62/P63 决策）。
 */
@Data
public class LedgerEntryRowDTO {

    /** 凭证 ID */
    private Long voucherId;

    /** 凭证号 */
    private String voucherNo;

    /** 凭证日期（DATE(created_at) 代理） */
    private LocalDate voucherDate;

    /** 科目 ID */
    private Long subjectId;

    /** 摘要 */
    private String summary;

    /** 借方发生额 */
    private BigDecimal debit;

    /** 贷方发生额 */
    private BigDecimal credit;

    /** 分录排序 */
    private Integer sortOrder;
}