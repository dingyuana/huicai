package com.huicai.module.finance.service.impl;

import com.huicai.module.arap.entity.CustomerEntity;
import com.huicai.module.arap.mapper.CustomerMapper;
import com.huicai.module.finance.entity.BusinessDocEntity;
import com.huicai.module.finance.mapper.BusinessDocEntryMapper;
import com.huicai.module.finance.mapper.BusinessDocMapper;
import com.huicai.module.finance.mapper.VoucherEntryMapper;
import com.huicai.module.finance.mapper.VoucherMapper;
import com.huicai.module.finance.service.VoucherNoService;
import com.huicai.module.system.entity.Subject;
import com.huicai.module.system.mapper.SubjectMapper;
import com.huicai.module.tax.mapper.OutputInvoiceMapper;
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

@ExtendWith(MockitoExtension.class)
class SalesInvoiceImportServiceTest {

    @Mock private BusinessDocMapper docMapper;
    @Mock private BusinessDocEntryMapper docEntryMapper;
    @Mock private VoucherMapper voucherMapper;
    @Mock private VoucherEntryMapper voucherEntryMapper;
    @Mock private VoucherNoService voucherNoService;
    @Mock private CustomerMapper customerMapper;
    @Mock private SubjectMapper subjectMapper;
    @Mock private OutputInvoiceMapper outputInvoiceMapper;
    @Mock private ColumnMappingResolver columnMappingResolver;

    @InjectMocks private SalesInvoiceImportService service;

    private SalesInvoiceImportService.ParsedInvoiceRow stubRow(int num, String invoiceNo, String buyerTaxId, String buyerName) {
        SalesInvoiceImportService.ParsedInvoiceRow r = new SalesInvoiceImportService.ParsedInvoiceRow();
        r.rowNum = num;
        r.invoiceNo = invoiceNo;
        r.buyerTaxId = buyerTaxId;
        r.buyerName = buyerName;
        r.invoiceDate = LocalDate.of(2026, 6, 15);
        r.amount = new BigDecimal("1000");
        r.taxAmount = new BigDecimal("130");
        r.totalAmount = new BigDecimal("1130");
        return r;
    }

    private Subject stubSubject(Long id, String code) {
        Subject s = new Subject();
        s.setId(id);
        s.setCode(code);
        s.setIsLeaf(true);
        return s;
    }

    private CustomerEntity stubCustomer(Long id, String name, String taxNo) {
        CustomerEntity c = new CustomerEntity();
        c.setId(id);
        c.setName(name);
        c.setTaxNo(taxNo);
        return c;
    }

    // ==================== ensureStandardSubjects ====================

    @Test
    void ensureStandardSubjects_科目全不存在_插入4次() throws Exception {
        // 4 个 findSubjectByCode 都返回 null
        when(subjectMapper.selectList(any())).thenReturn(List.of());

        Method m = SalesInvoiceImportService.class.getDeclaredMethod("ensureStandardSubjects");
        m.setAccessible(true);
        m.invoke(service);

        // 4 个 ensureSubject 都创建 → 4 次 insert
        verify(subjectMapper, times(4)).insert(any(Subject.class));
    }

    @Test
    void ensureStandardSubjects_科目全存在_不插入() throws Exception {
        // 4 个 code 都已存在
        when(subjectMapper.selectList(any()))
                .thenReturn(List.of(stubSubject(1L, "1122")))
                .thenReturn(List.of(stubSubject(2L, "5001")))
                .thenReturn(List.of(stubSubject(3L, "2221")))
                .thenReturn(List.of(stubSubject(4L, "2221.01")));

        Method m = SalesInvoiceImportService.class.getDeclaredMethod("ensureStandardSubjects");
        m.setAccessible(true);
        m.invoke(service);

        verify(subjectMapper, never()).insert(any(Subject.class));
    }

    @Test
    void ensureStandardSubjects_父科目非叶子_更新父() throws Exception {
        // 1122 不存在, 5001 不存在, 2221 存在但 isLeaf=true, 2221.01 不存在
        Subject parent2221 = stubSubject(10L, "2221");
        parent2221.setIsLeaf(true);
        when(subjectMapper.selectList(any()))
                .thenReturn(List.of())  // 1122 不存在
                .thenReturn(List.of())  // 5001 不存在
                .thenReturn(List.of(parent2221))  // 2221 存在
                .thenReturn(List.of())  // 2221.01 不存在
                // 2221.01 创建时再次 findSubjectByCode("2221")
                .thenReturn(List.of(parent2221));

        Method m = SalesInvoiceImportService.class.getDeclaredMethod("ensureStandardSubjects");
        m.setAccessible(true);
        m.invoke(service);

        // 父级 2221 被 updateById（非叶子化）
        verify(subjectMapper, atLeastOnce()).updateById(any(Subject.class));
        // 4 个 ensureSubject 都创建 → 至少 3 次 insert（1122, 5001, 2221.01）
        verify(subjectMapper, atLeast(3)).insert(any(Subject.class));
    }

    // ==================== matchOrCreateCustomer ====================

    @Test
    void matchOrCreateCustomer_税号匹配上_返回ID() throws Exception {
        SalesInvoiceImportService.ParsedInvoiceRow r = stubRow(1, null, "91110000ABC", "客户A");
        when(customerMapper.selectList(any())).thenReturn(List.of(stubCustomer(5L, "客户A", "91110000ABC")));

        Method m = SalesInvoiceImportService.class.getDeclaredMethod("matchOrCreateCustomer", SalesInvoiceImportService.ParsedInvoiceRow.class);
        m.setAccessible(true);
        Long id = (Long) m.invoke(service, r);
        assertEquals(5L, id);
        verify(customerMapper, never()).insert(any(CustomerEntity.class));
    }

    @Test
    void matchOrCreateCustomer_名称匹配上_返回ID() throws Exception {
        SalesInvoiceImportService.ParsedInvoiceRow r = stubRow(1, null, null, "客户A");
        when(customerMapper.selectList(any())).thenReturn(List.of(stubCustomer(7L, "客户A", null)));

        Method m = SalesInvoiceImportService.class.getDeclaredMethod("matchOrCreateCustomer", SalesInvoiceImportService.ParsedInvoiceRow.class);
        m.setAccessible(true);
        Long id = (Long) m.invoke(service, r);
        assertEquals(7L, id);
    }

    @Test
    void matchOrCreateCustomer_短名匹配上_返回ID() throws Exception {
        // "ABC（北京）科技" → "ABC科技" 5 字 ≥ 4
        SalesInvoiceImportService.ParsedInvoiceRow r = stubRow(1, null, null, "ABC（北京）科技");
        // 第 1 次按全名查空, 第 2 次按短名查命中
        when(customerMapper.selectList(any()))
                .thenReturn(List.of())  // 全名
                .thenReturn(List.of(stubCustomer(9L, "ABC科技", null)));  // 短名

        Method m = SalesInvoiceImportService.class.getDeclaredMethod("matchOrCreateCustomer", SalesInvoiceImportService.ParsedInvoiceRow.class);
        m.setAccessible(true);
        Long id = (Long) m.invoke(service, r);
        assertEquals(9L, id);
    }

    @Test
    void matchOrCreateCustomer_全无匹配_创建客户() throws Exception {
        SalesInvoiceImportService.ParsedInvoiceRow r = stubRow(1, null, null, "新客户X");
        when(customerMapper.selectList(any())).thenReturn(List.of());  // 3 次 selectList 都空
        when(customerMapper.insert(any(CustomerEntity.class))).thenAnswer(inv -> {
            CustomerEntity c = inv.getArgument(0);
            c.setId(123L);  // 模拟 insert 后回填 ID
            return 1;
        });

        Method m = SalesInvoiceImportService.class.getDeclaredMethod("matchOrCreateCustomer", SalesInvoiceImportService.ParsedInvoiceRow.class);
        m.setAccessible(true);
        Long id = (Long) m.invoke(service, r);
        assertNotNull(id);
        assertEquals(123L, id);
        verify(customerMapper, atLeastOnce()).insert(any(CustomerEntity.class));
    }

    @Test
    void matchOrCreateCustomer_名称和税号全空_创建匿名客户() throws Exception {
        SalesInvoiceImportService.ParsedInvoiceRow r = stubRow(1, null, null, null);
        when(customerMapper.insert(any(CustomerEntity.class))).thenAnswer(inv -> {
            CustomerEntity c = inv.getArgument(0);
            c.setId(999L);
            return 1;
        });

        Method m = SalesInvoiceImportService.class.getDeclaredMethod("matchOrCreateCustomer", SalesInvoiceImportService.ParsedInvoiceRow.class);
        m.setAccessible(true);
        Long id = (Long) m.invoke(service, r);
        assertNotNull(id);
        verify(customerMapper, times(1)).insert(any(CustomerEntity.class));
    }

    // ==================== parseInvoiceDate (通过 confirmImport 间接测或反射) ====================

    @Test
    void parseInvoiceDate_yyyyMMdd_正确解析() throws Exception {
        Method m = SalesInvoiceImportService.class.getDeclaredMethod("parseInvoiceDate", String.class);
        m.setAccessible(true);
        LocalDate d = (LocalDate) m.invoke(service, "20260615");
        assertEquals(LocalDate.of(2026, 6, 15), d);
    }

    @Test
    void parseInvoiceDate_ISO_正确解析() throws Exception {
        Method m = SalesInvoiceImportService.class.getDeclaredMethod("parseInvoiceDate", String.class);
        m.setAccessible(true);
        LocalDate d = (LocalDate) m.invoke(service, "2026-06-15");
        assertEquals(LocalDate.of(2026, 6, 15), d);
    }

    @Test
    void parseInvoiceDate_空白_返回null() throws Exception {
        Method m = SalesInvoiceImportService.class.getDeclaredMethod("parseInvoiceDate", String.class);
        m.setAccessible(true);
        LocalDate d = (LocalDate) m.invoke(service, "");
        assertNull(d);
    }

    // ==================== P10-1: 销售发票→应收单 ====================

    private BusinessDocEntity stubDoc(Long id, String docNo, Long customerId) {
        BusinessDocEntity d = new BusinessDocEntity();
        d.setId(id);
        d.setDocNo(docNo);
        d.setDocType("INVOICE_OUT");
        d.setCustomerId(customerId);
        d.setVoucherId(200L);
        return d;
    }
}
