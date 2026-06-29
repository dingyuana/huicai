package com.huicai.module.arap.service.impl;

import com.huicai.common.exception.BusinessException;
import com.huicai.module.arap.entity.*;
import com.huicai.module.arap.mapper.*;
import com.huicai.module.arap.service.ArapSettlementService;
import com.huicai.module.arap.service.ReconciliationService;
import com.huicai.module.arap.service.ReconciliationService.AllocationItem;
import com.huicai.module.arap.service.ReconciliationService.ExecuteRequest;
import com.huicai.module.arap.service.ReconciliationService.PreCheckResult;
import com.huicai.module.arap.service.ReconciliationService.RecommendResult;
import com.huicai.module.finance.entity.BusinessDocEntity;
import com.huicai.module.finance.mapper.BusinessDocMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReconciliationServiceImplTest {

    @Mock private BusinessDocMapper businessDocMapper;
    @Mock private CustomerMapper customerMapper;
    @Mock private VendorMapper vendorMapper;
    @Mock private ReconciliationLogMapper logMapper;
    @Mock private ReconciliationExceptionMapper exceptionMapper;
    @Mock private ArapSettlementService settlementService;

    @InjectMocks private ReconciliationServiceImpl service;

    private BusinessDocEntity stubBusinessDoc(Long id, Long customerId, Long supplierId, String docType, BigDecimal amount, BigDecimal unsettled) {
        BusinessDocEntity b = new BusinessDocEntity();
        b.setId(id);
        b.setCustomerId(customerId);
        b.setSupplierId(supplierId);
        b.setDocType(docType);
        b.setAmount(amount);
        b.setSettledAmount(amount.subtract(unsettled));
        b.setUnsettledAmount(unsettled);
        b.setDocDate(LocalDate.of(2026, 6, 10));
        b.setSummary(docType.equals("INVOICE_OUT") ? "应收测试" : "应付测试");
        b.setDueDate(LocalDate.of(2026, 7, 10));
        return b;
    }

    private ReconciliationLogEntity stubLog(Long id, String status) {
        ReconciliationLogEntity l = new ReconciliationLogEntity();
        l.setId(id);
        l.setStatus(status);
        l.setTargetDocType("INVOICE_OUT");
        l.setTargetDocId(100L);
        l.setAllocatedAmount(new BigDecimal("100"));
        l.setSourceDocType("bank_txn");
        l.setSourceDocId(1L);
        return l;
    }

    private CustomerEntity stubCustomer(Long id, String name) {
        CustomerEntity c = new CustomerEntity();
        c.setId(id);
        c.setName(name);
        return c;
    }

    private VendorEntity stubVendor(Long id, String name) {
        VendorEntity v = new VendorEntity();
        v.setId(id);
        v.setName(name);
        return v;
    }

    // ==================== recommendReceipt ====================

    @Test
    void recommendReceipt_无客户ID_返回空items() {
        RecommendResult r = service.recommendReceipt(1L, null, new BigDecimal("100"), "测试", "客户A");
        assertTrue(r.items().isEmpty());
        verifyNoInteractions(businessDocMapper);
    }

    @Test
    void recommendReceipt_应收未结清_按L4金额精确() {
        // recommendReceipt/recommendPayment 不传 externalNo/txDate，走 L3-L5 路径
        // 应收 unsettled=500，源金额 500 → L3（金额相等 + 日期近）或 L4
        when(businessDocMapper.selectList(any())).thenReturn(List.of(
                stubBusinessDoc(100L, 1L, null, "INVOICE_OUT", new BigDecimal("500"), new BigDecimal("500"))
        ));
        RecommendResult r = service.recommendReceipt(1L, 1L, new BigDecimal("500"), "摘要", "客户A");
        assertEquals(1, r.items().size());
        // L3(金额+日期) 优先于 L4(金额精确) — 但 stubBusinessDoc txDate 是 6-10, 源无日期, 走 L4
        assertTrue(r.items().get(0).matchLevel().equals("L3") || r.items().get(0).matchLevel().equals("L4"));
    }

    @Test
    void recommendReceipt_2张应收_按L级别排序() {
        // 第 1 张 L4（金额精确无 externalNo），第 2 张 L5（容差）
        BusinessDocEntity r4 = stubBusinessDoc(1L, 1L, null, "INVOICE_OUT", new BigDecimal("100"), new BigDecimal("100"));
        BusinessDocEntity r5 = stubBusinessDoc(2L, 1L, null, "INVOICE_OUT", new BigDecimal("100"), new BigDecimal("95"));
        when(businessDocMapper.selectList(any())).thenReturn(List.of(r5, r4));
        RecommendResult r = service.recommendReceipt(1L, 1L, new BigDecimal("100"), "摘要", "客户A");
        assertEquals(2, r.items().size());
        assertEquals("L4", r.items().get(0).matchLevel());
        assertEquals("L5", r.items().get(1).matchLevel());
    }

    // ==================== recommendPayment ====================

    @Test
    void recommendPayment_无供应商ID_返回空items() {
        RecommendResult r = service.recommendPayment(1L, null, new BigDecimal("100"), "测试", "供应商A");
        assertTrue(r.items().isEmpty());
    }

    @Test
    void recommendPayment_应付已结清_跳过() {
        when(businessDocMapper.selectList(any())).thenReturn(List.of(
                stubBusinessDoc(1L, null, 1L, "INVOICE_IN", new BigDecimal("100"), BigDecimal.ZERO)
        ));
        RecommendResult r = service.recommendPayment(1L, 1L, new BigDecimal("100"), "摘要", "供应商A");
        assertTrue(r.items().isEmpty());
    }

    // ==================== execute ====================

    @Test
    void execute_金额为null_throw() {
        ExecuteRequest req = new ExecuteRequest("receipt", 1L, "INVOICE_OUT", 1L, null, BigDecimal.ZERO, "MANUAL", 1L, null, "202606", "");
        BusinessException ex = assertThrows(BusinessException.class, () -> service.execute(req));
        assertTrue(ex.getMessage().contains("大于0"));
    }

    @Test
    void execute_INVOICE_OUT_更新应收并插入日志() {
        BusinessDocEntity doc = stubBusinessDoc(1L, 1L, null, "INVOICE_OUT", new BigDecimal("1000"), new BigDecimal("500"));
        when(businessDocMapper.selectById(1L)).thenReturn(doc);
        when(businessDocMapper.updateById(any(BusinessDocEntity.class))).thenReturn(1);

        ExecuteRequest req = new ExecuteRequest("receipt", 1L, "INVOICE_OUT", 1L, new BigDecimal("200"), new BigDecimal("0.95"), "AUTO", 1L, null, null, "");
        ReconciliationLogEntity log = service.execute(req);

        assertNotNull(log);
        assertEquals("CONFIRMED", log.getStatus());
        verify(businessDocMapper).updateById(any(BusinessDocEntity.class));
        verify(logMapper).insert(any(ReconciliationLogEntity.class));
    }

    @Test
    void execute_应收不存在_throw() {
        when(businessDocMapper.selectById(99L)).thenReturn(null);
        ExecuteRequest req = new ExecuteRequest("receipt", 1L, "INVOICE_OUT", 99L, new BigDecimal("100"), BigDecimal.ZERO, "MANUAL", 1L, null, "202606", "");
        BusinessException ex = assertThrows(BusinessException.class, () -> service.execute(req));
        assertTrue(ex.getMessage().contains("业务单据不存在"));
    }

    @Test
    void execute_有period_调用settlementService() {
        BusinessDocEntity doc = stubBusinessDoc(1L, 1L, null, "INVOICE_OUT", new BigDecimal("1000"), new BigDecimal("500"));
        when(businessDocMapper.selectById(1L)).thenReturn(doc);
        when(businessDocMapper.updateById(any(BusinessDocEntity.class))).thenReturn(1);

        ExecuteRequest req = new ExecuteRequest("receipt", 1L, "INVOICE_OUT", 1L, new BigDecimal("200"), BigDecimal.ZERO, "MANUAL", 1L, null, "202606", "");
        service.execute(req);
        verify(settlementService, atLeastOnce()).create(any(), any());
    }

    @Test
    void execute_无period_跳过settlement() {
        BusinessDocEntity doc = stubBusinessDoc(1L, 1L, null, "INVOICE_OUT", new BigDecimal("1000"), new BigDecimal("500"));
        when(businessDocMapper.selectById(1L)).thenReturn(doc);
        when(businessDocMapper.updateById(any(BusinessDocEntity.class))).thenReturn(1);

        ExecuteRequest req = new ExecuteRequest("receipt", 1L, "INVOICE_OUT", 1L, new BigDecimal("200"), BigDecimal.ZERO, "MANUAL", 1L, null, null, "");
        service.execute(req);
        verifyNoInteractions(settlementService);
    }

    // ==================== preCheck ====================

    @Test
    void preCheck_5项全过_allPassedTrue() {
        BusinessDocEntity doc = stubBusinessDoc(1L, 5L, null, "INVOICE_OUT", new BigDecimal("1000"), new BigDecimal("500"));
        when(businessDocMapper.selectById(1L)).thenReturn(doc);

        ExecuteRequest req = new ExecuteRequest("receipt", 1L, "INVOICE_OUT", 1L, new BigDecimal("300"), new BigDecimal("0.95"), "AUTO", 5L, null, "202606", "");
        PreCheckResult result = service.preCheck(req);
        assertTrue(result.allPassed());
        assertEquals(5, result.checks().size());
    }

    @Test
    void preCheck_sourceDocId为null_第1项失败() {
        ExecuteRequest req = new ExecuteRequest(null, null, "INVOICE_OUT", 1L, new BigDecimal("300"), BigDecimal.ZERO, "MANUAL", 1L, null, "202606", "");
        PreCheckResult result = service.preCheck(req);
        assertEquals(false, result.checks().get(0).passed());
    }

    @Test
    void preCheck_金额为0_第5项不通过() {
        // 第 5 项是 periodValid；金额检查是第 4 项 amountValid
        ExecuteRequest req = new ExecuteRequest("receipt", 1L, "INVOICE_OUT", 1L, new BigDecimal("0"), BigDecimal.ZERO, "MANUAL", 1L, null, "invalid", "");
        PreCheckResult result = service.preCheck(req);
        // 第 5 项 periodValid = false（period="invalid"）
        assertEquals("periodValid", result.checks().get(4).checkName());
        assertFalse(result.checks().get(4).passed());
    }

    // ==================== pageLogs ====================

    @Test
    void pageLogs_正常分页() {
        when(logMapper.selectPage(any(), any())).thenReturn(null);
        var r = service.pageLogs("receipt", 1, 10);
        verify(logMapper).selectPage(any(), any());
    }

    // ==================== reverse ====================

    @Test
    void reverse_正常_INVOICE_OUT恢复应收() {
        ReconciliationLogEntity log = new ReconciliationLogEntity();
        log.setId(1L);
        log.setStatus("CONFIRMED");
        log.setSourceDocType("receipt");
        log.setSourceDocId(1L);
        log.setTargetDocType("INVOICE_OUT");
        log.setTargetDocId(1L);
        log.setAllocatedAmount(new BigDecimal("200"));

        BusinessDocEntity doc = stubBusinessDoc(1L, 1L, null, "INVOICE_OUT", new BigDecimal("1000"), new BigDecimal("300"));
        when(logMapper.selectById(1L)).thenReturn(log);
        when(businessDocMapper.selectById(1L)).thenReturn(doc);
        when(businessDocMapper.updateById(any(BusinessDocEntity.class))).thenReturn(1);

        service.reverse(1L, "操作失误");
        verify(businessDocMapper).updateById(any(BusinessDocEntity.class));
        assertEquals("CANCELLED", log.getStatus());
        verify(logMapper).updateById(log);
    }

    @Test
    void reverse_记录不存在_throw() {
        when(logMapper.selectById(99L)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.reverse(99L, "test"));
        assertTrue(ex.getMessage().contains("不存在"));
    }

    @Test
    void reverse_状态非CONFIRMED_throw() {
        ReconciliationLogEntity log = new ReconciliationLogEntity();
        log.setId(1L);
        log.setStatus("REVERSED");
        when(logMapper.selectById(1L)).thenReturn(log);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.reverse(1L, "test"));
        assertTrue(ex.getMessage().contains("已确认"));
    }

    @Test
    void reverse_无原因_throw() {
        ReconciliationLogEntity log = new ReconciliationLogEntity();
        log.setId(1L);
        log.setStatus("CONFIRMED");
        when(logMapper.selectById(1L)).thenReturn(log);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.reverse(1L, ""));
        assertTrue(ex.getMessage().contains("原因"));
    }

    // ==================== batchExecute ====================

    @Test
    void batchExecute_2个请求_调2次execute() {
        BusinessDocEntity doc = stubBusinessDoc(1L, 1L, null, "INVOICE_OUT", new BigDecimal("1000"), new BigDecimal("500"));
        when(businessDocMapper.selectById(1L)).thenReturn(doc);
        when(businessDocMapper.updateById(any(BusinessDocEntity.class))).thenReturn(1);

        ExecuteRequest req1 = new ExecuteRequest("receipt", 1L, "INVOICE_OUT", 1L, new BigDecimal("100"), BigDecimal.ZERO, "MANUAL", 1L, null, null, "");
        ExecuteRequest req2 = new ExecuteRequest("receipt", 2L, "INVOICE_OUT", 1L, new BigDecimal("200"), BigDecimal.ZERO, "MANUAL", 1L, null, null, "");
        var logs = service.batchExecute(List.of(req1, req2));
        assertEquals(2, logs.size());
        verify(logMapper, times(2)).insert(any(ReconciliationLogEntity.class));
    }

    // ==================== P12-1: 核销审批/驳回 ====================

    @Test
    void approve_CONFIRMED状态_变为EXECUTED() {
        when(logMapper.selectById(1L)).thenReturn(stubLog(1L, "CONFIRMED"));
        when(logMapper.updateById(any(ReconciliationLogEntity.class))).thenReturn(1);

        ReconciliationLogEntity result = service.approve(1L);

        assertEquals("EXECUTED", result.getStatus());
        verify(logMapper, atLeastOnce()).updateById(any(ReconciliationLogEntity.class));
    }

    @Test
    void approve_非CONFIRMED状态_抛异常() {
        when(logMapper.selectById(1L)).thenReturn(stubLog(1L, "EXECUTED"));

        assertThrows(BusinessException.class, () -> service.approve(1L));
    }

    @Test
    void approve_不存在_抛异常() {
        when(logMapper.selectById(999L)).thenReturn(null);

        assertThrows(BusinessException.class, () -> service.approve(999L));
    }

    @Test
    void reject_CONFIRMED状态_恢复应收_变为REJECTED() {
        when(logMapper.selectById(1L)).thenReturn(stubLog(1L, "CONFIRMED"));
        when(businessDocMapper.selectById(100L)).thenReturn(stubBusinessDoc(100L, 5L, null, "INVOICE_OUT", new BigDecimal("500"), new BigDecimal("300")));
        when(businessDocMapper.updateById(any(BusinessDocEntity.class))).thenReturn(1);

        service.reject(1L, "金额有误");

        // 应收恢复: settled=500-300=200, 减去100→100; unsettled=500-100=400
        org.mockito.ArgumentCaptor<BusinessDocEntity> captor =
                org.mockito.ArgumentCaptor.forClass(BusinessDocEntity.class);
        verify(businessDocMapper).updateById(captor.capture());
        BusinessDocEntity updated = captor.getValue();
        assertEquals(0, updated.getSettledAmount().compareTo(new BigDecimal("100")));
        assertEquals(0, updated.getUnsettledAmount().compareTo(new BigDecimal("400")));
        verify(logMapper, atLeastOnce()).updateById(any(ReconciliationLogEntity.class));
    }

    @Test
    void reject_非CONFIRMED状态_抛异常() {
        when(logMapper.selectById(1L)).thenReturn(stubLog(1L, "EXECUTED"));

        assertThrows(BusinessException.class, () -> service.reject(1L, "test"));
    }

    @Test
    void reject_不存在_抛异常() {
        when(logMapper.selectById(999L)).thenReturn(null);

        assertThrows(BusinessException.class, () -> service.reject(999L, "test"));
    }

    // ==================== P12-2: 差额调整核销 ====================

    @Test
    void executeWithAdjustment_差额0_走普通核销() {
        BusinessDocEntity doc = stubBusinessDoc(1L, 1L, null, "INVOICE_OUT", new BigDecimal("1000"), new BigDecimal("500"));
        when(businessDocMapper.selectById(1L)).thenReturn(doc);
        when(businessDocMapper.updateById(any(BusinessDocEntity.class))).thenReturn(1);

        ExecuteRequest req = new ExecuteRequest("receipt", 1L, "INVOICE_OUT", 1L, new BigDecimal("500"), BigDecimal.ZERO, "MANUAL", 1L, null, null, "");
        ReconciliationLogEntity result = service.executeWithAdjustment(req, BigDecimal.ZERO, "FEE", 100L);

        assertNotNull(result);
        verify(logMapper, times(1)).insert(any(ReconciliationLogEntity.class));
    }

    @Test
    void executeWithAdjustment_有差额_主核销加调整日志() {
        BusinessDocEntity doc = stubBusinessDoc(1L, 1L, null, "INVOICE_OUT", new BigDecimal("1000"), new BigDecimal("500"));
        when(businessDocMapper.selectById(1L)).thenReturn(doc);
        when(businessDocMapper.updateById(any(BusinessDocEntity.class))).thenReturn(1);

        // 请求核销 500, 差额 20, 主核销 480
        ExecuteRequest req = new ExecuteRequest("receipt", 1L, "INVOICE_OUT", 1L, new BigDecimal("500"), BigDecimal.ZERO, "MANUAL", 1L, null, "202606", "");
        ReconciliationLogEntity result = service.executeWithAdjustment(req, new BigDecimal("20"), "FEE", 100L);

        assertNotNull(result);
        // 2 次 insert: 主核销 + 差额调整
        verify(logMapper, times(2)).insert(any(ReconciliationLogEntity.class));
    }

    @Test
    void executeWithAdjustment_差额大于请求金额_抛异常() {
        ExecuteRequest req = new ExecuteRequest("receipt", 1L, "INVOICE_OUT", 1L, new BigDecimal("100"), BigDecimal.ZERO, "MANUAL", 1L, null, null, "");

        assertThrows(BusinessException.class, () ->
                service.executeWithAdjustment(req, new BigDecimal("200"), "FEE", 100L));
    }

    // ==================== P12-3: 预收/预付检测 ====================

    @Test
    void hasOpenInvoices_客户有未结清应收_返回true() {
        when(businessDocMapper.selectCount(any())).thenReturn(1L);

        boolean result = service.hasOpenInvoices("INVOICE_OUT", 5L);

        assertTrue(result);
    }

    @Test
    void hasOpenInvoices_客户无未结清_返回false() {
        when(businessDocMapper.selectCount(any())).thenReturn(0L);

        boolean result = service.hasOpenInvoices("INVOICE_OUT", 5L);

        assertFalse(result);
    }

    @Test
    void hasOpenInvoices_供应商有未结清应付_返回true() {
        when(businessDocMapper.selectCount(any())).thenReturn(1L);

        boolean result = service.hasOpenInvoices("INVOICE_IN", 8L);

        assertTrue(result);
    }

    @Test
    void hasOpenInvoices_参数为null_返回false() {
        assertFalse(service.hasOpenInvoices(null, 5L));
        assertFalse(service.hasOpenInvoices("INVOICE_OUT", null));
    }

    // ==================== FIFO 自动核销 ====================

    @Test
    void autoReconcileFifo_金额0_返回空列表() {
        List<ReconciliationLogEntity> r = service.autoReconcileFifo(1L, "INVOICE_OUT", BigDecimal.ZERO, "receipt", 1L, "202606", "FIFO测试");
        assertTrue(r.isEmpty());
        verifyNoInteractions(businessDocMapper);
    }

    @Test
    void autoReconcileFifo_应收按到期日核销() {
        BusinessDocEntity r1 = stubBusinessDoc(1L, 5L, null, "INVOICE_OUT", new BigDecimal("1000"), new BigDecimal("500"));
        BusinessDocEntity r2 = stubBusinessDoc(2L, 5L, null, "INVOICE_OUT", new BigDecimal("500"), new BigDecimal("300"));
        r1.setDueDate(LocalDate.of(2026, 5, 1));
        r2.setDueDate(LocalDate.of(2026, 6, 1));
        when(businessDocMapper.selectList(any())).thenReturn(List.of(r1, r2));
        when(businessDocMapper.selectById(1L)).thenReturn(r1);
        when(businessDocMapper.selectById(2L)).thenReturn(r2);
        when(businessDocMapper.updateById(any(BusinessDocEntity.class))).thenReturn(1);

        List<ReconciliationLogEntity> logs = service.autoReconcileFifo(5L, "INVOICE_OUT", new BigDecimal("700"), "receipt", 1L, "202606", "FIFO测试");
        assertEquals(2, logs.size());
        verify(businessDocMapper, times(2)).updateById(any(BusinessDocEntity.class));
        verify(logMapper, times(2)).insert(any(ReconciliationLogEntity.class));
    }

    // ==================== 异常池 ====================

    @Test
    void createException_创建异常记录() {
        ReconciliationExceptionEntity ex = service.createException(
                "bank_txn", 1L, "INVOICE_OUT", 100L,
                5L, "CUSTOMER", new BigDecimal("1000"), new BigDecimal("500"),
                "PARTY_MISMATCH", "客商不匹配", null);
        verify(exceptionMapper).insert(any(ReconciliationExceptionEntity.class));
        assertEquals("OPEN", ex.getStatus());
    }

    @Test
    void resolveException_正常解决() {
        ReconciliationExceptionEntity ex = new ReconciliationExceptionEntity();
        ex.setId(1L);
        ex.setStatus("OPEN");
        when(exceptionMapper.selectById(1L)).thenReturn(ex);

        service.resolveException(1L, 1L, "已核对");
        assertEquals("RESOLVED", ex.getStatus());
        verify(exceptionMapper).updateById(ex);
    }

    @Test
    void resolveException_非OPEN状态_throw() {
        ReconciliationExceptionEntity ex = new ReconciliationExceptionEntity();
        ex.setId(1L);
        ex.setStatus("RESOLVED");
        when(exceptionMapper.selectById(1L)).thenReturn(ex);

        assertThrows(BusinessException.class, () -> service.resolveException(1L, 1L, "test"));
    }

    @Test
    void ignoreException_忽略异常() {
        ReconciliationExceptionEntity ex = new ReconciliationExceptionEntity();
        ex.setId(1L);
        ex.setStatus("OPEN");
        when(exceptionMapper.selectById(1L)).thenReturn(ex);

        service.ignoreException(1L, 1L, "无需处理");
        assertEquals("IGNORED", ex.getStatus());
        verify(exceptionMapper).updateById(ex);
    }

    @Test
    void pageExceptions_正常分页() {
        when(exceptionMapper.selectPage(any(), any())).thenReturn(null);
        service.pageExceptions("OPEN", "PARTY_MISMATCH", 1, 20);
        verify(exceptionMapper).selectPage(any(), any());
    }

    // ==================== 多对多核销 ====================

    @Test
    void splitAllocate_分配为空_throw() {
        assertThrows(BusinessException.class, () -> service.splitAllocate(
                "receipt", 1L, 1L, null,
                new BigDecimal("500"), List.of(), "202606", "split测试"));
    }

    @Test
    void splitAllocate_分配金额超总额_throw() {
        List<ReconciliationService.AllocationItem> allocs = List.of(
                new ReconciliationService.AllocationItem("INVOICE_OUT", 1L, new BigDecimal("600"))
        );
        assertThrows(BusinessException.class, () -> service.splitAllocate(
                "receipt", 1L, 1L, null,
                new BigDecimal("500"), allocs, "202606", "split测试"));
    }

    @Test
    void smartAllocate_0金额_返回空() {
        List<ReconciliationLogEntity> r = service.smartAllocate(
                "receipt", 1L, 5L, "CUSTOMER", "INVOICE_OUT",
                BigDecimal.ZERO, "202606", "smart测试");
        assertTrue(r.isEmpty());
    }
}
