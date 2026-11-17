package com.huicai.common.test;

import com.huicai.base.business.entity.ArapSettlementEntity;
import com.huicai.base.business.entity.ArapSettlementEntryEntity;
import com.huicai.base.business.entity.BankStatementEntity;
import com.huicai.base.business.entity.OutputInvoiceEntity;
import com.huicai.base.voucher.entity.VoucherEntity;
import com.huicai.base.voucher.mapper.VoucherMapper;
import com.huicai.base.business.mapper.ArapSettlementEntryMapper;
import com.huicai.base.business.mapper.ArapSettlementMapper;
import com.huicai.base.business.mapper.OutputInvoiceMapper;
import com.huicai.sme.arap.entity.BadDebtProvisionEntity;
import com.huicai.sme.arap.entity.CustomerStatementEntity;
import com.huicai.sme.arap.entity.ExpenseReimbursementEntity;
import com.huicai.sme.arap.mapper.BadDebtProvisionMapper;
import com.huicai.sme.arap.mapper.CustomerStatementMapper;
import com.huicai.sme.arap.mapper.ExpenseReimbursementMapper;
import com.huicai.sme.tax.entity.TaxDeclarationEntity;
import com.huicai.sme.tax.mapper.TaxDeclarationMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * SPEC-P153 幽灵字段转正的<b>落库行为</b>守卫（V194）。
 *
 * <p><b>要守的不变量</b>：本批从 {@code @TableField(exist=false)} 转正的每一个字段，
 * 必须「Mapper insert/updateById 写入 → selectById 读回同值」。
 *
 * <p><b>为什么每条都要显式断言</b>：幽灵字段的症状是「写了不报错、读回恒 null」，
 * 编译与常规业务测试都拦不住（AGENTS §4.5「名实不符」形态）。只有
 * 「写入 + 回读 + 逐字段比对」能同时证明 ①列真实存在 ②映射正确 ③
 * {@code exist=false} 已被移除。任何一条被回退（重新加回注解或迁移漏列），
 * 对应断言立刻转红。
 *
 * <p>真库 PostgreSQL + Flyway（V194 已应用）。范式对齐
 * {@code EntityDbSchemaIntegrationTest}（SPEC-P152 回读断言）。
 */
@DisplayName("SPEC-P153：V194 转正字段 写→回读 逐字段守卫")
class GhostFieldsPromotionRealDBTest extends AbstractMapperTest {

    @Autowired private VoucherMapper voucherMapper;
    @Autowired private ExpenseReimbursementMapper expenseMapper;
    @Autowired private com.huicai.base.business.mapper.BankStatementMapper bankStatementMapper;
    @Autowired private ArapSettlementMapper settlementMapper;
    @Autowired private ArapSettlementEntryMapper settlementEntryMapper;
    @Autowired private OutputInvoiceMapper outputInvoiceMapper;
    @Autowired private CustomerStatementMapper customerStatementMapper;
    @Autowired private BadDebtProvisionMapper badDebtMapper;
    @Autowired private TaxDeclarationMapper taxDeclarationMapper;

    // ══════════════ 1) t_voucher 溯源三元组 + 模板 + 驳回原因 ══════════════

    @Test
    @DisplayName("Voucher：sourceDoc* / templateId / rejectedReason 写后读回同值")
    void voucher_sourceTrace_roundTrip() {
        VoucherEntity v = new VoucherEntity();
        v.setVoucherNo("P153-VCH-001");
        v.setPeriod("202611");
        v.setVoucherTypeId(1L);
        v.setStatus("DRAFT");
        v.setSource("MANUAL");
        v.setSummary("P153 溯源守卫");
        v.setTotalDebit(new BigDecimal("100.00"));
        v.setTotalCredit(new BigDecimal("100.00"));
        // V194 转正五字段
        v.setSourceDocId(4242L);
        v.setSourceDocNo("BD-202611-042");
        v.setSourceDocType("BUSINESS_DOC");
        v.setTemplateId(77L);
        v.setRejectedReason("科目用错，退回重制");
        voucherMapper.insert(v);

        VoucherEntity r = voucherMapper.selectById(v.getId());
        assertNotNull(r);
        assertEquals(4242L, r.getSourceDocId(), "source_doc_id 落库/映射失效（疑似 exist=false 回退或迁移缺列）");
        assertEquals("BD-202611-042", r.getSourceDocNo());
        assertEquals("BUSINESS_DOC", r.getSourceDocType());
        assertEquals(77L, r.getTemplateId());
        assertEquals("科目用错，退回重制", r.getRejectedReason());
    }

    @Test
    @DisplayName("Voucher：reverseReason 保持非持久化（刻意不建列，写后读回必为 null）")
    void voucher_reverseReason_staysTransient() {
        VoucherEntity v = new VoucherEntity();
        v.setVoucherNo("P153-VCH-002");
        v.setPeriod("202611");
        v.setVoucherTypeId(1L);
        v.setStatus("DRAFT");
        v.setSource("MANUAL");
        v.setTotalDebit(BigDecimal.ONE);
        v.setTotalCredit(BigDecimal.ONE);
        v.setReverseReason("这条永远不会也不该落库");
        voucherMapper.insert(v);

        VoucherEntity r = voucherMapper.selectById(v.getId());
        assertEquals(null, r.getReverseReason(),
                "reverseReason 应恒 null：SPEC-P153 裁定全仓无写方刻意不建列，若读回非 null 说明有人加了列却没接写方");
    }

    // ══════════════ 2) t_expense_reimbursement 审批链 ══════════════

    @Test
    @DisplayName("Expense：bankStmtId/attachmentIds/submittedAt/approvedAt/approvedBy/rejectReason 写后读回")
    void expense_approvalChain_roundTrip() {
        ExpenseReimbursementEntity e = new ExpenseReimbursementEntity();
        e.setReimbNo("P153-REIMB-001");
        e.setApplicantId(1L);
        e.setExpenseType("TRAVEL");        // reimb_type
        e.setAmount(new BigDecimal("500.00")); // total_amount
        e.setStatus("DRAFT");
        // V194 转正六字段
        LocalDateTime now = LocalDateTime.of(2026, 11, 5, 10, 0, 0);
        e.setBankStmtId(9001L);
        e.setAttachmentIds("11,22,33");
        e.setSubmittedAt(now);
        e.setApprovedAt(now.plusHours(1));
        e.setApprovedBy("王会计");
        e.setRejectReason("发票缺失");
        expenseMapper.insert(e);

        ExpenseReimbursementEntity r = expenseMapper.selectById(e.getId());
        assertNotNull(r);
        assertEquals(9001L, r.getBankStmtId(), "bank_stmt_id 是流水自动报账幂等键，回读 null = 防重查询再次恒假");
        assertEquals("11,22,33", r.getAttachmentIds());
        assertEquals(now, r.getSubmittedAt());
        assertEquals(now.plusHours(1), r.getApprovedAt());
        assertEquals("王会计", r.getApprovedBy());
        assertEquals("发票缺失", r.getRejectReason());
    }

    @Test
    @DisplayName("Expense：employeeId 保持非持久化（入参别名，真值在 applicant_id）")
    void expense_employeeId_staysTransient() {
        ExpenseReimbursementEntity e = new ExpenseReimbursementEntity();
        e.setReimbNo("P153-REIMB-002");
        e.setApplicantId(5L);
        e.setEmployeeId(99L);
        e.setExpenseType("OFFICE");
        e.setAmount(BigDecimal.TEN);
        e.setStatus("DRAFT");
        expenseMapper.insert(e);

        ExpenseReimbursementEntity r = expenseMapper.selectById(e.getId());
        assertEquals(5L, r.getApplicantId());
        assertEquals(null, r.getEmployeeId(),
                "employeeId 是 applicantId 的入参别名，建列即双真值源 —— 应保持非持久化");
    }

    // ══════════════ 3) t_bank_statement 审核留痕 + 制证回填 + 审计覆盖删除 ══════════════

    @Test
    @DisplayName("BankStatement：reviewed*/generated*/purpose/remark/ruleId/batchId + 审计列 写后读回")
    void bankStatement_reviewTrace_roundTrip() {
        BankStatementEntity s = new BankStatementEntity();
        s.setAccountId(ensureBankAccount(DEFAULT_ENTERPRISE_ID));
        s.setTxDate(LocalDate.of(2026, 11, 5));
        s.setTxType("INCOME");
        s.setAmount(new BigDecimal("1000.00"));
        s.setSummary("P153 守卫");
        s.setReviewStatus("CONFIRMED");
        // V194 转正字段
        LocalDateTime now = LocalDateTime.of(2026, 11, 5, 12, 0, 0);
        s.setPurpose("货款");
        s.setTransactionRemark("11月上半月");
        s.setRuleId(3L);
        s.setReviewedBy(9L);
        s.setReviewedAt(now);
        s.setGeneratedDocId(501L);
        s.setGeneratedVoucherId(601L);
        s.setGeneratedVoucherNo("JZ-202611-0601");
        s.setGeneratedAt(now.plusMinutes(5));
        s.setBatchId("BATCH-P153-1");
        s.setUpdatedBy(9L);
        bankStatementMapper.insert(s);

        BankStatementEntity r = bankStatementMapper.selectById(s.getId());
        assertNotNull(r);
        assertEquals("货款", r.getPurpose());
        assertEquals("11月上半月", r.getTransactionRemark());
        assertEquals(3L, r.getRuleId());
        assertEquals(9L, r.getReviewedBy(), "reviewed_by 恒 null = 复核人溯源再次丢失");
        assertEquals(now, r.getReviewedAt());
        assertEquals(501L, r.getGeneratedDocId());
        assertEquals(601L, r.getGeneratedVoucherId());
        assertEquals("JZ-202611-0601", r.getGeneratedVoucherNo());
        assertEquals(now.plusMinutes(5), r.getGeneratedAt());
        assertEquals("BATCH-P153-1", r.getBatchId());
        assertEquals(9L, r.getUpdatedBy(),
                "updated_by 是 BaseEntity 真实列，实体 exist=false 覆盖必须保持删除状态");
        assertNotNull(r.getCreatedAt(), "created_at V194 补列 + BaseEntity fill=INSERT 应自动回填");
        // AI 三字段按裁定保持幽灵（AI 项目除外）
        assertEquals(null, r.getAiBusinessScene());
    }

    // ══════════════ 4) t_arap_settlement(_entry) 凭证号回写 + 红冲链 + 余额快照 ══════════════

    @Test
    @DisplayName("Settlement：voucherNo/reversedFromSettlementId + entry 余额快照 写后读回")
    void settlement_voucherNoAndBalance_roundTrip() {
        ArapSettlementEntity st = new ArapSettlementEntity();
        st.setSettlementNo("P153-JS-001");
        st.setSettlementType("RECEIVE");
        st.setSettlementDate(LocalDate.of(2026, 11, 5));
        st.setPeriod("202611");
        st.setPartyId(ensureCustomer(DEFAULT_ENTERPRISE_ID));
        st.setPartyType("CUSTOMER");
        st.setTotalAmount(new BigDecimal("800.00"));
        st.setStatus("DRAFT");
        st.setVoucherNo("JZ-202611-0777");
        settlementMapper.insert(st);

        ArapSettlementEntryEntity entry = new ArapSettlementEntryEntity();
        entry.setSettlementId(st.getId());
        entry.setSettledAmount(new BigDecimal("800.00"));
        entry.setBeforeBalance(new BigDecimal("1800.00"));
        entry.setAfterBalance(new BigDecimal("1000.00"));
        settlementEntryMapper.insert(entry);

        ArapSettlementEntity rs = settlementMapper.selectById(st.getId());
        assertEquals("JZ-202611-0777", rs.getVoucherNo());
        assertEquals(null, rs.getReversedFromSettlementId());

        ArapSettlementEntryEntity re = settlementEntryMapper.selectById(entry.getId());
        assertEquals(new BigDecimal("1800.00"), re.getBeforeBalance(),
                "余额快照恒 null ⇒ 反核销 copy 与对账审计依据断链（ArapSettlementServiceImpl L510 读方）");
        assertEquals(new BigDecimal("1000.00"), re.getAfterBalance());

        // 红冲链
        st.setReversedFromSettlementId(st.getId());
        settlementMapper.updateById(st);
        assertEquals(st.getId(), settlementMapper.selectById(st.getId()).getReversedFromSettlementId());
    }

    // ══════════════ 5) t_output_invoice 审核留痕 + updated_by ══════════════

    @Test
    @DisplayName("OutputInvoice：auditedBy/auditedAt/updatedBy 写后读回（confirm 链）")
    void outputInvoice_auditTrail_roundTrip() {
        OutputInvoiceEntity inv = new OutputInvoiceEntity();
        inv.setInvoiceNo("P153-INV-001");
        inv.setInvoiceDate(LocalDate.of(2026, 11, 5));
        inv.setPeriod("202611");
        inv.setCustomerId(ensureCustomer(DEFAULT_ENTERPRISE_ID));
        inv.setAmount(new BigDecimal("100.00"));
        inv.setTaxRate(new BigDecimal("0.13"));
        inv.setTaxAmount(new BigDecimal("13.00"));
        inv.setTotalAmount(new BigDecimal("113.00"));
        inv.setInvoiceType("SPECIAL");
        inv.setStatus("CONFIRMED");
        LocalDateTime now = LocalDateTime.of(2026, 11, 5, 14, 30, 0);
        inv.setAuditedBy(8L);
        inv.setAuditedAt(now);
        inv.setUpdatedBy(8L);
        outputInvoiceMapper.insert(inv);

        OutputInvoiceEntity r = outputInvoiceMapper.selectById(inv.getId());
        assertNotNull(r);
        assertEquals(8L, r.getAuditedBy(), "销项 confirm 的审核人必须落库（进项侧同构守卫）");
        assertEquals(now, r.getAuditedAt());
        assertEquals(8L, r.getUpdatedBy());
        // 查询回填派生字段保持非持久化（建列=双真值源）
        assertEquals(null, r.getDocNo());
        assertEquals(null, r.getVoucherNo());
        assertEquals(null, r.getOriginalInvoiceId());
    }

    // ══════════════ 6) t_customer_statement 确认时间 ══════════════

    @Test
    @DisplayName("CustomerStatement：confirmedAt 写后读回")
    void customerStatement_confirmedAt_roundTrip() {
        CustomerStatementEntity cs = new CustomerStatementEntity();
        cs.setStatementNo("P153-STMT-001");
        cs.setCustomerId(ensureCustomer(DEFAULT_ENTERPRISE_ID));
        cs.setPeriod("202610");
        cs.setPeriodStart(LocalDate.of(2026, 10, 1));
        cs.setPeriodEnd(LocalDate.of(2026, 10, 31));
        cs.setStatus("CONFIRMED");
        LocalDateTime now = LocalDateTime.of(2026, 11, 1, 9, 0, 0);
        cs.setConfirmedAt(now);
        customerStatementMapper.insert(cs);

        CustomerStatementEntity r = customerStatementMapper.selectById(cs.getId());
        assertEquals(now, r.getConfirmedAt(), "setConfirmedAt 此前不落库（confirm() 留痕恒空）");
    }

    // ══════════════ 7) t_bad_debt_provision 计提快照 ══════════════

    @Test
    @DisplayName("BadDebt：voucherNo/expected/existing/adjustment 快照 写后读回；schemeId 保持非持久化")
    void badDebt_snapshot_roundTrip() {
        BadDebtProvisionEntity p = new BadDebtProvisionEntity();
        p.setPeriod("202610");
        p.setMethod("AGING_RATIO");
        p.setProvisionDate(LocalDate.of(2026, 10, 31));
        p.setTotalAmount(new BigDecimal("120.00"));
        p.setStatus("DRAFT");
        p.setVoucherNo("JZ-202610-0088");
        p.setExpectedBalance(new BigDecimal("320.00"));
        p.setExistingBalance(new BigDecimal("200.00"));
        p.setAdjustmentAmount(new BigDecimal("120.00"));
        p.setAdjustmentType("PROVISION");
        p.setSchemeId(5L); // 扫描器误配位：真写方是 SchemeItemEntity，此处应保持不落库
        p.setUpdatedBy(6L);
        badDebtMapper.insert(p);

        BadDebtProvisionEntity r = badDebtMapper.selectById(p.getId());
        assertNotNull(r);
        assertEquals("JZ-202610-0088", r.getVoucherNo());
        assertEquals(new BigDecimal("320.00"), r.getExpectedBalance());
        assertEquals(new BigDecimal("200.00"), r.getExistingBalance());
        assertEquals(new BigDecimal("120.00"), r.getAdjustmentAmount());
        assertEquals("PROVISION", r.getAdjustmentType());
        assertEquals(6L, r.getUpdatedBy());
        assertEquals(null, r.getSchemeId(),
                "schemeId 刻意不建列（真写方在 SchemeItemEntity），读回非 null 即说明加了凭空空列");
    }

    // ══════════════ 8) t_tax_declaration updated_by 覆盖删除 ══════════════

    @Test
    @DisplayName("TaxDeclaration：updatedBy 写后读回（实体覆盖删除生效）")
    void taxDeclaration_updatedBy_roundTrip() {
        TaxDeclarationEntity d = new TaxDeclarationEntity();
        d.setDeclarationNo("P153-DECL-001");
        d.setPeriod("202610");
        d.setTaxType("VAT");
        d.setDeclaredDate(LocalDate.of(2026, 11, 10));
        d.setPayableAmount(new BigDecimal("113.00"));
        d.setStatus("DRAFT");
        d.setUpdatedBy(7L);
        taxDeclarationMapper.insert(d);

        TaxDeclarationEntity r = taxDeclarationMapper.selectById(d.getId());
        assertEquals(7L, r.getUpdatedBy());
    }
}
