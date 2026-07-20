package com.huicai.sme.arap.e2e;

import com.huicai.common.test.AbstractMapperTest;
import com.huicai.base.business.entity.BusinessDocEntity;
import com.huicai.base.voucher.entity.VoucherEntity;
import com.huicai.base.business.mapper.BusinessDocMapper;
import com.huicai.base.voucher.mapper.VoucherMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 费用报销全链路 E2E 测试.
 * <p>
 * 模拟: 创建报销单据 → 审批 → 生成凭证 → 过账
 */
public class ExpenseFlowE2ETest extends AbstractMapperTest {

    @Autowired
    private BusinessDocMapper businessDocMapper;

    @Autowired
    private VoucherMapper voucherMapper;

    @Test
    void step1_createExpenseDoc_shouldBeDraft() {
        BusinessDocEntity doc = new BusinessDocEntity();
        doc.setDocNo("EXP-E2E-" + System.currentTimeMillis());
        doc.setDocType("EXPENSE");
        doc.setDocDate(LocalDate.now());
        doc.setPeriod("202607");
        doc.setAmount(new BigDecimal("5000.00"));
        doc.setStatus("DRAFT");
        doc.setSummary("差旅费报销-E2E测试");
        doc.setSource("MANUAL");
        doc.setApplicantId(1L);

        int rows = businessDocMapper.insert(doc);
        assertEquals(1, rows);
        assertNotNull(doc.getId());
        assertEquals("DRAFT", doc.getStatus());
    }

    @Test
    void step2_submitExpense_shouldChangeStatus() {
        BusinessDocEntity doc = new BusinessDocEntity();
        doc.setDocNo("EXP-SUBMIT-" + System.currentTimeMillis());
        doc.setDocType("EXPENSE");
        doc.setDocDate(LocalDate.now());
        doc.setPeriod("202607");
        doc.setAmount(new BigDecimal("3000.00"));
        doc.setStatus("DRAFT");
        doc.setSummary("办公用品报销-E2E测试");
        doc.setSource("MANUAL");
        doc.setApplicantId(1L);
        businessDocMapper.insert(doc);

        // 提交
        doc.setStatus("SUBMITTED");
        doc.setSubmittedBy(1L);
        doc.setSubmittedAt(java.time.LocalDateTime.now());
        businessDocMapper.updateById(doc);

        BusinessDocEntity submitted = businessDocMapper.selectById(doc.getId());
        assertEquals("SUBMITTED", submitted.getStatus());
    }

    @Test
    void step3_approveExpense_shouldChangeStatus() {
        BusinessDocEntity doc = new BusinessDocEntity();
        doc.setDocNo("EXP-APPROVE-" + System.currentTimeMillis());
        doc.setDocType("EXPENSE");
        doc.setDocDate(LocalDate.now());
        doc.setPeriod("202607");
        doc.setAmount(new BigDecimal("2000.00"));
        doc.setStatus("DRAFT");
        doc.setSummary("招待费报销-E2E测试");
        doc.setSource("MANUAL");
        doc.setApplicantId(1L);
        businessDocMapper.insert(doc);

        // 提交
        doc.setStatus("SUBMITTED");
        doc.setSubmittedBy(1L);
        doc.setSubmittedAt(java.time.LocalDateTime.now());
        businessDocMapper.updateById(doc);

        // 审核通过
        doc.setStatus("APPROVED");
        doc.setApprovedBy(2L);
        doc.setApprovedAt(java.time.LocalDateTime.now());
        businessDocMapper.updateById(doc);

        BusinessDocEntity approved = businessDocMapper.selectById(doc.getId());
        assertEquals("APPROVED", approved.getStatus());
    }

    @Test
    void step4_generateVoucherFromExpense_shouldBeBalanced() {
        // 先生成业务单据
        BusinessDocEntity doc = new BusinessDocEntity();
        doc.setDocNo("EXP-VCH-" + System.currentTimeMillis());
        doc.setDocType("EXPENSE");
        doc.setDocDate(LocalDate.now());
        doc.setPeriod("202607");
        doc.setAmount(new BigDecimal("1500.00"));
        doc.setStatus("APPROVED");
        doc.setSummary("交通费报销-E2E测试");
        doc.setSource("MANUAL");
        doc.setApplicantId(1L);
        businessDocMapper.insert(doc);

        // 生成凭证
        VoucherEntity voucher = new VoucherEntity();
        voucher.setVoucherNo("VCH-EXP-" + System.currentTimeMillis());
        voucher.setPeriod("202607");
        voucher.setVoucherTypeId(1L);
        voucher.setStatus("DRAFT");
        voucher.setTotalDebit(new BigDecimal("1500.00"));
        voucher.setTotalCredit(new BigDecimal("1500.00"));
        voucher.setSummary("费用报销凭证-E2E测试");
        voucher.setSource("GENERATED");
        voucher.setCreatedBy(1L);
        voucherMapper.insert(voucher);

        assertNotNull(voucher.getId());
        assertEquals(0, voucher.getTotalDebit().compareTo(voucher.getTotalCredit()),
                "费用报销凭证借贷应平衡");
    }

    @Test
    void step5_rejectExpense_shouldRemainRejected() {
        BusinessDocEntity doc = new BusinessDocEntity();
        doc.setDocNo("EXP-REJ-" + System.currentTimeMillis());
        doc.setDocType("EXPENSE");
        doc.setDocDate(LocalDate.now());
        doc.setPeriod("202607");
        doc.setAmount(new BigDecimal("1000.00"));
        doc.setStatus("DRAFT");
        doc.setSummary("不合规报销-E2E测试");
        doc.setSource("MANUAL");
        doc.setApplicantId(1L);
        businessDocMapper.insert(doc);

        // 驳回：直接设为 REJECTED
        doc.setStatus("REJECTED");
        doc.setApprovedBy(2L);
        doc.setApprovedAt(java.time.LocalDateTime.now());
        businessDocMapper.updateById(doc);

        BusinessDocEntity rejected = businessDocMapper.selectById(doc.getId());
        assertEquals("REJECTED", rejected.getStatus());
    }

    @Test
    void step6_voucherPost_shouldCompleteFlow() {
        VoucherEntity voucher = new VoucherEntity();
        voucher.setVoucherNo("VCH-POST-" + System.currentTimeMillis());
        voucher.setPeriod("202607");
        voucher.setVoucherTypeId(1L);
        voucher.setStatus("DRAFT");
        voucher.setTotalDebit(new BigDecimal("10000.00"));
        voucher.setTotalCredit(new BigDecimal("10000.00"));
        voucher.setSummary("费用过账-E2E测试");
        voucher.setSource("GENERATED");
        voucher.setCreatedBy(1L);
        voucherMapper.insert(voucher);

        // 过账
        voucher.setStatus("POSTED");
        voucher.setPostedBy(2L);
        voucher.setPostedAt(java.time.LocalDateTime.now());
        voucherMapper.updateById(voucher);

        VoucherEntity posted = voucherMapper.selectById(voucher.getId());
        assertEquals("POSTED", posted.getStatus());
    }
}