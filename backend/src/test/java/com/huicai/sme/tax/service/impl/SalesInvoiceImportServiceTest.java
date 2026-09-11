package com.huicai.sme.tax.service.impl;

import com.huicai.base.masterdata.entity.CustomerEntity;
import com.huicai.base.masterdata.mapper.CustomerMapper;
import com.huicai.base.business.entity.BusinessDocEntity;
import com.huicai.base.business.mapper.BusinessDocEntryMapper;
import com.huicai.base.business.mapper.BusinessDocMapper;
import com.huicai.base.voucher.entity.VoucherEntryEntity;
import com.huicai.base.voucher.mapper.VoucherEntryMapper;
import com.huicai.base.voucher.mapper.VoucherMapper;
import com.huicai.base.voucher.service.VoucherNoService;
import com.huicai.base.system.entity.Subject;
import com.huicai.base.system.mapper.SubjectMapper;
import com.huicai.base.business.mapper.OutputInvoiceMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
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
    @Mock private com.huicai.base.business.util.ColumnMappingResolver columnMappingResolver;

    @InjectMocks private com.huicai.sme.tax.service.impl.SalesInvoiceImportService service;

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

    // ==================== extractOriginalInvoiceNo (红冲发票号提取) ====================

    @Test
    void extractOriginalInvoiceNo_实际模板格式_提取正确() throws Exception {
        Method m = SalesInvoiceImportService.class.getDeclaredMethod("extractOriginalInvoiceNo", String.class);
        m.setAccessible(true);
        String remark = "被红冲蓝字数电票号码：25922000000020770476 红字发票信息确认单编号：37028325041001204158";
        String result = (String) m.invoke(service, remark);
        assertEquals("25922000000020770476", result);
    }

    @Test
    void extractOriginalInvoiceNo_发票号码格式_提取正确() throws Exception {
        Method m = SalesInvoiceImportService.class.getDeclaredMethod("extractOriginalInvoiceNo", String.class);
        m.setAccessible(true);
        String result = (String) m.invoke(service, "被红冲蓝字发票号码：12345678");
        assertEquals("12345678", result);
    }

    @Test
    void extractOriginalInvoiceNo_原发票号格式_提取正确() throws Exception {
        Method m = SalesInvoiceImportService.class.getDeclaredMethod("extractOriginalInvoiceNo", String.class);
        m.setAccessible(true);
        String result = (String) m.invoke(service, "原发票号: 87654321");
        assertEquals("87654321", result);
    }

    @Test
    void extractOriginalInvoiceNo_红冲自格式_提取正确() throws Exception {
        Method m = SalesInvoiceImportService.class.getDeclaredMethod("extractOriginalInvoiceNo", String.class);
        m.setAccessible(true);
        String result = (String) m.invoke(service, "红冲自 INV-2026-001");
        assertEquals("INV-2026-001", result);
    }

    @Test
    void extractOriginalInvoiceNo_空字符串_返回null() throws Exception {
        Method m = SalesInvoiceImportService.class.getDeclaredMethod("extractOriginalInvoiceNo", String.class);
        m.setAccessible(true);
        assertNull((String) m.invoke(service, ""));
    }

    // ==================== extractRedConfirmNo (确认单编号提取) ====================

    @Test
    void extractRedConfirmNo_实际模板格式_提取正确() throws Exception {
        Method m = SalesInvoiceImportService.class.getDeclaredMethod("extractRedConfirmNo", String.class);
        m.setAccessible(true);
        String remark = "被红冲蓝字数电票号码：25922000000020770476 红字发票信息确认单编号：37028325041001204158";
        String result = (String) m.invoke(service, remark);
        assertEquals("37028325041001204158", result);
    }

    @Test
    void extractRedConfirmNo_确认单编号简写_提取正确() throws Exception {
        Method m = SalesInvoiceImportService.class.getDeclaredMethod("extractRedConfirmNo", String.class);
        m.setAccessible(true);
        String result = (String) m.invoke(service, "确认单编号：99999999999999999999");
        assertEquals("99999999999999999999", result);
    }

    @Test
    void extractRedConfirmNo_红字确认单编号简写_提取正确() throws Exception {
        Method m = SalesInvoiceImportService.class.getDeclaredMethod("extractRedConfirmNo", String.class);
        m.setAccessible(true);
        String result = (String) m.invoke(service, "红字确认单编号：88888888888888888888");
        assertEquals("88888888888888888888", result);
    }

    @Test
    void extractRedConfirmNo_空字符串_返回null() throws Exception {
        Method m = SalesInvoiceImportService.class.getDeclaredMethod("extractRedConfirmNo", String.class);
        m.setAccessible(true);
        assertNull((String) m.invoke(service, ""));
    }

    @Test
    void extractRedConfirmNo_无确认单信息_返回null() throws Exception {
        Method m = SalesInvoiceImportService.class.getDeclaredMethod("extractRedConfirmNo", String.class);
        m.setAccessible(true);
        String result = (String) m.invoke(service, "这是一条普通备注");
        assertNull(result);
    }

    // ==================== 销项收入科目 5001→6001 (RED: 应 FAIL) ====================

    @Test
    void createVoucher_蓝字发票_收入科目应为6001而非5001() throws Exception {
        Subject subject1122 = stubSubject(1L, "1122");
        Subject subject5001 = stubSubject(99L, "5001");
        Subject subject6001 = stubSubject(20L, "6001");
        Subject subject222101 = stubSubject(3L, "2221.01");

        // mock: findSubjectByCode 按调用顺序返回 — 第2次查询"6001"
        when(subjectMapper.selectList(any()))
                .thenReturn(List.of(subject1122))
                .thenReturn(List.of(subject6001))
                .thenReturn(List.of(subject222101));
        when(voucherNoService.generateNextNo(anyString(), anyLong())).thenReturn("JZ2026060001");

        BusinessDocEntity doc = new BusinessDocEntity();
        doc.setId(100L);
        SalesInvoiceImportService.ParsedInvoiceRow row = stubRow(1, "INV-001", "91110000ABC", "测试客户");

        Method m = SalesInvoiceImportService.class.getDeclaredMethod(
                "createVoucher", BusinessDocEntity.class,
                SalesInvoiceImportService.ParsedInvoiceRow.class, Long.class, String.class);
        m.setAccessible(true);
        m.invoke(service, doc, row, 10L, "202606");

        ArgumentCaptor<VoucherEntryEntity> captor = ArgumentCaptor.forClass(VoucherEntryEntity.class);
        verify(voucherEntryMapper, times(3)).insert(captor.capture());

        // 收入贷方分录：debit=0, credit=1000 (不含税金额)
        VoucherEntryEntity revenueEntry = captor.getAllValues().stream()
                .filter(e -> e.getDebit().compareTo(BigDecimal.ZERO) == 0
                        && e.getCredit().compareTo(new BigDecimal("1000")) == 0)
                .findFirst()
                .orElseThrow(() -> new AssertionError("未找到收入贷方分录"));

        assertEquals(subject6001.getId(), revenueEntry.getSubjectId(),
                "销项收入科目应为6001(主营业务收入)而非5001(生产成本)");
    }
}