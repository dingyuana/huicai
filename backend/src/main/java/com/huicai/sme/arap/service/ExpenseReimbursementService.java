package com.huicai.sme.arap.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.sme.arap.dto.ExpenseReimbursementVO;
import com.huicai.sme.arap.entity.ExpenseReimbursementEntity;

import java.math.BigDecimal;
import java.util.List;

public interface ExpenseReimbursementService {
    IPage<ExpenseReimbursementVO> pageQuery(Long employeeId, String status, Integer current, Integer size);
    List<ExpenseReimbursementVO> listAll();

    ExpenseReimbursementVO getById(Long id);

    /** 创建草稿 (DRAFT) */
    ExpenseReimbursementVO createDraft(ExpenseReimbursementEntity entity);

    /** 修改草稿 (DRAFT only) */
    ExpenseReimbursementVO updateDraft(ExpenseReimbursementEntity entity);

    /** 提交 (DRAFT → SUBMITTED) */
    ExpenseReimbursementVO submit(Long id);

    /** 审核通过 (SUBMITTED → APPROVED) */
    ExpenseReimbursementVO approve(Long id, String approver);

    /** 驳回 (SUBMITTED → REJECTED) */
    ExpenseReimbursementVO reject(Long id, String approver, String reason);

    /** 生成凭证 (APPROVED → VOUCHERED) — 仅记录 voucherId, 不创建真实凭证 */
    ExpenseReimbursementVO generateVoucher(Long id, Long voucherId);

    /** P11-4: 报销单审批后自动生成凭证 (APPROVED → VOUCHERED), 创建真实凭证 + 2 条分录 */
    ExpenseReimbursementVO generateVoucherForApproved(Long id);

    /** 按银行流水ID查 (P11-3 自动建单防重用) */
    ExpenseReimbursementEntity findByBankStmtId(Long bankStmtId);

    /** 按银行流水ID自动创建报销单 (P11-3) — 默认 expenseType=OTHER, 返回详情VO */
    ExpenseReimbursementVO autoCreateForBankStmt(Long bankStmtId, Long employeeId, BigDecimal amount, String summary);
}
