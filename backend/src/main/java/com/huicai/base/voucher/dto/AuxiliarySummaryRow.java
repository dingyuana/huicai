package com.huicai.base.voucher.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 辅助核算维度聚合行 — Mapper 投影（assist_json 按维度字段分组 SUM 借贷）
 */
@Data
public class AuxiliarySummaryRow {

    /** 科目ID */
    private Long subjectId;

    /** 核算维度 ID（assist_json 中提取的文本值） */
    private String dimensionValue;

    /** 借方发生额合计 */
    private BigDecimal debitTotal;

    /** 贷方发生额合计 */
    private BigDecimal creditTotal;
}
