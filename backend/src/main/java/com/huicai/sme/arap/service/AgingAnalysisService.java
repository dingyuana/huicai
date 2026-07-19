package com.huicai.sme.arap.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 账龄分析与逾期预警服务（P51）
 */
public interface AgingAnalysisService {

    /** 账龄汇总（按区间分布） */
    AgingSummaryVO getAgingSummary(String period, Long customerId);

    /** 按客户维度的账龄分析 */
    List<AgingByCustomerVO> getAgingByCustomer(String period);

    // ===== 应付侧（P53 对称扩展） =====

    /** 应付账龄汇总（按区间分布） */
    AgingSummaryVO getPayableAgingSummary(String period, Long vendorId);

    /** 按供应商维度的应付账龄 */
    List<AgingByVendorVO> getPayableAgingByVendor(String period);

    /** 到期应付表（已到期未付明细） */
    DuePayablesVO getDuePayables(LocalDate reportDate, Long vendorId);

    // ===== VOs =====

    record AgingByVendorVO(
        Long vendorId, String vendorName,
        BigDecimal totalUnsettled, Map<String, BigDecimal> buckets
    ) {}

    record DuePayablesVO(
        LocalDate reportDate, BigDecimal totalDue, int totalDueCount,
        List<DuePayableItem> items
    ) {}

    record DuePayableItem(
        String vendorName, String docNo, LocalDate dueDate,
        BigDecimal originalAmount, BigDecimal unsettledAmount,
        int overdueDays, String agingBucket
    ) {}

    /** 到期债权表（已到期未核销明细） */
    DueReceivablesVO getDueReceivables(LocalDate reportDate, Long customerId);

    /** 手动触发逾期预警扫描 */
    int generateAlerts(String period);

    /** 查询逾期预警列表 */
    List<AgingAlertVO> getAlerts(String alertLevel, String status, Long customerId);

    /** 忽略预警 */
    void dismissAlert(Long id);

    /** 标记预警已解决 */
    void resolveAlert(Long id);

    // ====== VO ======

    record AgingSummaryVO(
        LocalDate reportDate,
        String period,
        AgingSummary summary,
        List<AgingBucket> agingBuckets
    ) {}

    record AgingSummary(BigDecimal totalUnsettled, BigDecimal totalOverdue, String overdueRate) {}
    record AgingBucket(String label, BigDecimal amount, int count, String percentage) {}

    record AgingByCustomerVO(
        Long customerId, String customerName,
        BigDecimal totalUnsettled, Map<String, BigDecimal> buckets
    ) {}

    record DueReceivablesVO(
        LocalDate reportDate, BigDecimal totalDue, int totalDueCount,
        List<DueItem> items
    ) {}

    record DueItem(
        String customerName, String docNo, LocalDate docDate, LocalDate dueDate,
        BigDecimal originalAmount, BigDecimal unsettledAmount,
        int overdueDays, String agingBucket,
        String contactPerson, String contactPhone
    ) {}

    record AgingAlertVO(
        Long id, Long customerId, String customerName,
        String docNo, BigDecimal unsettledAmount, LocalDate dueDate,
        int overdueDays, String alertLevel, String status,
        LocalDateTime notifiedAt, LocalDateTime dismissedAt, LocalDateTime createdAt
    ) {}
}
