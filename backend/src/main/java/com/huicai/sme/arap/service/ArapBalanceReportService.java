package com.huicai.sme.arap.service;

import java.math.BigDecimal;
import java.util.List;

/**
 * 应收应付余额汇总服务（P75）
 * <p>
 * 输出：期初未核销 / 本期应收(付) / 本期实收(付) / 期末余额，一行一客商。
 * 口径（对齐 SPEC P75 V1.1）：
 * <ul>
 *   <li>单据侧：docType ∈ (INVOICE_OUT, OTHER_RECEIVABLE) / (INVOICE_IN, OTHER_PAYABLE)，period ≤ 查询期间，
 *       status ∈ (APPROVED, VOUCHERED, PARTIALLY_RECONCILED, FULLY_RECONCILED)（BusinessDocStatus 有效敞口四值）</li>
 *   <li>核销侧：period = 查询期间，partyType = CUSTOMER(应收)/VENDOR(应付)，status ∈ (CONFIRMED, VOUCHERED)，
 *       totalAmount &gt; 0（排除 -H 红字对冲单）</li>
 *   <li>期初推导：openingUnsettled = closingUnsettled − currentAmount + currentSettled（会计恒等式）</li>
 * </ul>
 * 数据权限由 {@code EnterpriseDataPermissionInterceptor} 自动注入 enterprise_id，服务不做手工过滤。
 */
public interface ArapBalanceReportService {

    /**
     * 查询应收应付余额汇总
     *
     * @param period     YYYYMM 会计期间（必填，6 位数字）
     * @param customerId 可选，按客户过滤应收侧
     * @param vendorId   可选，按供应商过滤应付侧
     */
    ArapBalanceSummaryVO getBalanceSummary(String period, Long customerId, Long vendorId);

    /** 一行一客商的余额行 */
    record ArapBalancePartyVO(
        Long partyId, String partyName,
        BigDecimal openingUnsettled, BigDecimal currentAmount,
        BigDecimal currentSettled, BigDecimal closingUnsettled
    ) {}

    /** 余额汇总 VO */
    record ArapBalanceSummaryVO(
        String period,
        boolean consistent,
        BigDecimal receivableTotal,
        BigDecimal payableTotal,
        List<ArapBalancePartyVO> receivables,
        List<ArapBalancePartyVO> payables
    ) {}
}