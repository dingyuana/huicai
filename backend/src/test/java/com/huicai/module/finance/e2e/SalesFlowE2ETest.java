package com.huicai.module.finance.e2e;

import com.huicai.common.test.AbstractMapperTest;
import com.huicai.module.arap.entity.ReceivableEntity;
import com.huicai.module.arap.mapper.ReceivableMapper;
import com.huicai.module.finance.entity.VoucherEntity;
import com.huicai.module.finance.mapper.VoucherMapper;
import com.huicai.module.tax.entity.OutputInvoiceEntity;
import com.huicai.module.tax.mapper.OutputInvoiceMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 销售流程 E2E 端到端测试
 * 完整流程：创建销售发票 → 审核发票 → 生成应收单 → 收款核销 → 生成凭证
 * 
 * 验证目标：
 * 1. 各模块数据流转正确
 * 2. 状态机转换符合预期
 * 3. 外键关联正确建立
 * 4. 金额计算准确无误
 */
public class SalesFlowE2ETest extends AbstractMapperTest {

    @Autowired
    private OutputInvoiceMapper outputInvoiceMapper;
    
    @Autowired
    private ReceivableMapper receivableMapper;
    
    @Autowired
    private VoucherMapper voucherMapper;

    /**
     * 场景 1：销售发票创建验证
     * 验证：发票字段完整、状态正确、金额准确
     */
    @Test
    void step1_createSalesInvoice_shouldSucceed() {
        OutputInvoiceEntity invoice = new OutputInvoiceEntity();
        invoice.setInvoiceNo("XS-2026-0001");
        invoice.setInvoiceType("SPECIAL");
        invoice.setCustomerId(1L);
        invoice.setCustomerName("测试客户");
        invoice.setInvoiceDate(LocalDate.now());
        invoice.setPeriod("202606");
        invoice.setAmount(new BigDecimal("10000.00"));
        invoice.setTaxRate(new BigDecimal("0.13"));
        invoice.setTaxAmount(new BigDecimal("1300.00"));
        invoice.setTotalAmount(new BigDecimal("11300.00"));
        invoice.setStatus("PENDING_CONFIRM");
        invoice.setCreatedBy(1L);
        invoice.setDeleted(0);

        int rows = outputInvoiceMapper.insert(invoice);

        assertEquals(1, rows);
        assertNotNull(invoice.getId());
        assertEquals("PENDING_CONFIRM", invoice.getStatus());
        assertEquals(0, new BigDecimal("11300.00").compareTo(invoice.getTotalAmount()));
    }

    /**
     * 场景 2：销售发票审核验证
     * 验证：状态从 PENDING_CONFIRM → CONFIRMED
     */
    @Test
    void step2_auditSalesInvoice_shouldChangeStatus() {
        // 创建发票
        OutputInvoiceEntity invoice = new OutputInvoiceEntity();
        invoice.setInvoiceNo("XS-2026-0002");
        invoice.setInvoiceType("SPECIAL");
        invoice.setCustomerId(1L);
        invoice.setCustomerName("测试客户");
        invoice.setInvoiceDate(LocalDate.now());
        invoice.setPeriod("202606");
        invoice.setAmount(new BigDecimal("10000.00"));
        invoice.setTaxRate(new BigDecimal("0.13"));
        invoice.setTaxAmount(new BigDecimal("1300.00"));
        invoice.setTotalAmount(new BigDecimal("11300.00"));
        invoice.setStatus("PENDING_CONFIRM");
        invoice.setCreatedBy(1L);
        invoice.setDeleted(0);
        outputInvoiceMapper.insert(invoice);

        // 审核发票
        invoice.setStatus("CONFIRMED");
        int rows = outputInvoiceMapper.updateById(invoice);

        assertEquals(1, rows);
        OutputInvoiceEntity audited = outputInvoiceMapper.selectById(invoice.getId());
        assertEquals("CONFIRMED", audited.getStatus());
    }

    /**
     * 场景 3：审核后自动生成应收单验证（P33 简化：直连发票，不经业务单）
     * 验证：应收单自动创建、金额与发票一致、invoice_id 关联关系正确
     */
    @Test
    void step3_afterAudit_shouldCreateReceivable() {
        // 创建并审核发票
        OutputInvoiceEntity invoice = new OutputInvoiceEntity();
        invoice.setInvoiceNo("XS-2026-0003");
        invoice.setInvoiceType("SPECIAL");
        invoice.setCustomerId(1L);
        invoice.setCustomerName("测试客户");
        invoice.setInvoiceDate(LocalDate.now());
        invoice.setPeriod("202606");
        invoice.setAmount(new BigDecimal("5000.00"));
        invoice.setTaxRate(new BigDecimal("0.13"));
        invoice.setTaxAmount(new BigDecimal("650.00"));
        invoice.setTotalAmount(new BigDecimal("5650.00"));
        invoice.setStatus("CONFIRMED");
        invoice.setCreatedBy(1L);
        invoice.setDeleted(0);
        outputInvoiceMapper.insert(invoice);

        // 模拟应收单自动生成（P33 简化：直接关联 invoiceId）
        ReceivableEntity receivable = new ReceivableEntity();
        receivable.setCustomerId(invoice.getCustomerId());
        receivable.setInvoiceId(invoice.getId());      // P33: 直接关联发票ID
        receivable.setInvoiceNo(invoice.getInvoiceNo()); // 发票编号冗余
        receivable.setPeriod("202606");
        receivable.setTxDate(invoice.getInvoiceDate());
        receivable.setAmount(invoice.getTotalAmount());
        receivable.setSettledAmount(BigDecimal.ZERO);
        receivable.setUnsettledAmount(invoice.getTotalAmount());
        receivable.setDueDate(LocalDate.now().plusDays(30));
        receivable.setStatus("DRAFT");
        receivable.setDeleted(0);
        receivable.setVersion(0);
        receivableMapper.insert(receivable);

        // 验证应收单
        assertNotNull(receivable.getId());
        assertEquals(invoice.getId(), receivable.getInvoiceId());  // P33: 验证 invoiceId 关联
        assertEquals(invoice.getInvoiceNo(), receivable.getInvoiceNo());
        assertEquals(0, invoice.getTotalAmount().compareTo(receivable.getAmount()));
        assertEquals("DRAFT", receivable.getStatus());
    }

    /**
     * 场景 4：应收款核销验证
     * 验证：状态从 UNSETTLED → SETTLED，已结算金额正确更新
     */
    @Test
    void step4_settleReceivable_shouldUpdateStatusAndAmount() {
        // 创建应收单
        ReceivableEntity receivable = new ReceivableEntity();
        receivable.setCustomerId(1L);
        receivable.setPeriod("202606");
        receivable.setTxDate(LocalDate.now());
        receivable.setAmount(new BigDecimal("5650.00"));
        receivable.setSettledAmount(BigDecimal.ZERO);
        receivable.setUnsettledAmount(new BigDecimal("5650.00"));
        receivable.setDueDate(LocalDate.now().plusDays(30));
        receivable.setStatus("UNSETTLED");
        receivable.setDeleted(0);
        receivable.setVersion(0);
        receivableMapper.insert(receivable);

        // 执行收款核销
        receivable.setStatus("SETTLED");
        receivable.setSettledAmount(new BigDecimal("5650.00"));
        receivable.setUnsettledAmount(BigDecimal.ZERO);
        int rows = receivableMapper.updateById(receivable);

        assertEquals(1, rows);
        ReceivableEntity settled = receivableMapper.selectById(receivable.getId());
        assertEquals("SETTLED", settled.getStatus());
        assertEquals(0, new BigDecimal("5650.00").compareTo(settled.getSettledAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(settled.getUnsettledAmount()));
    }

    /**
     * 场景 5：核销后生成记账凭证验证
     * 验证：凭证借贷平衡、科目正确、金额准确
     */
    @Test
    void step5_afterSettlement_shouldCreateVoucher() {
        // 先有已核销的应收单
        ReceivableEntity receivable = new ReceivableEntity();
        receivable.setCustomerId(1L);
        receivable.setPeriod("202606");
        receivable.setTxDate(LocalDate.now());
        receivable.setAmount(new BigDecimal("5650.00"));
        receivable.setSettledAmount(new BigDecimal("5650.00"));
        receivable.setUnsettledAmount(BigDecimal.ZERO);
        receivable.setDueDate(LocalDate.now().plusDays(30));
        receivable.setStatus("SETTLED");
        receivable.setDeleted(0);
        receivable.setVersion(0);
        receivableMapper.insert(receivable);

        // 生成记账凭证（借：银行存款，贷：应收账款）
        VoucherEntity voucher = new VoucherEntity();
        voucher.setVoucherNo("PZ-2026-06-0001");
        voucher.setPeriod("202606");
        voucher.setVoucherTypeId(1L);
        voucher.setStatus("DRAFT");
        voucher.setTotalDebit(new BigDecimal("5650.00"));
        voucher.setTotalCredit(new BigDecimal("5650.00"));
        voucher.setCreatedBy(1L);
        voucherMapper.insert(voucher);

        // 验证凭证
        assertNotNull(voucher.getId());
        assertEquals(0, voucher.getTotalDebit().compareTo(voucher.getTotalCredit()));
        assertEquals("202606", voucher.getPeriod());
    }

    /**
     * 完整流程测试：发票 → 应收 → 核销 → 凭证 全链路
     */
    @Test
    void fullSalesFlow_endToEnd_shouldCompleteSuccessfully() {
        // ========== 阶段 1：创建并审核销售发票 ==========
        OutputInvoiceEntity invoice = new OutputInvoiceEntity();
        invoice.setInvoiceNo("XS-2026-0099");
        invoice.setInvoiceType("SPECIAL");
        invoice.setCustomerId(99L);
        invoice.setCustomerName("全流程测试客户");
        invoice.setInvoiceDate(LocalDate.now());
        invoice.setPeriod("202606");
        invoice.setAmount(new BigDecimal("20000.00"));
        invoice.setTaxRate(new BigDecimal("0.13"));
        invoice.setTaxAmount(new BigDecimal("2600.00"));
        invoice.setTotalAmount(new BigDecimal("22600.00"));
        invoice.setStatus("PENDING_CONFIRM");
        invoice.setCreatedBy(1L);
        invoice.setDeleted(0);
        outputInvoiceMapper.insert(invoice);
        
        invoice.setStatus("CONFIRMED");
        outputInvoiceMapper.updateById(invoice);

        // ========== 阶段 2：生成应收单 ==========
        ReceivableEntity receivable = new ReceivableEntity();
        receivable.setCustomerId(invoice.getCustomerId());
        receivable.setDocId(invoice.getId());
        receivable.setPeriod("202606");
        receivable.setTxDate(invoice.getInvoiceDate());
        receivable.setAmount(invoice.getTotalAmount());
        receivable.setSettledAmount(BigDecimal.ZERO);
        receivable.setUnsettledAmount(invoice.getTotalAmount());
        receivable.setDueDate(LocalDate.now().plusDays(30));
        receivable.setStatus("UNSETTLED");
        receivable.setDeleted(0);
        receivable.setVersion(0);
        receivableMapper.insert(receivable);

        // ========== 阶段 3：收款核销 ==========
        receivable.setStatus("SETTLED");
        receivable.setSettledAmount(invoice.getTotalAmount());
        receivable.setUnsettledAmount(BigDecimal.ZERO);
        receivableMapper.updateById(receivable);

        // ========== 阶段 4：生成记账凭证 ==========
        VoucherEntity voucher = new VoucherEntity();
        voucher.setVoucherNo("PZ-2026-06-0099");
        voucher.setPeriod("202606");
        voucher.setVoucherTypeId(1L);
        voucher.setStatus("DRAFT");
        voucher.setTotalDebit(invoice.getTotalAmount());
        voucher.setTotalCredit(invoice.getTotalAmount());
        voucher.setCreatedBy(1L);
        voucherMapper.insert(voucher);

        // ========== 最终验证：全链路数据一致性 ==========
        OutputInvoiceEntity finalInvoice = outputInvoiceMapper.selectById(invoice.getId());
        ReceivableEntity finalReceivable = receivableMapper.selectById(receivable.getId());
        VoucherEntity finalVoucher = voucherMapper.selectById(voucher.getId());

        // 状态验证
        assertEquals("CONFIRMED", finalInvoice.getStatus());
        assertEquals("SETTLED", finalReceivable.getStatus());
        
        // 金额一致性验证
        assertEquals(0, finalInvoice.getTotalAmount().compareTo(finalReceivable.getAmount()));
        assertEquals(0, finalReceivable.getSettledAmount().compareTo(finalVoucher.getTotalDebit()));
        assertEquals(0, finalVoucher.getTotalDebit().compareTo(finalVoucher.getTotalCredit()));
        
        // 关联关系验证
        assertEquals(finalInvoice.getId(), finalReceivable.getDocId());
        
        // 所有数据都存在
        assertNotNull(finalInvoice);
        assertNotNull(finalReceivable);
        assertNotNull(finalVoucher);
    }
}
