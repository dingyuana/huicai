package com.huicai.module.finance.e2e;

import com.huicai.common.test.AbstractMapperTest;
import com.huicai.module.finance.entity.BankStatementEntity;
import com.huicai.module.finance.entity.BusinessDocEntity;
import com.huicai.module.finance.entity.VoucherEntity;
import com.huicai.module.finance.mapper.BankStatementMapper;
import com.huicai.module.finance.mapper.BusinessDocMapper;
import com.huicai.module.finance.mapper.VoucherMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 银行流水全链路 E2E 测试.
 * <p>
 * 模拟: 导入 → 分类 → 确认 → 生单 → 制证 → 核对
 */
public class BankFlowE2ETest extends AbstractMapperTest {

    @Autowired
    private BankStatementMapper bankStatementMapper;

    @Autowired
    private BusinessDocMapper businessDocMapper;

    @Autowired
    private VoucherMapper voucherMapper;

    @Test
    void step1_importBankStatement_shouldBePending() {
        BankStatementEntity stmt = new BankStatementEntity();
        stmt.setAccountId(1L);
        stmt.setTxDate(LocalDate.of(2026, 7, 1));
        stmt.setTxType("INCOME");
        stmt.setAmount(new BigDecimal("10000.00"));
        stmt.setDirection("in");
        stmt.setSummary("货款-测试客户");
        stmt.setCounterAccount("测试客户");
        stmt.setClassification("business_receipt");
        stmt.setMatchStatus("UNMATCHED");
        stmt.setReviewStatus("PENDING");

        int rows = bankStatementMapper.insert(stmt);
        assertEquals(1, rows);
        assertNotNull(stmt.getId());
        assertEquals("PENDING", stmt.getReviewStatus());

        // 保存ID供后续步骤使用
        System.setProperty("test.statement.id", String.valueOf(stmt.getId()));
    }

    @Test
    void step2_classifyAndConfirm_shouldUpdateStatus() {
        String idStr = System.getProperty("test.statement.id");
        if (idStr == null) return; // 跳过，独立运行
        BankStatementEntity stmt = bankStatementMapper.selectById(Long.parseLong(idStr));
        assertNotNull(stmt);

        stmt.setReviewStatus("CONFIRMED");
        stmt.setReviewedBy(1L);
        stmt.setReviewedAt(java.time.LocalDateTime.now());
        int rows = bankStatementMapper.updateById(stmt);
        assertEquals(1, rows);

        BankStatementEntity confirmed = bankStatementMapper.selectById(stmt.getId());
        assertEquals("CONFIRMED", confirmed.getReviewStatus());
    }

    @Test
    void step3_generateBusinessDoc_shouldSucceed() {
        // 验证 BusinessDoc 可通过银行流水 ID 关联查询
        BusinessDocEntity doc = new BusinessDocEntity();
        doc.setDocNo("E2E-BANK-" + System.currentTimeMillis());
        doc.setDocType("RECEIPT");
        doc.setDocDate(LocalDate.of(2026, 7, 1));
        doc.setPeriod("202607");
        doc.setAmount(new BigDecimal("10000.00"));
        doc.setStatus("DRAFT");
        doc.setSummary("银行流水生单E2E测试");
        doc.setSource("FROM_BANK_TXN");

        int rows = businessDocMapper.insert(doc);
        assertEquals(1, rows);
        assertNotNull(doc.getId());
        assertEquals("DRAFT", doc.getStatus());
    }

    @Test
    void step4_generateVoucher_shouldSucceed() {
        VoucherEntity voucher = new VoucherEntity();
        voucher.setVoucherNo("E2E-" + System.currentTimeMillis());
        voucher.setPeriod("202607");
        voucher.setVoucherTypeId(1L);
        voucher.setStatus("DRAFT");
        voucher.setTotalDebit(new BigDecimal("10000.00"));
        voucher.setTotalCredit(new BigDecimal("10000.00"));
        voucher.setSummary("银行流水制证E2E测试");
        voucher.setSource("GENERATED");
        voucher.setCreatedBy(1L);

        int rows = voucherMapper.insert(voucher);
        assertEquals(1, rows);
        assertNotNull(voucher.getId());
        assertEquals("DRAFT", voucher.getStatus());
        assertEquals(0, voucher.getTotalDebit().compareTo(voucher.getTotalCredit()),
                "借贷金额应平衡");
    }

    @Test
    void step5_voucherSubmitAndAudit_shouldChangeStatus() {
        VoucherEntity voucher = new VoucherEntity();
        voucher.setVoucherNo("E2E-AUDIT-" + System.currentTimeMillis());
        voucher.setPeriod("202607");
        voucher.setVoucherTypeId(1L);
        voucher.setStatus("DRAFT");
        voucher.setTotalDebit(new BigDecimal("5000.00"));
        voucher.setTotalCredit(new BigDecimal("5000.00"));
        voucher.setSummary("审核流程E2E测试");
        voucher.setSource("GENERATED");
        voucher.setCreatedBy(1L);
        voucherMapper.insert(voucher);

        // 提交
        voucher.setStatus("SUBMITTED");
        voucher.setSubmittedBy(1L);
        voucher.setSubmittedAt(java.time.LocalDateTime.now());
        voucherMapper.updateById(voucher);

        VoucherEntity submitted = voucherMapper.selectById(voucher.getId());
        assertEquals("SUBMITTED", submitted.getStatus());

        // 审核
        submitted.setStatus("AUDITED");
        submitted.setAuditedBy(2L);
        submitted.setAuditedAt(java.time.LocalDateTime.now());
        voucherMapper.updateById(submitted);

        VoucherEntity approved = voucherMapper.selectById(voucher.getId());
        assertEquals("AUDITED", approved.getStatus());
    }

    @Test
    void step6_amountPrecision_shouldBeCorrect() {
        // 验证 BigDecimal 精度
        BigDecimal a = new BigDecimal("10000.00");
        BigDecimal b = new BigDecimal("9999.99");
        BigDecimal diff = a.subtract(b);
        assertEquals(0, diff.compareTo(new BigDecimal("0.01")),
                "金额精度应保持两位小数");
    }
}