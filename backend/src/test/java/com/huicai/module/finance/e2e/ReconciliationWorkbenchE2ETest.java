package com.huicai.module.finance.e2e;

import com.huicai.common.test.AbstractMapperTest;
import com.huicai.module.arap.entity.CustomerEntity;
import com.huicai.module.arap.entity.ReconciliationLogEntity;
import com.huicai.module.arap.mapper.CustomerMapper;
import com.huicai.module.arap.mapper.ReconciliationLogMapper;
import com.huicai.module.arap.service.ArapSettlementService;
import com.huicai.module.arap.service.impl.ReconciliationServiceImpl;
import com.huicai.module.finance.entity.BusinessDocEntity;
import com.huicai.module.finance.mapper.BusinessDocMapper;
import com.huicai.module.finance.service.BusinessDocService;
import com.huicai.module.finance.dto.BusinessDocQueryDTO;
import com.huicai.module.finance.dto.BusinessDocVO;
import com.huicai.module.system.entity.Subject;
import com.huicai.module.system.mapper.SubjectMapper;
import com.huicai.module.tax.entity.OutputInvoiceEntity;
import com.huicai.module.tax.mapper.OutputInvoiceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 核销工作台 E2E 链路测试.
 *
 * 验证：销项发票导入 → INVOICE_OUT 单据创建 → 核销工作台 pageQuery 可见
 * 覆盖 P40 修复的 unsettledAmount 逻辑 + 工作台查询条件
 */
public class ReconciliationWorkbenchE2ETest extends AbstractMapperTest {

    @Autowired
    private OutputInvoiceMapper outputInvoiceMapper;

    @Autowired
    private BusinessDocMapper businessDocMapper;

    @Autowired
    private BusinessDocService businessDocService;

    @Autowired
    private CustomerMapper customerMapper;

    @Autowired
    private SubjectMapper subjectMapper;

    @Autowired
    private ReconciliationServiceImpl reconciliationService;

    @Autowired
    private ReconciliationLogMapper reconciliationLogMapper;

    @Autowired
    private ArapSettlementService arapSettlementService;

    private Long testCustomerId;

    @BeforeEach
    void setupTestData() {
        // 创建测试科目
        Subject subject = new Subject();
        subject.setCode("9999.E2E.RECON");
        subject.setName("核销测试科目");
        subject.setDirection("debit");
        subject.setLevel(1);
        subject.setIsActive(true);
        subject.setIsLeaf(true);
        subject.setDeleted(0);
        subjectMapper.insert(subject);

        // 创建测试客户
        CustomerEntity customer = new CustomerEntity();
        customer.setCode("C-E2E-RECON-001");
        customer.setName("核销E2E测试客户");
        customer.setContactPerson("张三");
        customer.setPhone("13800138000");
        customer.setEmail("e2e@test.com");
        customer.setAddress("测试地址");
        customer.setTaxNo("91110101MB99999999");
        customer.setBankName("测试银行");
        customer.setBankAccount("1111111111");
        customer.setCreditLimit(new BigDecimal("500000.00"));
        customer.setCreditDays(30);
        customer.setSubjectId(subject.getId());
        customer.setIsActive(true);
        customer.setDeleted(0);
        customerMapper.insert(customer);
        testCustomerId = customer.getId();
    }

    @Test
    void fullChain_invoiceToWorkbench_shouldShowUnsettledDoc() {
        // 1. 创建销项发票
        OutputInvoiceEntity invoice = new OutputInvoiceEntity();
        invoice.setInvoiceNo("INV-E2E-RECON-001");
        invoice.setInvoiceDate(LocalDate.of(2026, 7, 8));
        invoice.setPeriod("202607");
        invoice.setCustomerId(testCustomerId);
        invoice.setCustomerName("核销E2E测试客户");
        invoice.setAmount(new BigDecimal("1950.00"));
        invoice.setTaxRate(new BigDecimal("0.13"));
        invoice.setTaxAmount(new BigDecimal("253.50"));
        invoice.setTotalAmount(new BigDecimal("2203.50"));
        invoice.setInvoiceType("SPECIAL");
        invoice.setStatus("PENDING_CONFIRM");
        invoice.setCreatedBy(1L);
        invoice.setDeleted(0);
        outputInvoiceMapper.insert(invoice);
        assertNotNull(invoice.getId());

        // 2. 创建 INVOICE_OUT 业务单据（模拟发票审核后自动创建）
        BusinessDocEntity doc = new BusinessDocEntity();
        doc.setDocNo("YS-E2E-" + System.currentTimeMillis());
        doc.setDocType("INVOICE_OUT");
        doc.setDocDate(LocalDate.of(2026, 7, 8));
        doc.setPeriod("202607");
        doc.setAmount(new BigDecimal("1950.00"));
        doc.setSettledAmount(BigDecimal.ZERO);
        doc.setUnsettledAmount(new BigDecimal("1950.00"));
        doc.setCustomerId(testCustomerId);
        doc.setInvoiceNo(invoice.getInvoiceNo());
        doc.setStatus("DRAFT");
        doc.setSource("INVOICE_IMPORT");
        doc.setCreatedBy(1L);
        businessDocMapper.insert(doc);
        assertNotNull(doc.getId());

        // 3. 验证 pageQuery 在 RECEIPT tab 条件下能查到 INVOICE_OUT 单据
        BusinessDocQueryDTO query = new BusinessDocQueryDTO();
        query.setDocTypes(List.of("RECEIPT", "INVOICE_OUT", "OTHER_RECEIVABLE"));
        query.setCurrent(1);
        query.setSize(20);
        var page = businessDocService.pageQuery(query);

        assertNotNull(page);
        assertTrue(page.getTotal() > 0, "核销工作台应查出 INVOICE_OUT 单据");

        // 4. 验证返回的记录包含 INVOICE_OUT 类型
        boolean hasInvoiceOut = page.getRecords().stream()
                .anyMatch(v -> "INVOICE_OUT".equals(v.getDocType())
                        && v.getInvoiceNo() != null
                        && v.getInvoiceNo().equals(invoice.getInvoiceNo()));
        assertTrue(hasInvoiceOut, "pageQuery 应返回新创建的 INVOICE_OUT 单据");

        // 5. 验证 unsettledAmount > 0（工作台只显示未核完的单据）
        BusinessDocVO vo = page.getRecords().stream()
                .filter(v -> "INVOICE_OUT".equals(v.getDocType())
                        && invoice.getInvoiceNo().equals(v.getInvoiceNo()))
                .findFirst().orElse(null);
        assertNotNull(vo);
        assertTrue(vo.getUnsettledAmount() != null
                        && vo.getUnsettledAmount().compareTo(BigDecimal.ZERO) > 0,
                "新单据 unsettledAmount 应大于 0");
        assertEquals(0, new BigDecimal("1950.00").compareTo(vo.getUnsettledAmount()),
                "unsettledAmount 应等于单据金额 1950.00");
    }

    @Test
    void workbenchQuery_shouldReturnBothReceiptAndInvoiceOut() {
        // 1. 创建收款单
        BusinessDocEntity receipt = new BusinessDocEntity();
        receipt.setDocNo("SK-E2E-" + System.currentTimeMillis());
        receipt.setDocType("RECEIPT");
        receipt.setDocDate(LocalDate.of(2026, 7, 8));
        receipt.setPeriod("202607");
        receipt.setAmount(new BigDecimal("5000.00"));
        receipt.setSettledAmount(BigDecimal.ZERO);
        receipt.setUnsettledAmount(new BigDecimal("5000.00"));
        receipt.setCustomerId(testCustomerId);
        receipt.setStatus("DRAFT");
        receipt.setSource("MANUAL");
        receipt.setCreatedBy(1L);
        businessDocMapper.insert(receipt);

        // 2. 创建应收单
        BusinessDocEntity invoiceOut = new BusinessDocEntity();
        invoiceOut.setDocNo("YS-E2E-" + System.currentTimeMillis());
        invoiceOut.setDocType("INVOICE_OUT");
        invoiceOut.setDocDate(LocalDate.of(2026, 7, 8));
        invoiceOut.setPeriod("202607");
        invoiceOut.setAmount(new BigDecimal("3000.00"));
        invoiceOut.setSettledAmount(BigDecimal.ZERO);
        invoiceOut.setUnsettledAmount(new BigDecimal("3000.00"));
        invoiceOut.setCustomerId(testCustomerId);
        invoiceOut.setInvoiceNo("INV-E2E-RECON-002");
        invoiceOut.setStatus("DRAFT");
        invoiceOut.setSource("INVOICE_IMPORT");
        invoiceOut.setCreatedBy(1L);
        businessDocMapper.insert(invoiceOut);

        // 3. 创建其它应收单
        BusinessDocEntity otherReceivable = new BusinessDocEntity();
        otherReceivable.setDocNo("QTYS-E2E-" + System.currentTimeMillis());
        otherReceivable.setDocType("OTHER_RECEIVABLE");
        otherReceivable.setDocDate(LocalDate.of(2026, 7, 8));
        otherReceivable.setPeriod("202607");
        otherReceivable.setAmount(new BigDecimal("2000.00"));
        otherReceivable.setSettledAmount(BigDecimal.ZERO);
        otherReceivable.setUnsettledAmount(new BigDecimal("2000.00"));
        otherReceivable.setCustomerId(testCustomerId);
        otherReceivable.setStatus("DRAFT");
        otherReceivable.setSource("MANUAL");
        otherReceivable.setCreatedBy(1L);
        businessDocMapper.insert(otherReceivable);

        // 4. 创建付款单（不应出现在 RECEIPT tab 查询中）
        BusinessDocEntity payment = new BusinessDocEntity();
        payment.setDocNo("FK-E2E-" + System.currentTimeMillis());
        payment.setDocType("PAYMENT");
        payment.setDocDate(LocalDate.of(2026, 7, 8));
        payment.setPeriod("202607");
        payment.setAmount(new BigDecimal("1000.00"));
        payment.setSettledAmount(BigDecimal.ZERO);
        payment.setUnsettledAmount(new BigDecimal("1000.00"));
        payment.setSupplierId(1L);
        payment.setStatus("DRAFT");
        payment.setSource("MANUAL");
        payment.setCreatedBy(1L);
        businessDocMapper.insert(payment);

        // 5. RECEIPT tab 查询：应返回 3 种应收方向类型
        BusinessDocQueryDTO queryReceipt = new BusinessDocQueryDTO();
        queryReceipt.setDocTypes(List.of("RECEIPT", "INVOICE_OUT", "OTHER_RECEIVABLE"));
        queryReceipt.setCurrent(1);
        queryReceipt.setSize(20);
        var receiptPage = businessDocService.pageQuery(queryReceipt);

        assertTrue(receiptPage.getTotal() >= 3, "RECEIPT tab 应返回所有应收方向单据(>=3)");
        assertEquals(0, receiptPage.getRecords().stream()
                        .filter(v -> "PAYMENT".equals(v.getDocType())).count(),
                "RECEIPT tab 不应包含 PAYMENT 类型单据");

        // 6. PAYMENT tab 查询：应返回应付方向类型
        BusinessDocQueryDTO queryPayment = new BusinessDocQueryDTO();
        queryPayment.setDocTypes(List.of("PAYMENT", "EXPENSE", "INVOICE_IN", "OTHER_PAYABLE"));
        queryPayment.setCurrent(1);
        queryPayment.setSize(20);
        var paymentPage = businessDocService.pageQuery(queryPayment);

        assertTrue(paymentPage.getTotal() >= 1, "PAYMENT tab 应返回应付方向单据(>=1)");
        assertTrue(paymentPage.getRecords().stream()
                        .anyMatch(v -> "PAYMENT".equals(v.getDocType())),
                "PAYMENT tab 应包含 PAYMENT 类型单据");
    }

    @Test
    void executeReconciliation_thenTrace_shouldContainSettlementData() {
        // 1. 创建收款单（来源单据）
        BusinessDocEntity receipt = new BusinessDocEntity();
        receipt.setDocNo("SK-E2E-TRC-" + System.currentTimeMillis());
        receipt.setDocType("RECEIPT");
        receipt.setDocDate(LocalDate.of(2026, 7, 9));
        receipt.setPeriod("202607");
        receipt.setAmount(new BigDecimal("1000.00"));
        receipt.setSettledAmount(BigDecimal.ZERO);
        receipt.setUnsettledAmount(new BigDecimal("1000.00"));
        receipt.setCustomerId(testCustomerId);
        receipt.setStatus("APPROVED");
        receipt.setSource("MANUAL");
        receipt.setCreatedBy(1L);
        businessDocMapper.insert(receipt);

        // 2. 创建应收单（目标单据）
        BusinessDocEntity invoiceOut = new BusinessDocEntity();
        invoiceOut.setDocNo("YS-E2E-TRC-" + System.currentTimeMillis());
        invoiceOut.setDocType("INVOICE_OUT");
        invoiceOut.setDocDate(LocalDate.of(2026, 7, 9));
        invoiceOut.setPeriod("202607");
        invoiceOut.setAmount(new BigDecimal("1000.00"));
        invoiceOut.setSettledAmount(BigDecimal.ZERO);
        invoiceOut.setUnsettledAmount(new BigDecimal("1000.00"));
        invoiceOut.setCustomerId(testCustomerId);
        invoiceOut.setStatus("APPROVED");
        invoiceOut.setSource("INVOICE_IMPORT");
        invoiceOut.setCreatedBy(1L);
        businessDocMapper.insert(invoiceOut);

        // 3. 执行核销
        ReconciliationService.ExecuteRequest executeReq = new ReconciliationService.ExecuteRequest(
                "RECEIPT", receipt.getId(),
                "INVOICE_OUT", invoiceOut.getId(),
                new BigDecimal("500.00"), new BigDecimal("0.95"), "MANUAL",
                testCustomerId, null, "202607", "E2E核销测试");
        ReconciliationLogEntity log = reconciliationService.execute(executeReq);
        assertNotNull(log);
        assertEquals("CONFIRMED", log.getStatus());
        assertEquals("CREATE", log.getOperationType());
        assertNotNull(log.getId());

        // 4. 查询 trace
        var trace = reconciliationService.trace(log.getId());
        assertNotNull(trace);
        assertNotNull(trace.getSettlement());
        assertNotNull(trace.getSettlement().getId(), "trace settlement ID 不应为空");
        assertNotNull(trace.getSettlement().getSettlementNo(), "trace settlementNo 不应为空（period有值时生成核销单）");
        assertNotNull(trace.getUpstream());
        assertNotNull(trace.getDownstream());
        assertFalse(trace.getOperationTrail().isEmpty(), "operationTrail 不应为空");
        assertEquals("CREATE", trace.getOperationTrail().get(0).getOperationType());

        // 5. 验证下游业务单据
        assertNotNull(trace.getDownstream().getBusinessDocs());
        assertFalse(trace.getDownstream().getBusinessDocs().isEmpty());
    }

    @Test
    void executeReconciliation_traceSettlementId_shouldMatchActualSettlement() {
        // 创建核销执行所需数据
        BusinessDocEntity receipt = new BusinessDocEntity();
        receipt.setDocNo("SK-E2E-TRC2-" + System.currentTimeMillis());
        receipt.setDocType("RECEIPT");
        receipt.setDocDate(LocalDate.of(2026, 7, 9));
        receipt.setPeriod("202607");
        receipt.setAmount(new BigDecimal("2000.00"));
        receipt.setSettledAmount(BigDecimal.ZERO);
        receipt.setUnsettledAmount(new BigDecimal("2000.00"));
        receipt.setCustomerId(testCustomerId);
        receipt.setStatus("APPROVED");
        receipt.setSource("MANUAL");
        receipt.setCreatedBy(1L);
        businessDocMapper.insert(receipt);

        BusinessDocEntity invoiceOut = new BusinessDocEntity();
        invoiceOut.setDocNo("YS-E2E-TRC2-" + System.currentTimeMillis());
        invoiceOut.setDocType("INVOICE_OUT");
        invoiceOut.setDocDate(LocalDate.of(2026, 7, 9));
        invoiceOut.setPeriod("202607");
        invoiceOut.setAmount(new BigDecimal("2000.00"));
        invoiceOut.setSettledAmount(BigDecimal.ZERO);
        invoiceOut.setUnsettledAmount(new BigDecimal("2000.00"));
        invoiceOut.setCustomerId(testCustomerId);
        invoiceOut.setStatus("APPROVED");
        invoiceOut.setSource("INVOICE_IMPORT");
        invoiceOut.setCreatedBy(1L);
        businessDocMapper.insert(invoiceOut);

        // 执行核销（带period触发settlement创建）
        ReconciliationService.ExecuteRequest executeReq = new ReconciliationService.ExecuteRequest(
                "RECEIPT", receipt.getId(),
                "INVOICE_OUT", invoiceOut.getId(),
                new BigDecimal("1000.00"), new BigDecimal("0.95"), "MANUAL",
                testCustomerId, null, "202607", "E2E Settlement ID检查");
        ReconciliationLogEntity log = reconciliationService.execute(executeReq);

        // trace 中的 settlement.id 应是真实核销单ID，不是 log.id
        var trace = reconciliationService.trace(log.getId());
        assertNotNull(trace.getSettlement());
        assertNotEquals(log.getId(), trace.getSettlement().getId(),
                "trace settlement.id 不应等于 log.id，应指向真实核销单");
    }
}