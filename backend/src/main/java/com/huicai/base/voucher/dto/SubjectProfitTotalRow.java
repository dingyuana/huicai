package com.huicai.base.voucher.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 损益科目按期间聚合行 — 供结转凭证生成用（P85 性能修复）
 */
@Data
public class SubjectProfitTotalRow {
    /** 科目 ID */
    private Long subjectId;
    /** 科目编码 */
    private String code;
    /** 科目名称 */
    private String name;
    /** 余额方向 (debit/credit) */
    private String direction;
    /** 借方发生额合计 */
    private BigDecimal debitTotal;
    /** 贷方发生额合计 */
    private BigDecimal creditTotal;
}
