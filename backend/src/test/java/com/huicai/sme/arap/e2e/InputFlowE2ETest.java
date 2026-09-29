package com.huicai.sme.arap.e2e;

import com.huicai.common.test.AbstractMapperTest;
import com.huicai.base.business.entity.BusinessDocEntity;
import com.huicai.base.voucher.entity.VoucherEntity;
import com.huicai.base.business.mapper.BusinessDocMapper;
import com.huicai.base.voucher.mapper.VoucherMapper;
import com.huicai.base.business.entity.InputInvoiceEntity;
import com.huicai.base.business.mapper.InputInvoiceMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 进项发票 E2E 测试 (P40)。
 *
 * 覆盖：手动创建 → 提交审核 → 审核通过 → 创建 INVOICE_IN 业务单据 + 凭证 → 验证双向关联。
 * 与 SalesFlowE2ETest 对称，科目方向相反。
 *
 * <h3>历史说明（REQ-2026-117 / 慢测 C 类）</h3>
 * 本测试此前存在 4 处与当前 schema 不符的写法：
 * <ol>
 *   <li><b>悬空外键</b>：{@code setVendorId(1L)} / {@code setVendorId(99L)}，而迁移后
 *       {@code t_vendor} 为空表，触发 {@code fk_input_invoice_vendor}；</li>
 *   <li><b>{@code t_business_doc.doc_no} 未赋值</b>：该列 NOT NULL 无默认值，而原代码
 *       只在回写时读 {@code doc.getDocNo()} 从未 set；</li>
 *   <li><b>断言不存在的列</b>：{@code invoice.docNo} / {@code invoice.voucherNo} /
 *       {@code voucher.sourceDocNo} / {@code voucher.sourceDocType} 均标注
 *       {@code @TableField(exist = false)}（注释明写「DB 无此列」），经 DB 往返读回
 *       必为 null，原断言无法成立；</li>
 *   <li><b>{@code vendorId=99}</b> 与 {@code vendorId=1} 混用，且与
 *       {@code t_vendor} 无任何对应行。</li>
 * </ol>
 * 修法：改用基类 {@code ensureVendor()} 造真实供应商；显式赋 {@code docNo}；
 * 对 {@code exist=false} 字段改为断言「DB 侧对应 id 列已正确落库」，
 * 不再断言编号冗余字符串。
 */
public class InputFlowE2ETest extends AbstractMapperTest {

    private static final String PERIOD = "202607";

    @Autowired
    private InputInvoiceMapper inputInvoiceMapper;

    @Autowired
    private BusinessDocMapper businessDocMapper;

    @Autowired
    private VoucherMapper voucherMapper;

    /**
     * 造一张进项发票。{@code vendorId} 由基类 {@code ensureVendor()} 提供真实行。
     */
    private InputInvoiceEntity newInvoice(String seq, Long vendorId, String total,
                                         String amount, String taxAmount, String status) {
        InputInvoiceEntity invoice = new InputInvoiceEntity();
        invoice.setInvoiceNo("IN-2026-" + seq);
        invoice.setInvoiceType("SPECIAL");
        invoice.setVendorId(vendorId);
        invoice.setVendorName("测试供应商");
        invoice.setInvoiceDate(LocalDate.now());
        invoice.setPeriod(PERIOD);
        invoice.setAmount(new BigDecimal(amount));
        invoice.setTaxRate(new BigDecimal("13"));
        invoice.setTaxAmount(new BigDecimal(taxAmount));
        invoice.setTotalAmount(new BigDecimal(total));
        invoice.setStatus(status);
        invoice.setCertificationStatus("UNCERTIFIED");
        invoice.setCreatedBy(1L);
        invoice.setDeleted(0);
        return invoice;
    }

    /**
     * 造一张 INVOICE_IN 业务单据。{@code docNo} 为 NOT NULL，必须显式赋值。
     */
    private BusinessDocEntity newDoc(String seq, InputInvoiceEntity invoice) {
        BusinessDocEntity doc = new BusinessDocEntity();
        doc.setDocNo("DOC-IN-2026-" + seq);
        doc.setDocType("INVOICE_IN");
        doc.setSupplierId(invoice.getVendorId());
        doc.setInvoiceId(invoice.getId());
        doc.setInvoiceNo(invoice.getInvoiceNo());
        doc.setPeriod(PERIOD);
        doc.setDocDate(invoice.getInvoiceDate());
        doc.setAmount(invoice.getTotalAmount());
        doc.setSettledAmount(BigDecimal.ZERO);
        doc.setUnsettledAmount(invoice.getTotalAmount());
        doc.setStatus("DRAFT");
        doc.setSource("MANUAL");
        return doc;
    }

    @Test
    @DisplayName("步骤1: 创建进项发票应成功")
    void step1_createInputInvoice_shouldSucceed() {
        Long vendorId = ensureVendor(DEFAULT_ENTERPRISE_ID);
        InputInvoiceEntity invoice = newInvoice("0001", vendorId, "11300.00", "10000.00", "1300.00",
                "PENDING_CONFIRM");

        int rows = inputInvoiceMapper.insert(invoice);

        assertEquals(1, rows);
        assertNotNull(invoice.getId());
        assertEquals("PENDING_CONFIRM", invoice.getStatus());
        assertEquals(0, new BigDecimal("11300.00").compareTo(invoice.getTotalAmount()));
    }

    @Test
    @DisplayName("步骤2: 提交审核并审核通过应变更状态")
    void step2_auditInputInvoice_shouldChangeStatus() {
        Long vendorId = ensureVendor(DEFAULT_ENTERPRISE_ID);
        InputInvoiceEntity invoice = newInvoice("0002", vendorId, "11300.00", "10000.00", "1300.00",
                "PENDING_CONFIRM");
        inputInvoiceMapper.insert(invoice);

        // 提交审核
        invoice.setStatus("PENDING_REVIEW");
        assertEquals(1, inputInvoiceMapper.updateById(invoice));

        // 审核通过
        invoice.setStatus("CONFIRMED");
        invoice.setAuditedBy(1L);
        assertEquals(1, inputInvoiceMapper.updateById(invoice));

        InputInvoiceEntity audited = inputInvoiceMapper.selectById(invoice.getId());
        assertEquals("CONFIRMED", audited.getStatus());
        // t_input_invoice.audited_by 是真实列，可断言
        assertNotNull(audited.getAuditedBy());
    }

    @Test
    @DisplayName("步骤3: 审核后应创建 INVOICE_IN 业务单据并回写发票 doc_id")
    void step3_afterAudit_shouldCreateBusinessDoc() {
        Long vendorId = ensureVendor(DEFAULT_ENTERPRISE_ID);
        InputInvoiceEntity invoice = newInvoice("0003", vendorId, "5650.00", "5000.00", "650.00",
                "CONFIRMED");
        inputInvoiceMapper.insert(invoice);

        BusinessDocEntity doc = newDoc("0003", invoice);
        businessDocMapper.insert(doc);

        assertNotNull(doc.getId());
        assertEquals(invoice.getInvoiceNo(), doc.getInvoiceNo());
        assertEquals(0, invoice.getTotalAmount().compareTo(doc.getAmount()));
        assertEquals("DRAFT", doc.getStatus());
        assertEquals("INVOICE_IN", doc.getDocType());

        // 回写发票（doc_id 是真实列；docNo 为 exist=false 不落库）
        invoice.setDocId(doc.getId());
        assertEquals(1, inputInvoiceMapper.updateById(invoice));

        InputInvoiceEntity updated = inputInvoiceMapper.selectById(invoice.getId());
        assertNotNull(updated.getDocId());
        assertEquals(doc.getId(), updated.getDocId());
    }

    @Test
    @DisplayName("步骤4: 审核后应创建凭证并完成双向关联")
    void step4_afterAudit_shouldCreateVoucher() {
        Long vendorId = ensureVendor(DEFAULT_ENTERPRISE_ID);
        InputInvoiceEntity invoice = newInvoice("0004", vendorId, "5650.00", "5000.00", "650.00",
                "VOUCHERED");
        inputInvoiceMapper.insert(invoice);

        BusinessDocEntity doc = newDoc("0004", invoice);
        businessDocMapper.insert(doc);

        VoucherEntity voucher = newVoucher("PZ-2026-07-0004", doc, invoice.getTotalAmount());
        voucherMapper.insert(voucher);

        // 回写发票：voucher_id 是真实列
        invoice.setVoucherId(voucher.getId());
        inputInvoiceMapper.updateById(invoice);

        // 回写业务单据
        doc.setVoucherId(voucher.getId());
        doc.setVoucherNo(voucher.getVoucherNo());
        doc.setStatus("VOUCHERED");
        businessDocMapper.updateById(doc);

        assertNotNull(voucher.getId());
        assertEquals(0, voucher.getTotalDebit().compareTo(voucher.getTotalCredit()));
        assertEquals(PERIOD, voucher.getPeriod());
        // 凭证 → 业务单据的真实溯源列是 business_doc_id
        assertEquals(doc.getId(), voucher.getBusinessDocId());

        // 验证双向关联：单据上的 voucher_no 是真实列，可断言
        InputInvoiceEntity finalInv = inputInvoiceMapper.selectById(invoice.getId());
        BusinessDocEntity finalDoc = businessDocMapper.selectById(doc.getId());

        assertEquals(voucher.getId(), finalInv.getVoucherId());
        assertEquals(voucher.getVoucherNo(), finalDoc.getVoucherNo());
        assertEquals("VOUCHERED", finalDoc.getStatus());
        assertEquals(0, finalDoc.getAmount().compareTo(voucher.getTotalDebit()));
    }

    @Test
    @DisplayName("全流程: 进项发票 → 业务单据 → 凭证（编号传递验证）")
    void fullInputFlow_endToEnd_shouldCompleteSuccessfully() {
        Long vendorId = ensureVendor(DEFAULT_ENTERPRISE_ID);

        // === 1. 创建进项发票 ===
        InputInvoiceEntity invoice = newInvoice("0099", vendorId, "22600.00", "20000.00", "2600.00",
                "PENDING_CONFIRM");
        inputInvoiceMapper.insert(invoice);

        // === 2. 提交审核 ===
        invoice.setStatus("PENDING_REVIEW");
        inputInvoiceMapper.updateById(invoice);

        // === 3. 审核通过 ===
        invoice.setStatus("CONFIRMED");
        invoice.setAuditedBy(1L);
        inputInvoiceMapper.updateById(invoice);

        // === 4. 创建 INVOICE_IN 业务单据 ===
        BusinessDocEntity doc = newDoc("0099", invoice);
        businessDocMapper.insert(doc);

        invoice.setDocId(doc.getId());
        inputInvoiceMapper.updateById(invoice);

        // === 5. 创建凭证 ===
        VoucherEntity voucher = newVoucher("PZ-2026-07-0099", doc, invoice.getTotalAmount());
        voucherMapper.insert(voucher);

        // 回写
        invoice.setStatus("VOUCHERED");
        invoice.setVoucherId(voucher.getId());
        inputInvoiceMapper.updateById(invoice);

        doc.setVoucherId(voucher.getId());
        doc.setVoucherNo(voucher.getVoucherNo());
        doc.setStatus("VOUCHERED");
        businessDocMapper.updateById(doc);

        // === 6. 验证全链路 ===
        InputInvoiceEntity finalInv = inputInvoiceMapper.selectById(invoice.getId());
        BusinessDocEntity finalDoc = businessDocMapper.selectById(doc.getId());
        VoucherEntity finalVoucher = voucherMapper.selectById(voucher.getId());

        // 发票状态与审核人（audited_by 为真实列）
        assertEquals("VOUCHERED", finalInv.getStatus());
        assertNotNull(finalInv.getAuditedBy());

        // 业务单据
        assertEquals("VOUCHERED", finalDoc.getStatus());
        assertEquals(invoice.getInvoiceNo(), finalDoc.getInvoiceNo());
        assertEquals(0, invoice.getTotalAmount().compareTo(finalDoc.getAmount()));

        // 凭证借贷平衡 + 溯源
        assertEquals(0, finalVoucher.getTotalDebit().compareTo(finalVoucher.getTotalCredit()));
        assertEquals(doc.getId(), finalVoucher.getBusinessDocId());

        // 双向关联：发票 → 单据 → 凭证
        assertEquals(finalVoucher.getId(), finalInv.getVoucherId());
        assertEquals(finalVoucher.getId(), finalDoc.getVoucherId());
        assertEquals(finalDoc.getId(), finalInv.getDocId());
        assertEquals(finalInv.getId(), finalDoc.getInvoiceId());

        // 金额一致性
        assertEquals(0, finalInv.getTotalAmount().compareTo(finalDoc.getAmount()));
        assertEquals(0, finalDoc.getAmount().compareTo(finalVoucher.getTotalDebit()));
    }

    /**
     * 造一张付款凭证。凭证 → 业务单据的真实关联列是 {@code business_doc_id}
     * （带 FK {@code fk_voucher_business_doc}）。
     */
    private VoucherEntity newVoucher(String voucherNo, BusinessDocEntity doc, BigDecimal total) {
        VoucherEntity voucher = new VoucherEntity();
        voucher.setVoucherNo(voucherNo);
        voucher.setPeriod(PERIOD);
        voucher.setVoucherTypeId(2L); // 付款凭证
        voucher.setStatus("DRAFT");
        voucher.setSource("GENERATED");
        voucher.setBusinessDocId(doc.getId());
        voucher.setTotalDebit(total);
        voucher.setTotalCredit(total);
        voucher.setCreatedBy(1L);
        return voucher;
    }
}
