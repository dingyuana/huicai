package com.huicai.module.finance.service.impl;

import com.huicai.module.arap.entity.PayableEntity;
import com.huicai.module.arap.entity.VendorEntity;
import com.huicai.module.arap.mapper.PayableMapper;
import com.huicai.module.arap.mapper.VendorMapper;
import com.huicai.module.finance.entity.BusinessDocEntity;
import com.huicai.module.finance.mapper.BusinessDocEntryMapper;
import com.huicai.module.finance.mapper.BusinessDocMapper;
import com.huicai.module.finance.mapper.VoucherEntryMapper;
import com.huicai.module.finance.mapper.VoucherMapper;
import com.huicai.module.finance.service.VoucherNoService;
import com.huicai.module.system.entity.Subject;
import com.huicai.module.system.mapper.SubjectMapper;
import com.huicai.module.tax.entity.InputInvoiceEntity;
import com.huicai.module.tax.mapper.InputInvoiceMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * P10-2: 采购发票 Excel 导入单元测试
 */
@ExtendWith(MockitoExtension.class)
class InputInvoiceImportServiceTest {

    @Mock private BusinessDocMapper docMapper;
    @Mock private BusinessDocEntryMapper docEntryMapper;
    @Mock private VoucherMapper voucherMapper;
    @Mock private VoucherEntryMapper voucherEntryMapper;
    @Mock private VoucherNoService voucherNoService;
    @Mock private VendorMapper vendorMapper;
    @Mock private SubjectMapper subjectMapper;
    @Mock private InputInvoiceMapper inputInvoiceMapper;
    @Mock private PayableMapper payableMapper;
    @Mock private ColumnMappingResolver columnMappingResolver;

    @InjectMocks private InputInvoiceImportService service;

    private InputInvoiceImportService.ParsedInputInvoiceRow stubRow(int num, String invoiceNo, String sellerTaxId, String sellerName) {
        InputInvoiceImportService.ParsedInputInvoiceRow r = new InputInvoiceImportService.ParsedInputInvoiceRow();
        r.rowNum = num;
        r.invoiceNo = invoiceNo;
        r.sellerTaxId = sellerTaxId;
        r.sellerName = sellerName;
        r.invoiceDate = LocalDate.of(2026, 6, 15);
        r.goodsName = "测试采购物资A";
        r.amount = new BigDecimal("1000");
        r.taxAmount = new BigDecimal("130");
        r.totalAmount = new BigDecimal("1130");
        r.isPositive = true;
        return r;
    }

    private Subject stubSubject(Long id, String code) {
        Subject s = new Subject();
        s.setId(id);
        s.setCode(code);
        s.setIsLeaf(true);
        return s;
    }

    private VendorEntity stubVendor(Long id, String name, String taxNo) {
        VendorEntity v = new VendorEntity();
        v.setId(id);
        v.setName(name);
        v.setTaxNo(taxNo);
        return v;
    }

    // ==================== P10-2 关键: 应付单自动生成 ====================

    @Test
    void createPayableFromInvoice_正常_插入应付单() throws Exception {
        InputInvoiceImportService.ParsedInputInvoiceRow r = stubRow(1, "INV-IN-001", null, "供应商A");
        BusinessDocEntity doc = new BusinessDocEntity();
        doc.setId(200L);
        doc.setVoucherId(300L);

        Method m = InputInvoiceImportService.class.getDeclaredMethod(
                "createPayableFromInvoice", BusinessDocEntity.class,
                InputInvoiceImportService.ParsedInputInvoiceRow.class, Long.class, String.class);
        m.setAccessible(true);
        m.invoke(service, doc, r, 10L, "202606");

        verify(payableMapper, times(1)).insert(any(PayableEntity.class));
    }

    @Test
    void createPayableFromInvoice_字段正确填充() throws Exception {
        InputInvoiceImportService.ParsedInputInvoiceRow r = stubRow(1, "INV-IN-001", null, "供应商A");
        BusinessDocEntity doc = new BusinessDocEntity();
        doc.setId(200L);
        doc.setVoucherId(300L);

        Method m = InputInvoiceImportService.class.getDeclaredMethod(
                "createPayableFromInvoice", BusinessDocEntity.class,
                InputInvoiceImportService.ParsedInputInvoiceRow.class, Long.class, String.class);
        m.setAccessible(true);
        m.invoke(service, doc, r, 10L, "202606");

        org.mockito.ArgumentCaptor<PayableEntity> captor =
                org.mockito.ArgumentCaptor.forClass(PayableEntity.class);
        verify(payableMapper).insert(captor.capture());
        PayableEntity captured = captor.getValue();
        assertEquals(10L, captured.getVendorId());
        assertEquals(200L, captured.getDocId());
        assertEquals(300L, captured.getVoucherId());
        assertEquals("202606", captured.getPeriod());
        assertEquals(LocalDate.of(2026, 6, 15), captured.getTxDate());
        assertEquals(0, captured.getAmount().compareTo(new BigDecimal("1130")));
        assertEquals(0, captured.getSettledAmount().compareTo(BigDecimal.ZERO));
        assertEquals(0, captured.getUnsettledAmount().compareTo(new BigDecimal("1130")));
        assertEquals("测试采购物资A", captured.getSummary());
    }

    @Test
    void createPayableFromInvoice_未结清等于总额() throws Exception {
        InputInvoiceImportService.ParsedInputInvoiceRow r = stubRow(1, "INV-IN-001", null, "供应商A");
        BusinessDocEntity doc = new BusinessDocEntity();
        doc.setId(200L);
        doc.setVoucherId(300L);

        Method m = InputInvoiceImportService.class.getDeclaredMethod(
                "createPayableFromInvoice", BusinessDocEntity.class,
                InputInvoiceImportService.ParsedInputInvoiceRow.class, Long.class, String.class);
        m.setAccessible(true);
        m.invoke(service, doc, r, 10L, "202606");

        org.mockito.ArgumentCaptor<PayableEntity> captor =
                org.mockito.ArgumentCaptor.forClass(PayableEntity.class);
        verify(payableMapper).insert(captor.capture());
        PayableEntity captured = captor.getValue();
        // 关键: unsettled = amount, settled = 0
        assertEquals(captured.getAmount().compareTo(captured.getUnsettledAmount()), 0);
        assertEquals(0, captured.getSettledAmount().compareTo(BigDecimal.ZERO));
    }

    // ==================== matchOrCreateVendor 反射测试 ====================

    @Test
    void matchOrCreateVendor_税号匹配上_返回ID() throws Exception {
        InputInvoiceImportService.ParsedInputInvoiceRow r = stubRow(1, null, "91110000XYZ", "供应商A");
        when(vendorMapper.selectList(any())).thenReturn(List.of(stubVendor(5L, "供应商A", "91110000XYZ")));

        Method m = InputInvoiceImportService.class.getDeclaredMethod(
                "matchOrCreateVendor", InputInvoiceImportService.ParsedInputInvoiceRow.class);
        m.setAccessible(true);
        Long id = (Long) m.invoke(service, r);
        assertEquals(5L, id);
        verify(vendorMapper, never()).insert(any(VendorEntity.class));
    }

    @Test
    void matchOrCreateVendor_名称匹配上_返回ID() throws Exception {
        InputInvoiceImportService.ParsedInputInvoiceRow r = stubRow(1, null, null, "供应商A");
        when(vendorMapper.selectList(any())).thenReturn(List.of(stubVendor(7L, "供应商A", null)));

        Method m = InputInvoiceImportService.class.getDeclaredMethod(
                "matchOrCreateVendor", InputInvoiceImportService.ParsedInputInvoiceRow.class);
        m.setAccessible(true);
        Long id = (Long) m.invoke(service, r);
        assertEquals(7L, id);
    }

    @Test
    void matchOrCreateVendor_短名匹配上_返回ID() throws Exception {
        // "ABC（北京）科技" → "ABC科技" 5 字 ≥ 4
        InputInvoiceImportService.ParsedInputInvoiceRow r = stubRow(1, null, null, "ABC（北京）科技");
        when(vendorMapper.selectList(any()))
                .thenReturn(List.of())  // 全名
                .thenReturn(List.of(stubVendor(9L, "ABC科技", null)));  // 短名

        Method m = InputInvoiceImportService.class.getDeclaredMethod(
                "matchOrCreateVendor", InputInvoiceImportService.ParsedInputInvoiceRow.class);
        m.setAccessible(true);
        Long id = (Long) m.invoke(service, r);
        assertEquals(9L, id);
    }

    @Test
    void matchOrCreateVendor_全无匹配_创建供应商() throws Exception {
        InputInvoiceImportService.ParsedInputInvoiceRow r = stubRow(1, null, null, "新供应商X");
        when(vendorMapper.selectList(any())).thenReturn(List.of());  // 3 次都空
        when(vendorMapper.insert(any(VendorEntity.class))).thenAnswer(inv -> {
            VendorEntity v = inv.getArgument(0);
            v.setId(456L);
            return 1;
        });

        Method m = InputInvoiceImportService.class.getDeclaredMethod(
                "matchOrCreateVendor", InputInvoiceImportService.ParsedInputInvoiceRow.class);
        m.setAccessible(true);
        Long id = (Long) m.invoke(service, r);
        assertNotNull(id);
        assertEquals(456L, id);
        verify(vendorMapper, atLeastOnce()).insert(any(VendorEntity.class));
    }

    @Test
    void matchOrCreateVendor_名称和税号全空_返回null() throws Exception {
        InputInvoiceImportService.ParsedInputInvoiceRow r = stubRow(1, null, null, null);

        Method m = InputInvoiceImportService.class.getDeclaredMethod(
                "matchOrCreateVendor", InputInvoiceImportService.ParsedInputInvoiceRow.class);
        m.setAccessible(true);
        Long id = (Long) m.invoke(service, r);
        assertNull(id);
        verifyNoInteractions(vendorMapper);
    }

    // ==================== findExistingInvoiceNos 反射测试 ====================

    @Test
    void findExistingInvoiceNos_3个发票号_2个已存在_返回2() throws Exception {
        InputInvoiceImportService.ParsedInputInvoiceRow r1 = stubRow(1, "INV-IN-001", null, null);
        InputInvoiceImportService.ParsedInputInvoiceRow r2 = stubRow(2, "INV-IN-002", null, null);
        InputInvoiceImportService.ParsedInputInvoiceRow r3 = stubRow(3, "INV-IN-003", null, null);

        InputInvoiceEntity inv1 = new InputInvoiceEntity();
        inv1.setInvoiceNo("INV-IN-001");
        InputInvoiceEntity inv2 = new InputInvoiceEntity();
        inv2.setInvoiceNo("INV-IN-002");

        when(inputInvoiceMapper.selectList(any())).thenReturn(List.of(inv1, inv2));

        Method m = InputInvoiceImportService.class.getDeclaredMethod("findExistingInvoiceNos", List.class);
        m.setAccessible(true);
        @SuppressWarnings("unchecked")
        Set<String> result = (Set<String>) m.invoke(service, List.of(r1, r2, r3));
        assertEquals(2, result.size());
        assertTrue(result.contains("INV-IN-001"));
        assertTrue(result.contains("INV-IN-002"));
    }

    @Test
    void findExistingInvoiceNos_空发票号_返回空集() throws Exception {
        InputInvoiceImportService.ParsedInputInvoiceRow r1 = stubRow(1, null, null, null);
        InputInvoiceImportService.ParsedInputInvoiceRow r2 = stubRow(2, "", null, null);
        Method m = InputInvoiceImportService.class.getDeclaredMethod("findExistingInvoiceNos", List.class);
        m.setAccessible(true);
        @SuppressWarnings("unchecked")
        Set<String> result = (Set<String>) m.invoke(service, List.of(r1, r2));
        assertTrue(result.isEmpty());
        verifyNoInteractions(inputInvoiceMapper);
    }

    // ==================== createBusinessDoc 反射测试 ====================

    @Test
    void createBusinessDoc_正常_插入单据_类型INVOICE_IN() throws Exception {
        InputInvoiceImportService.ParsedInputInvoiceRow r = stubRow(1, "INV-IN-001", null, "供应商A");
        Method m = InputInvoiceImportService.class.getDeclaredMethod(
                "createBusinessDoc", InputInvoiceImportService.ParsedInputInvoiceRow.class, Long.class, String.class);
        m.setAccessible(true);
        BusinessDocEntity doc = (BusinessDocEntity) m.invoke(service, r, 10L, "202606");

        verify(docMapper, times(1)).insert(any(BusinessDocEntity.class));
        assertEquals("INVOICE_IN", doc.getDocType());
        assertEquals(10L, doc.getSupplierId());
        assertEquals(0, doc.getAmount().compareTo(new BigDecimal("1130")));
        assertEquals("DRAFT", doc.getStatus());
        assertEquals("INVOICE_IMPORT", doc.getSource());
    }

    // ==================== insertInputInvoice 反射测试 ====================

    @Test
    void insertInputInvoice_正常_插入进项发票_关联doc和voucher() throws Exception {
        InputInvoiceImportService.ParsedInputInvoiceRow r = stubRow(1, "INV-IN-001", null, "供应商A");
        BusinessDocEntity doc = new BusinessDocEntity();
        doc.setId(200L);
        doc.setVoucherId(300L);

        Method m = InputInvoiceImportService.class.getDeclaredMethod(
                "insertInputInvoice", InputInvoiceImportService.ParsedInputInvoiceRow.class, Long.class, String.class, BusinessDocEntity.class);
        m.setAccessible(true);
        m.invoke(service, r, 10L, "202606", doc);

        org.mockito.ArgumentCaptor<InputInvoiceEntity> captor =
                org.mockito.ArgumentCaptor.forClass(InputInvoiceEntity.class);
        verify(inputInvoiceMapper).insert(captor.capture());
        InputInvoiceEntity captured = captor.getValue();
        assertEquals("INV-IN-001", captured.getInvoiceNo());
        assertEquals(10L, captured.getVendorId());
        assertEquals("供应商A", captured.getVendorName());
        assertEquals(200L, captured.getDocId());
        assertEquals(300L, captured.getVoucherId());
        assertEquals("PENDING", captured.getCertificationStatus());
    }

    // ==================== parseInvoiceDate 反射测试 ====================

    @Test
    void parseInvoiceDate_yyyyMMdd_正确解析() throws Exception {
        Method m = InputInvoiceImportService.class.getDeclaredMethod("parseInvoiceDate", String.class);
        m.setAccessible(true);
        LocalDate d = (LocalDate) m.invoke(service, "20260615");
        assertEquals(LocalDate.of(2026, 6, 15), d);
    }

    @Test
    void parseInvoiceDate_ISO_正确解析() throws Exception {
        Method m = InputInvoiceImportService.class.getDeclaredMethod("parseInvoiceDate", String.class);
        m.setAccessible(true);
        LocalDate d = (LocalDate) m.invoke(service, "2026-06-15");
        assertEquals(LocalDate.of(2026, 6, 15), d);
    }

    @Test
    void parseInvoiceDate_空白_返回null() throws Exception {
        Method m = InputInvoiceImportService.class.getDeclaredMethod("parseInvoiceDate", String.class);
        m.setAccessible(true);
        LocalDate d = (LocalDate) m.invoke(service, "");
        assertNull(d);
    }
}
