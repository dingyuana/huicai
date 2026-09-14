package com.huicai.sme.tax.service.impl;

import com.huicai.base.business.entity.BusinessDocEntity;
import com.huicai.base.business.entity.InputInvoiceEntity;
import com.huicai.base.business.mapper.BusinessDocEntryMapper;
import com.huicai.base.business.mapper.BusinessDocMapper;
import com.huicai.base.business.mapper.InputInvoiceMapper;
import com.huicai.base.business.util.ColumnMappingResolver;
import com.huicai.base.masterdata.entity.VendorEntity;
import com.huicai.base.masterdata.mapper.VendorMapper;
import com.huicai.base.system.entity.Subject;
import com.huicai.base.system.mapper.SubjectMapper;
import com.huicai.base.voucher.entity.VoucherEntity;
import com.huicai.base.voucher.entity.VoucherEntryEntity;
import com.huicai.base.voucher.mapper.VoucherEntryMapper;
import com.huicai.base.voucher.mapper.VoucherMapper;
import com.huicai.base.voucher.service.VoucherNoService;
import com.huicai.common.exception.BusinessException;
import com.huicai.sme.tax.constant.InvoiceStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

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
    @Mock private ColumnMappingResolver columnMappingResolver;
    @Mock private InvoiceDedupUtil invoiceDedupUtil;

    @Captor private ArgumentCaptor<InputInvoiceEntity> invoiceCaptor;
    @Captor private ArgumentCaptor<VoucherEntryEntity> entryCaptor;

    private InputInvoiceImportService service;

    @BeforeEach
    void setUp() {
        service = new InputInvoiceImportService(
                docMapper, docEntryMapper, voucherMapper, voucherEntryMapper,
                voucherNoService, vendorMapper, subjectMapper, inputInvoiceMapper,
                columnMappingResolver, invoiceDedupUtil);
    }

    private InputInvoiceImportService.ParsedInputInvoiceRow stubRow(int rowNum, String invoiceNo,
                                                                     String sellerTaxId, String sellerName) {
        InputInvoiceImportService.ParsedInputInvoiceRow r = new InputInvoiceImportService.ParsedInputInvoiceRow();
        r.rowNum = rowNum;
        r.invoiceNo = invoiceNo;
        r.sellerTaxId = sellerTaxId;
        r.sellerName = sellerName;
        r.invoiceDate = LocalDate.of(2026, 6, 15);
        r.amount = new BigDecimal("1000");
        r.taxAmount = new BigDecimal("130");
        r.totalAmount = new BigDecimal("1130");
        r.isPositive = true;
        return r;
    }

    private VendorEntity stubVendor(Long id, String name, String taxNo) {
        VendorEntity v = new VendorEntity();
        v.setId(id);
        v.setName(name);
        v.setTaxNo(taxNo);
        return v;
    }

    // ==================== parseInvoiceDate ====================

    @Test
    @DisplayName("parseInvoiceDate: yyyy-MM-dd 格式解析成功")
    void parseInvoiceDate_yyyyMMdd_成功() {
        LocalDate d = service.parseInvoiceDate("2026-06-15");
        assertEquals(LocalDate.of(2026, 6, 15), d);
    }

    @Test
    @DisplayName("parseInvoiceDate: yyyy/MM/dd 格式解析成功")
    void parseInvoiceDate_yyyySlashMMdd_成功() {
        LocalDate d = service.parseInvoiceDate("2026/06/15");
        assertEquals(LocalDate.of(2026, 6, 15), d);
    }

    @Test
    @DisplayName("parseInvoiceDate: yyyyMMdd 格式解析成功")
    void parseInvoiceDate_yyyyMMddCompact_成功() {
        LocalDate d = service.parseInvoiceDate("20260615");
        assertEquals(LocalDate.of(2026, 6, 15), d);
    }

    @Test
    @DisplayName("parseInvoiceDate: yyyy.MM.dd 格式解析成功")
    void parseInvoiceDate_yyyyDotMMdd_成功() {
        LocalDate d = service.parseInvoiceDate("2026.06.15");
        assertEquals(LocalDate.of(2026, 6, 15), d);
    }

    @Test
    @DisplayName("parseInvoiceDate: 空字符串返回 null")
    void parseInvoiceDate_空白_返回null() {
        assertNull(service.parseInvoiceDate(""));
        assertNull(service.parseInvoiceDate(null));
        assertNull(service.parseInvoiceDate("   "));
    }

    @Test
    @DisplayName("parseInvoiceDate: 无效格式返回 null")
    void parseInvoiceDate_无效格式_返回null() {
        assertNull(service.parseInvoiceDate("2026-13-01"));
        assertNull(service.parseInvoiceDate("not-a-date"));
        assertNull(service.parseInvoiceDate("06-15-2026"));
    }

    // ==================== matchOrCreateVendor ====================

    @Test
    @DisplayName("matchOrCreateVendor: 按税号匹配成功返回 ID")
    void matchOrCreateVendor_按税号匹配_返回ID() {
        InputInvoiceImportService.ParsedInputInvoiceRow row =
                stubRow(1, "INV001", "91110000ABC", "供应商A");
        when(vendorMapper.selectList(any())).thenReturn(List.of(stubVendor(5L, "供应商A", "91110000ABC")));

        Long id = service.matchOrCreateVendor(row);
        assertEquals(5L, id);
        verify(vendorMapper, never()).insert(any(VendorEntity.class));
    }

    @Test
    @DisplayName("matchOrCreateVendor: 按名称匹配成功返回 ID")
    void matchOrCreateVendor_按名称匹配_返回ID() {
        InputInvoiceImportService.ParsedInputInvoiceRow row =
                stubRow(1, "INV001", null, "供应商A");
        when(vendorMapper.selectList(any())).thenReturn(List.of(stubVendor(7L, "供应商A", null)));

        Long id = service.matchOrCreateVendor(row);
        assertEquals(7L, id);
        verify(vendorMapper, never()).insert(any(VendorEntity.class));
    }

    @Test
    @DisplayName("matchOrCreateVendor: 短名匹配成功返回 ID")
    void matchOrCreateVendor_短名匹配_返回ID() {
        // "ABC（北京）科技" → "ABC科技" 长度 >= 4
        InputInvoiceImportService.ParsedInputInvoiceRow row =
                stubRow(1, "INV001", null, "ABC（北京）科技");
        when(vendorMapper.selectList(any()))
                .thenReturn(List.of())                          // 全名未匹配
                .thenReturn(List.of(stubVendor(9L, "ABC科技", null))); // 短名匹配

        Long id = service.matchOrCreateVendor(row);
        assertEquals(9L, id);
        verify(vendorMapper, never()).insert(any(VendorEntity.class));
    }

    @Test
    @DisplayName("matchOrCreateVendor: 全无匹配则创建新供应商")
    void matchOrCreateVendor_创建新供应商() {
        InputInvoiceImportService.ParsedInputInvoiceRow row =
                stubRow(1, "INV001", null, "新供应商X");
        when(vendorMapper.selectList(any())).thenReturn(List.of());
        when(vendorMapper.insert(any(VendorEntity.class))).thenAnswer(inv -> {
            VendorEntity v = inv.getArgument(0);
            v.setId(123L);
            return 1;
        });

        Long id = service.matchOrCreateVendor(row);
        assertNotNull(id);
        assertEquals(123L, id);
        verify(vendorMapper, atLeastOnce()).insert(any(VendorEntity.class));
    }

    @Test
    @DisplayName("matchOrCreateVendor: 税号和名称都为空返回 null")
    void matchOrCreateVendor_税号和名称都为空_返回null() {
        InputInvoiceImportService.ParsedInputInvoiceRow row =
                stubRow(1, "INV001", null, null);
        row.sellerTaxId = null;
        row.sellerName = null;

        Long id = service.matchOrCreateVendor(row);
        assertNull(id);
        verify(vendorMapper, never()).selectList(any());
        verify(vendorMapper, never()).insert(any(VendorEntity.class));
    }

    // ==================== insertInputInvoice ====================

    @Test
    @DisplayName("insertInputInvoice: 正确设置发票字段和状态")
    void insertInputInvoice_正确设置字段和状态() {
        InputInvoiceImportService.ParsedInputInvoiceRow row =
                stubRow(1, "INV001", "91110000ABC", "供应商A");
        row.goodsName = "办公用品";
        row.taxRate = new BigDecimal("13");
        Long vendorId = 5L;
        String period = "202606";

        service.insertInputInvoice(row, vendorId, period);

        verify(inputInvoiceMapper).insert(invoiceCaptor.capture());
        InputInvoiceEntity inv = invoiceCaptor.getValue();

        assertEquals("INV001", inv.getInvoiceNo());
        assertEquals(LocalDate.of(2026, 6, 15), inv.getInvoiceDate());
        assertEquals("202606", inv.getPeriod());
        assertEquals(vendorId, inv.getVendorId());
        assertEquals("供应商A", inv.getVendorName());
        assertEquals(0, new BigDecimal("1000").compareTo(inv.getAmount()));
        assertEquals(0, new BigDecimal("1000").compareTo(inv.getAmountExTax()));
        assertEquals(0, new BigDecimal("130").compareTo(inv.getTaxAmount()));
        assertEquals(0, new BigDecimal("1130").compareTo(inv.getTotalAmount()));
        assertEquals(0, new BigDecimal("13").compareTo(inv.getTaxRate()));
        assertEquals(InvoiceStatus.PENDING_CONFIRM, inv.getStatus());
        assertEquals("UNCERTIFIED", inv.getCertificationStatus());
        assertEquals("SPECIAL", inv.getInvoiceType());
        assertEquals("PENDING", inv.getProcessStatus());
    }

    @Test
    @DisplayName("insertInputInvoice: taxRate 为 null 时默认 0")
    void insertInputInvoice_taxRate为null_默认0() {
        InputInvoiceImportService.ParsedInputInvoiceRow row =
                stubRow(1, "INV001", "91110000ABC", "供应商A");
        row.taxRate = null;

        service.insertInputInvoice(row, 5L, "202606");

        verify(inputInvoiceMapper).insert(invoiceCaptor.capture());
        assertEquals(0, BigDecimal.ZERO.compareTo(invoiceCaptor.getValue().getTaxRate()));
    }

    // ==================== confirmImport ====================

    @Test
    @DisplayName("confirmImport: batchId 为空抛异常")
    void confirmImport_batchId为空_抛异常() {
        assertThrows(BusinessException.class, () -> service.confirmImport(null));
        assertThrows(BusinessException.class, () -> service.confirmImport(""));
        assertThrows(BusinessException.class, () -> service.confirmImport("   "));
    }

    @Test
    @DisplayName("confirmImport: batchId 不存在抛异常")
    void confirmImport_batchId不存在_抛异常() {
        assertThrows(BusinessException.class, () -> service.confirmImport("non-existent-batch-id"));
    }

    @Test
    @DisplayName("confirmImport: 空批次返回成功 0")
    void confirmImport_空批次_返回成功0() throws Exception {
        Field cacheField = InputInvoiceImportService.class.getDeclaredField("batchCache");
        cacheField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, List<InputInvoiceImportService.ParsedInputInvoiceRow>> cache =
                (Map<String, List<InputInvoiceImportService.ParsedInputInvoiceRow>>) cacheField.get(service);
        cache.put("empty-batch", Collections.emptyList());

        Map<String, Object> result = service.confirmImport("empty-batch");
        assertEquals(0, result.get("total"));
        assertEquals(0, result.get("success"));
        assertEquals(0, result.get("docCreated"));
        assertEquals(0, result.get("voucherCreated"));
        assertEquals("empty-batch", result.get("batchId"));
    }

    @Test
    @DisplayName("confirmImport: 正常确认导入创建发票")
    void confirmImport_正常导入_创建发票() throws Exception {
        InputInvoiceImportService.ParsedInputInvoiceRow row =
                stubRow(1, "INV001", "91110000ABC", "供应商A");
        row.goodsName = "办公用品";
        row.taxRate = new BigDecimal("13");

        Field cacheField = InputInvoiceImportService.class.getDeclaredField("batchCache");
        cacheField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, List<InputInvoiceImportService.ParsedInputInvoiceRow>> cache =
                (Map<String, List<InputInvoiceImportService.ParsedInputInvoiceRow>>) cacheField.get(service);
        cache.put("batch-1", List.of(row));

        when(invoiceDedupUtil.findExisting(any())).thenReturn(Collections.emptySet());
        when(vendorMapper.selectList(any())).thenReturn(List.of(stubVendor(5L, "供应商A", "91110000ABC")));
        when(inputInvoiceMapper.insert(any(InputInvoiceEntity.class))).thenReturn(1);

        Map<String, Object> result = service.confirmImport("batch-1");
        assertEquals(1, result.get("total"));
        assertEquals(1, result.get("success"));
        assertEquals(0, result.get("duplicateSkipped"));
        assertEquals("batch-1", result.get("batchId"));
        verify(inputInvoiceMapper, times(1)).insert(any(InputInvoiceEntity.class));
        verify(vendorMapper, never()).insert(any(VendorEntity.class));
    }

    @Test
    @DisplayName("confirmImport: 重复发票号跳过")
    void confirmImport_重复发票号_跳过() throws Exception {
        InputInvoiceImportService.ParsedInputInvoiceRow row1 =
                stubRow(1, "INV001", "91110000ABC", "供应商A");
        row1.goodsName = "办公用品";
        InputInvoiceImportService.ParsedInputInvoiceRow row2 =
                stubRow(2, "INV002", "91110000ABC", "供应商A");
        row2.goodsName = "商品B";

        Field cacheField = InputInvoiceImportService.class.getDeclaredField("batchCache");
        cacheField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, List<InputInvoiceImportService.ParsedInputInvoiceRow>> cache =
                (Map<String, List<InputInvoiceImportService.ParsedInputInvoiceRow>>) cacheField.get(service);
        cache.put("batch-dup", List.of(row1, row2));

        // INV001 已存在
        when(invoiceDedupUtil.findExisting(any())).thenReturn(Set.of("INV001"));
        when(vendorMapper.selectList(any())).thenReturn(List.of(stubVendor(5L, "供应商A", "91110000ABC")));
        when(inputInvoiceMapper.insert(any(InputInvoiceEntity.class))).thenReturn(1);

        Map<String, Object> result = service.confirmImport("batch-dup");
        assertEquals(2, result.get("total"));
        assertEquals(1, result.get("success"));
        assertEquals(1, result.get("duplicateSkipped"));
        assertEquals("batch-dup", result.get("batchId"));
        // 只有 INV002 被插入
        verify(inputInvoiceMapper, times(1)).insert(any(InputInvoiceEntity.class));
    }

    // ==================== importInvoices ====================

    @Test
    @DisplayName("importInvoices: 调用 previewInvoices + confirmImport")
    void importInvoices_调用链_正确() {
        InputInvoiceImportService spy = spy(service);

        Map<String, Object> previewResult = new LinkedHashMap<>();
        previewResult.put("batchId", "test-batch");
        previewResult.put("total", 5);
        doReturn(previewResult).when(spy).previewInvoices(any());

        Map<String, Object> confirmResult = new LinkedHashMap<>();
        confirmResult.put("total", 5);
        confirmResult.put("success", 3);
        confirmResult.put("duplicateSkipped", 1);
        confirmResult.put("errors", List.of());
        confirmResult.put("batchId", "test-batch");
        doReturn(confirmResult).when(spy).confirmImport("test-batch");

        MultipartFile mockFile = mock(MultipartFile.class);
        Map<String, Object> result = spy.importInvoices(mockFile);

        assertEquals(5, result.get("total"));
        assertEquals(3, result.get("success"));
        assertEquals(1, result.get("duplicateSkipped"));
        verify(spy, times(1)).previewInvoices(mockFile);
        verify(spy, times(1)).confirmImport("test-batch");
    }

    // ==================== P70 createVoucher ====================

    private Subject stubSubject(Long id, String code, String name, String direction) {
        Subject s = new Subject();
        s.setId(id);
        s.setCode(code);
        s.setName(name);
        s.setDirection(direction);
        s.setIsLeaf(true);
        return s;
    }

    private void stubRequiredSubjects(InputInvoiceImportService spy, Map<String, Subject> configured) {
        doAnswer(inv -> {
            String code = inv.getArgument(0);
            Subject s = configured.get(code);
            if (s == null) {
                throw BusinessException.badRequest("未配置科目 " + code);
            }
            return s;
        }).when(spy).requireSubjectByCode(anyString());
    }

    @Test
    @DisplayName("createVoucher: 借库存商品1405+进项税2221.02，贷应付2202，金额平衡")
    void createVoucher_正确科目与金额_借贷平衡() {
        InputInvoiceImportService spy = spy(service);
        stubRequiredSubjects(spy, Map.of(
                "1405", stubSubject(16L, "1405", "库存商品", "debit"),
                "2221.02", stubSubject(700L, "2221.02", "应交增值税-进项税额", "debit"),
                "2202", stubSubject(44L, "2202", "应付账款", "credit")));
        when(voucherNoService.generateNextNo(any(), anyLong())).thenReturn("FK2026060001");
        when(voucherMapper.insert(any(VoucherEntity.class))).thenAnswer(inv -> {
            inv.getArgument(0, VoucherEntity.class).setId(9001L);
            return 1;
        });

        BusinessDocEntity doc = new BusinessDocEntity();
        var row = stubRow(1, "INV001", "91110000ABC", "供应商A");
        row.goodsName = "采购商品";

        String no = spy.createVoucher(doc, row, 5L, "202606");

        assertEquals("FK2026060001", no);
        verify(spy).requireSubjectByCode("1405");
        verify(spy).requireSubjectByCode("2221.02");
        verify(spy).requireSubjectByCode("2202");
        verify(subjectMapper, never()).insert(any(Subject.class));

        verify(voucherEntryMapper, times(3)).insert(entryCaptor.capture());
        List<VoucherEntryEntity> entries = entryCaptor.getAllValues();
        assertEquals(16L, entries.get(0).getSubjectId());
        assertEquals(0, new BigDecimal("1000").compareTo(entries.get(0).getDebit()));
        assertEquals(0, BigDecimal.ZERO.compareTo(entries.get(0).getCredit()));
        assertEquals(700L, entries.get(1).getSubjectId());
        assertEquals(0, new BigDecimal("130").compareTo(entries.get(1).getDebit()));
        assertEquals(44L, entries.get(2).getSubjectId());
        assertEquals(0, new BigDecimal("1130").compareTo(entries.get(2).getCredit()));
        assertEquals(0, BigDecimal.ZERO.compareTo(entries.get(2).getDebit()));
        assertNotNull(doc.getVoucherId());
    }

    @Test
    @DisplayName("createVoucher: 缺进项税科目2221.02时抛BusinessException且不产生凭证/分录")
    void createVoucher_缺进项税科目_抛异常且无半成品() {
        InputInvoiceImportService spy = spy(service);
        stubRequiredSubjects(spy, Map.of(
                "1405", stubSubject(16L, "1405", "库存商品", "debit"),
                "2202", stubSubject(44L, "2202", "应付账款", "credit")));

        BusinessDocEntity doc = new BusinessDocEntity();
        var row = stubRow(1, "INV001", "91110000ABC", "供应商A");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> spy.createVoucher(doc, row, 5L, "202606"));
        assertTrue(ex.getMessage().contains("2221.02"));
        verify(subjectMapper, never()).insert(any(Subject.class));
        verify(voucherMapper, never()).insert(any(VoucherEntity.class));
        verify(voucherEntryMapper, never()).insert(any(VoucherEntryEntity.class));
        verify(docMapper, never()).updateById(any(BusinessDocEntity.class));
        assertNull(doc.getVoucherId());
    }

    @Test
    @DisplayName("createVoucher: 缺库存商品1405时抛BusinessException且不自动建科目")
    void createVoucher_缺库存商品科目_抛异常() {
        InputInvoiceImportService spy = spy(service);
        stubRequiredSubjects(spy, Map.of(
                "2221.02", stubSubject(700L, "2221.02", "应交增值税-进项税额", "debit"),
                "2202", stubSubject(44L, "2202", "应付账款", "credit")));

        BusinessDocEntity doc = new BusinessDocEntity();
        var row = stubRow(1, "INV001", "91110000ABC", "供应商A");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> spy.createVoucher(doc, row, 5L, "202606"));
        assertTrue(ex.getMessage().contains("1405"));
        verify(subjectMapper, never()).insert(any(Subject.class));
        verify(voucherMapper, never()).insert(any(VoucherEntity.class));
    }

    @Test
    @DisplayName("requireSubjectByCode: 科目不存在时抛BusinessException且不自动建科目")
    void requireSubjectByCode_不存在_抛异常不自动建() {
        when(subjectMapper.selectList(any())).thenReturn(List.of());
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.requireSubjectByCode("2221.02"));
        assertTrue(ex.getMessage().contains("2221.02"));
        verify(subjectMapper, never()).insert(any(Subject.class));
    }
}