package com.huicai.sme.arap.e2e;

import com.huicai.common.test.AbstractMapperTest;
import com.huicai.base.business.entity.BusinessDocEntity;
import com.huicai.base.voucher.entity.VoucherEntity;
import com.huicai.base.business.mapper.BusinessDocMapper;
import com.huicai.base.voucher.mapper.VoucherMapper;
import com.huicai.base.business.entity.OutputInvoiceEntity;
import com.huicai.base.business.mapper.OutputInvoiceMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 销项发票 E2E 测试（P40 销售链路）。
 *
 * <h3>历史说明（REQ-2026-117 / 慢测 C 类）</h3>
 * 本测试此前存在 3 处与当前 schema 不符的写法：
 * <ol>
 *   <li><b>悬空外键</b>：{@code setCustomerId(1L)} / {@code setCustomerId(99L)}，而迁移后
 *       {@code t_customer} 为空表（seed 0 行），触发
 *       {@code fk_output_invoice_customer}；</li>
 *   <li><b>{@code t_business_doc.doc_no} 未赋值</b>：该列 NOT NULL 无默认值；</li>
 *   <li><b>单据状态用发票状态</b>：写 {@code "CONFIRMED"} / {@code "SETTLED"}，而
 *       {@code chk_doc_status} 允许集为
 *       DRAFT/SUBMITTED/APPROVED/VOUCHERED/PARTIALLY_RECONCILED/FULLY_RECONCILED/
 *       CLOSED/REJECTED/REVERSED —— {@code CONFIRMED} 属发票侧状态，
 *       {@code SETTLED} 根本不存在，触发 check 违约。</li>
 * </ol>
 * 修法：改用基类 {@code ensureCustomer()} 造真实客户；显式赋 {@code docNo}；
 * 单据状态改用 {@code APPROVED}（已审核待结算）与 {@code FULLY_RECONCILED}（已结清）。
 * 另补 {@code voucher.businessDocId} 以建立凭证 → 单据的真实溯源。
 */
public class SalesFlowE2ETest extends AbstractMapperTest {

    private static final String PERIOD = "202606";

    /** t_business_doc.chk_doc_status 合法值（发票状态 CONFIRMED 在此非法） */
    private static final String DOC_STATUS_APPROVED = "APPROVED";
    private static final String DOC_STATUS_FULLY_RECONCILED = "FULLY_RECONCILED";

    @Autowired
    private OutputInvoiceMapper outputInvoiceMapper;

    @Autowired
    private BusinessDocMapper businessDocMapper;

    @Autowired
    private VoucherMapper voucherMapper;

    private OutputInvoiceEntity newInvoice(String seq, Long customerId, String amount,
                                          String taxAmount, String total, String status) {
        OutputInvoiceEntity invoice = new OutputInvoiceEntity();
        invoice.setInvoiceNo("XS-2026-" + seq);
        invoice.setInvoiceType("SPECIAL");
        invoice.setCustomerId(customerId);
        invoice.setCustomerName("测试客户");
        invoice.setInvoiceDate(LocalDate.now());
        invoice.setPeriod(PERIOD);
        invoice.setAmount(new BigDecimal(amount));
        invoice.setTaxRate(new BigDecimal("0.13"));
        invoice.setTaxAmount(new BigDecimal(taxAmount));
        invoice.setTotalAmount(new BigDecimal(total));
        invoice.setStatus(status);
        invoice.setCreatedBy(1L);
        invoice.setDeleted(0);
        return invoice;
    }

    /** 造一张 INVOICE_OUT 业务单据。{@code docNo} NOT NULL，必须显式赋值。 */
    private BusinessDocEntity newDoc(String seq, OutputInvoiceEntity invoice) {
        BusinessDocEntity doc = new BusinessDocEntity();
        doc.setDocNo("DOC-OUT-2026-" + seq);
        doc.setDocType("INVOICE_OUT");
        doc.setCustomerId(invoice.getCustomerId());
        doc.setInvoiceId(invoice.getId());
        doc.setInvoiceNo(invoice.getInvoiceNo());
        doc.setPeriod(PERIOD);
        doc.setDocDate(invoice.getInvoiceDate());
        doc.setAmount(invoice.getTotalAmount());
        doc.setSettledAmount(BigDecimal.ZERO);
        doc.setUnsettledAmount(invoice.getTotalAmount());
        doc.setStatus(DOC_STATUS_APPROVED);
        return doc;
    }

    private VoucherEntity newVoucher(String voucherNo, BusinessDocEntity doc, BigDecimal total) {
        VoucherEntity voucher = new VoucherEntity();
        voucher.setVoucherNo(voucherNo);
        voucher.setPeriod(PERIOD);
        voucher.setVoucherTypeId(1L); // 收款凭证
        voucher.setStatus("DRAFT");
        voucher.setSource("GENERATED");
        voucher.setBusinessDocId(doc.getId());
        voucher.setTotalDebit(total);
        voucher.setTotalCredit(total);
        voucher.setCreatedBy(1L);
        return voucher;
    }

    @Test
    @DisplayName("步骤1: 创建销项发票应成功")
    void step1_createSalesInvoice_shouldSucceed() {
        Long customerId = ensureCustomer(DEFAULT_ENTERPRISE_ID);
        OutputInvoiceEntity invoice = newInvoice("0001", customerId, "10000.00", "1300.00",
                "11300.00", "PENDING_CONFIRM");

        int rows = outputInvoiceMapper.insert(invoice);

        assertEquals(1, rows);
        assertNotNull(invoice.getId());
        assertEquals("PENDING_CONFIRM", invoice.getStatus());
        assertEquals(0, new BigDecimal("11300.00").compareTo(invoice.getTotalAmount()));
    }

    @Test
    @DisplayName("步骤2: 审核销项发票应变更状态")
    void step2_auditSalesInvoice_shouldChangeStatus() {
        Long customerId = ensureCustomer(DEFAULT_ENTERPRISE_ID);
        OutputInvoiceEntity invoice = newInvoice("0002", customerId, "10000.00", "1300.00",
                "11300.00", "PENDING_CONFIRM");
        outputInvoiceMapper.insert(invoice);

        invoice.setStatus("CONFIRMED");
        int rows = outputInvoiceMapper.updateById(invoice);

        assertEquals(1, rows);
        OutputInvoiceEntity audited = outputInvoiceMapper.selectById(invoice.getId());
        assertEquals("CONFIRMED", audited.getStatus());
    }

    @Test
    @DisplayName("步骤3: 审核后应创建 INVOICE_OUT 业务单据并回写发票 doc_id")
    void step3_afterAudit_shouldCreateBusinessDoc() {
        Long customerId = ensureCustomer(DEFAULT_ENTERPRISE_ID);
        OutputInvoiceEntity invoice = newInvoice("0003", customerId, "5000.00", "650.00",
                "5650.00", "CONFIRMED");
        outputInvoiceMapper.insert(invoice);

        BusinessDocEntity doc = newDoc("0003", invoice);
        businessDocMapper.insert(doc);

        assertNotNull(doc.getId());
        assertEquals(invoice.getInvoiceNo(), doc.getInvoiceNo());
        assertEquals(0, invoice.getTotalAmount().compareTo(doc.getAmount()));
        assertEquals(DOC_STATUS_APPROVED, doc.getStatus());

        // 回写发票：doc_id 是真实列
        invoice.setDocId(doc.getId());
        assertEquals(1, outputInvoiceMapper.updateById(invoice));
        assertEquals(doc.getId(), outputInvoiceMapper.selectById(invoice.getId()).getDocId());
    }

    @Test
    @DisplayName("步骤4: 结算业务单据应更新已结/未结金额与状态")
    void step4_settleBusinessDoc_shouldUpdateStatusAndAmount() {
        Long customerId = ensureCustomer(DEFAULT_ENTERPRISE_ID);
        OutputInvoiceEntity invoice = newInvoice("0004", customerId, "5000.00", "650.00",
                "5650.00", "CONFIRMED");
        outputInvoiceMapper.insert(invoice);

        BusinessDocEntity doc = newDoc("0004", invoice);
        businessDocMapper.insert(doc);

        doc.setSettledAmount(new BigDecimal("5650.00"));
        doc.setUnsettledAmount(BigDecimal.ZERO);
        doc.setStatus(DOC_STATUS_FULLY_RECONCILED);
        int rows = businessDocMapper.updateById(doc);

        assertEquals(1, rows);
        BusinessDocEntity settled = businessDocMapper.selectById(doc.getId());
        assertEquals(0, new BigDecimal("5650.00").compareTo(settled.getSettledAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(settled.getUnsettledAmount()));
        assertEquals(DOC_STATUS_FULLY_RECONCILED, settled.getStatus());
    }

    @Test
    @DisplayName("步骤5: 结算后应生成凭证并回写单据 voucher_id")
    void step5_afterSettlement_shouldCreateVoucher() {
        Long customerId = ensureCustomer(DEFAULT_ENTERPRISE_ID);
        OutputInvoiceEntity invoice = newInvoice("0005", customerId, "5000.00", "650.00",
                "5650.00", "CONFIRMED");
        outputInvoiceMapper.insert(invoice);

        BusinessDocEntity doc = newDoc("0005", invoice);
        doc.setSettledAmount(new BigDecimal("5650.00"));
        doc.setUnsettledAmount(BigDecimal.ZERO);
        doc.setStatus(DOC_STATUS_FULLY_RECONCILED);
        businessDocMapper.insert(doc);

        VoucherEntity voucher = newVoucher("PZ-2026-06-0001", doc, new BigDecimal("5650.00"));
        voucherMapper.insert(voucher);

        doc.setVoucherId(voucher.getId());
        doc.setVoucherNo(voucher.getVoucherNo());
        businessDocMapper.updateById(doc);

        assertNotNull(voucher.getId());
        assertEquals(0, voucher.getTotalDebit().compareTo(voucher.getTotalCredit()));
        assertEquals(PERIOD, voucher.getPeriod());
        // 凭证 → 单据真实溯源列
        assertEquals(doc.getId(), voucher.getBusinessDocId());

        BusinessDocEntity finalDoc = businessDocMapper.selectById(doc.getId());
        assertEquals(voucher.getId(), finalDoc.getVoucherId());
        assertEquals(voucher.getVoucherNo(), finalDoc.getVoucherNo());
    }

    @Test
    @DisplayName("全流程: 销项发票 → 业务单据 → 结算 → 凭证（编号传递验证）")
    void fullSalesFlow_endToEnd_shouldCompleteSuccessfully() {
        Long customerId = ensureCustomer(DEFAULT_ENTERPRISE_ID);

        OutputInvoiceEntity invoice = newInvoice("0099", customerId, "20000.00", "2600.00",
                "22600.00", "PENDING_CONFIRM");
        outputInvoiceMapper.insert(invoice);

        invoice.setStatus("CONFIRMED");
        outputInvoiceMapper.updateById(invoice);

        BusinessDocEntity doc = newDoc("0099", invoice);
        businessDocMapper.insert(doc);

        invoice.setDocId(doc.getId());
        outputInvoiceMapper.updateById(invoice);

        doc.setSettledAmount(invoice.getTotalAmount());
        doc.setUnsettledAmount(BigDecimal.ZERO);
        doc.setStatus(DOC_STATUS_FULLY_RECONCILED);
        businessDocMapper.updateById(doc);

        VoucherEntity voucher = newVoucher("PZ-2026-06-0099", doc, invoice.getTotalAmount());
        voucherMapper.insert(voucher);

        doc.setVoucherId(voucher.getId());
        doc.setVoucherNo(voucher.getVoucherNo());
        businessDocMapper.updateById(doc);

        OutputInvoiceEntity finalInvoice = outputInvoiceMapper.selectById(invoice.getId());
        BusinessDocEntity finalDoc = businessDocMapper.selectById(doc.getId());
        VoucherEntity finalVoucher = voucherMapper.selectById(voucher.getId());

        assertNotNull(finalInvoice);
        assertNotNull(finalDoc);
        assertNotNull(finalVoucher);

        assertEquals("CONFIRMED", finalInvoice.getStatus());
        assertEquals(0, finalInvoice.getTotalAmount().compareTo(finalDoc.getAmount()));
        assertEquals(0, finalDoc.getSettledAmount().compareTo(finalVoucher.getTotalDebit()));
        assertEquals(0, finalVoucher.getTotalDebit().compareTo(finalVoucher.getTotalCredit()));

        // 双向关联：发票 → 单据 → 凭证
        assertEquals(finalDoc.getId(), finalInvoice.getDocId());
        assertEquals(finalDoc.getVoucherId(), finalVoucher.getId());
        assertEquals(finalDoc.getId(), finalVoucher.getBusinessDocId());
        assertEquals(DOC_STATUS_FULLY_RECONCILED, finalDoc.getStatus());
    }
}
