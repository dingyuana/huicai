package com.huicai.sme.arap.service;

import java.math.BigDecimal;
import java.util.List;

/**
 * 预收预付余额汇总服务（P78）
 * <p>
 * 输出：期初未结清 / 本期新增 / 本期抵扣 / 本期冲销 / 期末未结清，一行一往来单位。
 * P75 管借方应收应付（业务单据 + 普通核销 JS/FS），P78 管贷方预收预付（t_prepayment + 预收预付核销 YS/YF）。
 * <p>
 * 口径（对齐 SPEC P78 V1.0，代码实证 2026-09-17）：
 * <ul>
 *   <li>数据源：t_prepayment（status ∈ CONFIRMED/APPLIED/REVERSED 非草稿）；
 *       预收按 customerId 分组（PRE_RECEIPT），预付按 vendorId 分组（PRE_PAYMENT），两侧互不混入</li>
 *   <li>期间归属：PrepaymentEntity.period 在 create() 未赋值，有效期间 = period（非空），否则回退 txDate 的 yyyyMM</li>
 *   <li>本期新增 currentCreated：status ∈ (CONFIRMED, APPLIED, REVERSED) 且 有效期间 = 查询期间，聚合 amount</li>
 *   <li>本期抵扣 currentApplied：t_arap_settlement 按 settlementNo 前缀隔离——YS-（预收冲应收，CUSTOMER）/
 *       YF-（预付冲应付，VENDOR）；status ∈ (CONFIRMED, VOUCHERED)、period = 查询期间、totalAmount &gt; 0。
 *       前缀隔离是关键：P75 普通核销单前缀 JS/FS，若不做前缀隔离会双重计入</li>
 *   <li>本期冲销 currentReversed：status = REVERSED 且 有效期间 = 查询期间，聚合 amount</li>
 *   <li>期末未结清 closingUnsettled：status ∈ (CONFIRMED, APPLIED) 且 有效期间 ≤ 查询期间，求和 unsettledAmount</li>
 *   <li>期初推导：openingUnsettled = closingUnsettled − currentCreated + currentApplied + currentReversed</li>
 * </ul>
 * 数据权限由 {@code EnterpriseDataPermissionInterceptor} 自动注入 enterprise_id（t_prepayment /
 * t_arap_settlement 均非共享表），服务不做手工过滤。
 */
public interface PrepaymentBalanceReportService {

    /**
     * 查询预收预付余额汇总
     *
     * @param period     YYYYMM 会计期间（必填，6 位数字）
     * @param partyType  可选，PRE_RECEIPT（预收=客户侧）/ PRE_PAYMENT（预付=供应商侧）；null = 两侧都出
     * @param partyId    可选，按往来单位过滤（PRE_RECEIPT 对应 customerId，PRE_PAYMENT 对应 vendorId）
     */
    PrepaymentBalanceSummaryVO getBalanceSummary(String period, String partyType, Long partyId);

    /** 一行一往来单位的余额行 */
    record PrepaymentBalancePartyVO(
        Long partyId, String partyName,
        BigDecimal openingUnsettled, BigDecimal currentCreated,
        BigDecimal currentApplied, BigDecimal currentReversed,
        BigDecimal closingUnsettled
    ) {}

    /** 余额汇总 VO（一侧被显式指定时，另一侧列表为空） */
    record PrepaymentBalanceSummaryVO(
        String period,
        String partyType,
        boolean consistent,
        BigDecimal preReceiptTotal,
        BigDecimal prePaymentTotal,
        List<PrepaymentBalancePartyVO> preReceipts,
        List<PrepaymentBalancePartyVO> prePayments
    ) {}
}
