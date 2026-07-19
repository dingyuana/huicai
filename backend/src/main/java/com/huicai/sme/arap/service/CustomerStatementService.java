package com.huicai.sme.arap.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.sme.arap.entity.CustomerStatementEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface CustomerStatementService {

    // ==================== 生成对账单 ====================

    /**
     * 生成对账单（单客户或全部）
     */
    List<CustomerStatementEntity> generateStatements(List<Long> customerIds, String period);

    // ==================== 查询 ====================

    CustomerStatementEntity getById(Long id);

    IPage<CustomerStatementEntity> pageQuery(String status, Integer current, Integer size);

    // ==================== 状态流转 ====================

    void send(Long id);

    void confirm(Long id);

    void dispute(Long id, DisputeRequest request);

    // ==================== 未达账项 ====================

    IPage<OutstandingItemVO> pageOutstandingItems(Long statementId, Long customerId, String status, Integer current, Integer size);

    void resolveOutstandingItem(Long id);

    void cancelOutstandingItem(Long id);

    // ==================== 差异 ====================

    IPage<DisputeVO> pageDisputes(Long statementId, Long customerId, String disputeType, Integer current, Integer size);

    void resolveDispute(Long id, String resolution);

    // ==================== 供P43调用 ====================

    /**
     * 查询有未解决差异的客户ID列表
     */
    List<Long> getCustomerIdsWithOpenDisputes();

    // ==================== VO Records ====================

    record CustomerStatementVO(
            Long id, Long customerId, String customerName, String period,
            LocalDate statementDate,
            BigDecimal totalOriginal, BigDecimal totalSettled, BigDecimal totalUnsettled,
            String status,
            java.time.LocalDateTime sentAt, java.time.LocalDateTime confirmedAt,
            java.time.LocalDateTime createdAt, java.time.LocalDateTime updatedAt,
            Long createdBy,
            List<StatementDetail> details,
            List<OutstandingItemVO> outstandingItems,
            List<DisputeVO> disputes
    ) {}

    record StatementDetail(
            String docType, String docNo,
            BigDecimal originalAmount, BigDecimal unsettledAmount,
            LocalDate dueDate, int agingDays
    ) {}

    record OutstandingItemVO(
            Long id, Long customerId, Long statementId,
            String outstandingType, BigDecimal amount,
            String description, String evidence,
            String status, java.time.LocalDateTime resolvedAt,
            java.time.LocalDateTime createdAt
    ) {}

    record DisputeVO(
            Long id, Long statementId, Long customerId,
            String docNo, String disputeType,
            BigDecimal expectedAmount, BigDecimal actualAmount, BigDecimal diffAmount,
            String reason, String resolution,
            Long resolvedBy, java.time.LocalDateTime resolvedAt,
            java.time.LocalDateTime createdAt
    ) {}

    record DisputeRequest(
            String docNo, String disputeType,
            BigDecimal expectedAmount, BigDecimal actualAmount,
            String reason
    ) {}
}