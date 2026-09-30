package com.huicai.sme.tax.mapper;

import com.huicai.base.business.entity.BusinessDocEntity;
import com.huicai.base.business.entity.InputInvoiceEntity;
import com.huicai.common.context.EnterpriseContextHolder;
import com.huicai.common.test.AbstractMapperTest;
import com.huicai.sme.tax.dto.vo.InvoiceReconcileVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P58: 发票-收付款勾稽 Mapper 真实 DB 测试（SQL CASE 逻辑验证）.
 * 慢测试：需 Docker，本地跳过，CI 全量执行。
 */
class InvoicePaymentReconcileMapperRealDBTest extends AbstractMapperTest {

    @Autowired
    private InvoicePaymentReconcileMapper reconcileMapper;
    @Autowired
    private com.huicai.base.business.mapper.InputInvoiceMapper inputInvoiceMapper;
    @Autowired
    private com.huicai.base.business.mapper.BusinessDocMapper businessDocMapper;

    /**
     * 勾稽查询经 EnterpriseDataPermissionInterceptor 注入 enterprise_id 条件，
     * 且 MyMetaObjectHandler 也从上下文回填 enterprise_id。测试需模拟真实请求上下文。
     */
    @BeforeEach
    void setUp() {
        EnterpriseContextHolder.set(1L);
    }

    @AfterEach
    void tearDown() {
        EnterpriseContextHolder.clear();
    }

    /**
     * t_input_invoice.id 是 {@code GENERATED ALWAYS AS IDENTITY}，禁止显式赋值
     * （P99 D 类：ERROR: cannot insert a non-DEFAULT value into column "id"）。
     * 由 DB 分配 id，insert 后回填到实体。
     *
     * @param seq      测试序号，仅用于生成可读且唯一的 invoice_no
     * @param total    价税合计
     * @param cert     认证状态
     * @param declared 申报状态
     */
    private InputInvoiceEntity invoice(String seq, Long vendorId, BigDecimal total, String cert, String declared) {
        InputInvoiceEntity e = new InputInvoiceEntity();
        e.setInvoiceNo("INV-RDB-" + seq);
        e.setInvoiceDate(LocalDate.of(2026, 8, 1));
        e.setPeriod("202608");
        e.setVendorId(vendorId);
        e.setVendorName("供应商A");
        e.setAmount(total);
        e.setTaxRate(new BigDecimal("13"));
        e.setTaxAmount(total.multiply(new BigDecimal("0.13")));
        e.setTotalAmount(total);
        e.setInvoiceType("SPECIAL");
        e.setCertificationStatus(cert);
        e.setDeclaredStatus(declared);
        e.setStatus("VOUCHERED");
        e.setCreatedBy(1L);
        e.setDeleted(0);
        e.setEnterpriseId(1L); // V103 起 NOT NULL 且无 DB 默认值
        return e;
    }

    private BusinessDocEntity doc(String seq, Long invoiceId, BigDecimal settled) {
        BusinessDocEntity d = new BusinessDocEntity();
        d.setDocNo("DOC-RDB-" + seq);
        d.setDocType("INVOICE_IN");
        d.setDocDate(LocalDate.of(2026, 8, 1));
        d.setPeriod("202608");
        d.setAmount(settled);
        d.setStatus("VOUCHERED");
        d.setSummary("供应商A");
        d.setInvoiceNo("INV-RDB-" + seq);
        d.setInvoiceId(invoiceId);
        d.setSettledAmount(settled);
        d.setUnsettledAmount(BigDecimal.ZERO);
        d.setCreatedBy(1L);
        d.setSubmittedBy(1L);
        d.setDeleted(0);
        d.setEnterpriseId(1L); // V102 起 NOT NULL 且无 DB 默认值
        return d;
    }

    @Test
    void reconcile_partialPayment_marksPartial() {
        // 勾稽 SQL 按 vendor_id 过滤，需用真实存在的供应商
        Long vendorId = ensureVendor(1L);
        InputInvoiceEntity e = invoice("901", vendorId, new BigDecimal("1000.00"), "CERTIFIED", "DECLARED");
        inputInvoiceMapper.insert(e);
        // id 由 DB 分配，单据必须用真实 id 才能被勾稽 SQL JOIN 到
        businessDocMapper.insert(doc("901", e.getId(), new BigDecimal("600.00")));

        List<InvoiceReconcileVO> r = reconcileMapper.queryInputReconcile("202608", vendorId);
        assertEquals(1, r.size());
        assertEquals("PARTIAL", r.get(0).getReconcileStatus());
        assertEquals(new BigDecimal("400.00"), r.get(0).getUnpaidAmount());
    }

    @Test
    void reconcile_fullyPaid_marksPaid() {
        Long vendorId = ensureVendor(1L);
        InputInvoiceEntity e = invoice("902", vendorId, new BigDecimal("1000.00"), "CERTIFIED", "DECLARED");
        inputInvoiceMapper.insert(e);
        businessDocMapper.insert(doc("902", e.getId(), new BigDecimal("1000.00")));

        List<InvoiceReconcileVO> r = reconcileMapper.queryInputReconcile("202608", vendorId);
        assertEquals("PAID", r.get(0).getReconcileStatus());
    }

    @Test
    void reconcile_unpaid_noDoc_marksUnpaid() {
        // 发票无关联业务单 → paidAmount=0
        Long vendorId = ensureVendor(1L);
        InputInvoiceEntity e = invoice("903", vendorId, new BigDecimal("1000.00"), "UNCERTIFIED", "UNDECLARED");
        inputInvoiceMapper.insert(e);

        List<InvoiceReconcileVO> r = reconcileMapper.queryInputReconcile("202608", vendorId);
        assertEquals(1, r.size());
        assertEquals("UNPAID", r.get(0).getReconcileStatus());
        // paidAmount 来自业务单 settled_amount，无单据时为 0；未付款额才是票面全额
        assertEquals(0, new BigDecimal("0").compareTo(r.get(0).getPaidAmount()));
        assertEquals(new BigDecimal("1000.00"), r.get(0).getUnpaidAmount());
    }

    @Test
    void reconcile_nullFilters_doNotFail() {
        Long vendorId = ensureVendor(1L);
        inputInvoiceMapper.insert(invoice("904", vendorId, new BigDecimal("800.00"), "CERTIFIED", "DECLARED"));

        List<InvoiceReconcileVO> r = reconcileMapper.queryInputReconcile(null, null);
        assertFalse(r.isEmpty(), "NULL 筛选应返回全部进项发票而非报错");
        assertTrue(r.stream().anyMatch(v -> "INV-RDB-904".equals(v.getInvoiceNo())));
    }
}
