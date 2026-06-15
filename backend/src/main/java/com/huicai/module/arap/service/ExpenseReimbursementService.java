package com.huicai.module.arap.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.module.arap.entity.ExpenseReimbursementEntity;

import java.math.BigDecimal;
import java.util.List;

public interface ExpenseReimbursementService {
    IPage<ExpenseReimbursementEntity> pageQuery(Long employeeId, String status, Integer current, Integer size);
    List<ExpenseReimbursementEntity> listAll();

    ExpenseReimbursementEntity getById(Long id);

    /** 创建草稿 (DRAFT) */
    ExpenseReimbursementEntity createDraft(ExpenseReimbursementEntity entity);

    /** 修改草稿 (DRAFT only) */
    ExpenseReimbursementEntity updateDraft(ExpenseReimbursementEntity entity);

    /** 提交 (DRAFT → SUBMITTED) */
    ExpenseReimbursementEntity submit(Long id);

    /** 审核通过 (SUBMITTED → APPROVED) */
    ExpenseReimbursementEntity approve(Long id, String approver);

    /** 驳回 (SUBMITTED → REJECTED) */
    ExpenseReimbursementEntity reject(Long id, String approver, String reason);

    /** 生成凭证 (APPROVED → VOUCHERED) */
    ExpenseReimbursementEntity generateVoucher(Long id, Long voucherId);

    /** 按银行流水ID查 (P11-3 自动建单防重用) */
    ExpenseReimbursementEntity findByBankStmtId(Long bankStmtId);

    /** 按银行流水ID自动创建报销单 (P11-3) — 默认 expenseType=OTHER */
    ExpenseReimbursementEntity autoCreateForBankStmt(Long bankStmtId, Long employeeId, BigDecimal amount, String summary);
}
