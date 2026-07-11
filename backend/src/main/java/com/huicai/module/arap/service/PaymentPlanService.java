package com.huicai.module.arap.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 付款计划服务（P53 M2）
 */
public interface PaymentPlanService {

    /** 生成付款计划 */
    List<PaymentPlanGroupVO> generatePaymentPlan(String period, Long vendorId);

    // ===== VOs =====

    record PaymentPlanGroupVO(
        Long vendorId,
        String vendorName,
        BigDecimal totalDue,
        int itemCount,
        List<PaymentPlanItemVO> items
    ) {}

    record PaymentPlanItemVO(
        String docNo,
        String docType,
        LocalDate dueDate,
        BigDecimal unsettledAmount,
        int overdueDays,
        LocalDate suggestedPayDate,
        String priority
    ) {}
}