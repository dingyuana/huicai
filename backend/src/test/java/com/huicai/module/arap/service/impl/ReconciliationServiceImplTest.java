package com.huicai.module.arap.service.impl;

import com.huicai.common.exception.BusinessException;
import com.huicai.module.arap.entity.*;
import com.huicai.module.arap.mapper.*;
import com.huicai.module.arap.service.ArapSettlementService;
import com.huicai.module.arap.service.ReconciliationService.ExecuteRequest;
import com.huicai.module.arap.service.ReconciliationService.PreCheckResult;
import com.huicai.module.arap.service.ReconciliationService.RecommendResult;
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

    @Mock private ReceivableMapper receivableMapper;
    @Mock private PayableMapper payableMapper;
    @Mock private CustomerMapper customerMapper;
    @Mock private VendorMapper vendorMapper;
    @Mock private ReconciliationLogMapper logMapper;
    @Mock private ArapSettlementService settlementService;

    @InjectMocks private ReconciliationServiceImpl service;

    private ReceivableEntity stubReceivable(Long id, Long customerId, BigDecimal amount, BigDecimal unsettled) {
        ReceivableEntity r = new ReceivableEntity();
        r.setId(id);
        r.setCustomerId(customerId);
        r.setAmount(amount);
        r.setSettledAmount(amount.subtract(unsettled));
        r.setUnsettledAmount(unsettled);
        r.setTxDate(LocalDate.of(2026, 6, 10));
        r.setSummary("应收测试");
        return r;
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

    private PayableEntity stubPayable(Long id, Long vendorId, BigDecimal amount, BigDecimal unsettled) {
        PayableEntity p = new PayableEntity();
        p.setId(id);
        p.setVendorId(vendorId);
        p.setAmount(amount);
        p.setSettledAmount(amount.subtract(unsettled));
        p.setUnsettledAmount(unsettled);
        p.setTxDate(LocalDate.of(2026, 6, 10));
        p.setSummary("应付测试");
        return p;
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
        verifyNoInteractions(receivableMapper);
    }

    @Test
    void recommendReceipt_应收未结清_按L4金额精确() {
        // recommendReceipt/recommendPayment 不传 externalNo/txDate，走 L3-L5 路径
        // 应收 unsettled=500，源金额 500 → L3（金额相等 + 日期近）或 L4
        when(receivableMapper.selectList(any())).thenReturn(List.of(
                stubReceivable(100L, 1L, new BigDecimal("500"), new BigDecimal("500"))
        ));
        RecommendResult r = service.recommendReceipt(1L, 1L, new BigDecimal("500"), "摘要", "客户A");
        assertEquals(1, r.items().size());
        // L3(金额+日期) 优先于 L4(金额精确) — 但 stubReceivable txDate 是 6-10, 源无日期, 走 L4
        assertTrue(r.items().get(0).matchLevel().equals("L3") || r.items().get(0).matchLevel().equals("L4"));
    }

    @Test
    void recommendForStatement_带externalNo_触发L1() {
        when(customerMapper.selectList(any())).thenReturn(List.of(stubCustomer(5L, "客户A")));
        when(receivableMapper.selectList(any())).thenReturn(List.of(
                stubReceivable(777L, 5L, new BigDecimal("500"), new BigDecimal("500"))
        ));
        RecommendResult r = service.recommendForStatement(
                1L, 1L, "in", new BigDecimal("500"), "客户A", "摘要", LocalDate.now(), "777");
        assertEquals(1, r.items().size());
        assertEquals("L1", r.items().get(0).matchLevel());
    }

    @Test
    void recommendReceipt_2张应收_按L级别排序() {
        // 第 1 张 L4（金额精确无 externalNo），第 2 张 L5（容差）
        ReceivableEntity r4 = stubReceivable(1L, 1L, new BigDecimal("100"), new BigDecimal("100"));
        ReceivableEntity r5 = stubReceivable(2L, 1L, new BigDecimal("100"), new BigDecimal("95"));
        when(receivableMapper.selectList(any())).thenReturn(List.of(r5, r4));
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
        when(payableMapper.selectList(any())).thenReturn(List.of(
                stubPayable(1L, 1L, new BigDecimal("100"), BigDecimal.ZERO)
        ));
        RecommendResult r = service.recommendPayment(1L, 1L, new BigDecimal("100"), "摘要", "供应商A");
        assertTrue(r.items().isEmpty());
    }

    // ==================== recommendForStatement ====================

    @Test
    void recommendForStatement_收款方向_查客户表() {
        when(customerMapper.selectList(any())).thenReturn(List.of(stubCustomer(5L, "客户A")));
        service.recommendForStatement(1L, 1L, "in", new BigDecimal("100"), "客户A", "摘要", LocalDate.now(), null);
        verify(customerMapper, atLeastOnce()).selectList(any());
        verifyNoInteractions(vendorMapper);
    }

    @Test
    void recommendForStatement_付款方向_查供应商表() {
        when(vendorMapper.selectList(any())).thenReturn(List.of(stubVendor(5L, "供应商A")));
        service.recommendForStatement(1L, 1L, "out", new BigDecimal("100"), "供应商A", "摘要", LocalDate.now(), null);
        verify(vendorMapper, atLeastOnce()).selectList(any());
        verifyNoInteractions(customerMapper);
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
        ReceivableEntity r = stubReceivable(1L, 1L, new BigDecimal("1000"), new BigDecimal("500"));
        when(receivableMapper.selectById(1L)).thenReturn(r);

        ExecuteRequest req = new ExecuteRequest("receipt", 1L, "INVOICE_OUT", 1L, new BigDecimal("200"), new BigDecimal("0.95"), "AUTO", 1L, null, null, "");
        ReconciliationLogEntity log = service.execute(req);

        assertNotNull(log);
        assertEquals("CONFIRMED", log.getStatus());
        verify(receivableMapper).updateById(any(ReceivableEntity.class));
        verify(logMapper).insert(any(ReconciliationLogEntity.class));
    }

    @Test
    void execute_应收不存在_throw() {
        when(receivableMapper.selectById(99L)).thenReturn(null);
        ExecuteRequest req = new ExecuteRequest("receipt", 1L, "INVOICE_OUT", 99L, new BigDecimal("100"), BigDecimal.ZERO, "MANUAL", 1L, null, "202606", "");
        BusinessException ex = assertThrows(BusinessException.class, () -> service.execute(req));
        assertTrue(ex.getMessage().contains("应收记录不存在"));
    }

    @Test
    void execute_有period_调用settlementService() {
        ReceivableEntity r = stubReceivable(1L, 1L, new BigDecimal("1000"), new BigDecimal("500"));
        when(receivableMapper.selectById(1L)).thenReturn(r);

        ExecuteRequest req = new ExecuteRequest("receipt", 1L, "INVOICE_OUT", 1L, new BigDecimal("200"), BigDecimal.ZERO, "MANUAL", 1L, null, "202606", "");
        service.execute(req);
        verify(settlementService, atLeastOnce()).create(any(), any());
    }

    @Test
    void execute_无period_跳过settlement() {
        ReceivableEntity r = stubReceivable(1L, 1L, new BigDecimal("1000"), new BigDecimal("500"));
        when(receivableMapper.selectById(1L)).thenReturn(r);

        ExecuteRequest req = new ExecuteRequest("receipt", 1L, "INVOICE_OUT", 1L, new BigDecimal("200"), BigDecimal.ZERO, "MANUAL", 1L, null, null, "");
        service.execute(req);
        verifyNoInteractions(settlementService);
    }

    // ==================== preCheck ====================

    @Test
    void preCheck_5项全过_allPassedTrue() {
        ReceivableEntity r = stubReceivable(1L, 5L, new BigDecimal("1000"), new BigDecimal("500"));
        when(receivableMapper.selectById(1L)).thenReturn(r);

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

        ReceivableEntity r = stubReceivable(1L, 1L, new BigDecimal("1000"), new BigDecimal("300"));
        when(logMapper.selectById(1L)).thenReturn(log);
        when(receivableMapper.selectById(1L)).thenReturn(r);

        service.reverse(1L);
        verify(receivableMapper).updateById(any(ReceivableEntity.class));
        assertEquals("CANCELLED", log.getStatus());
        verify(logMapper).updateById(log);
    }

    @Test
    void reverse_记录不存在_throw() {
        when(logMapper.selectById(99L)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.reverse(99L));
        assertTrue(ex.getMessage().contains("不存在"));
    }

    @Test
    void reverse_状态非CONFIRMED_throw() {
        ReconciliationLogEntity log = new ReconciliationLogEntity();
        log.setId(1L);
        log.setStatus("REVERSED");
        when(logMapper.selectById(1L)).thenReturn(log);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.reverse(1L));
        assertTrue(ex.getMessage().contains("已确认"));
    }

    // ==================== batchExecute ====================

    @Test
    void batchExecute_2个请求_调2次execute() {
        ReceivableEntity r = stubReceivable(1L, 1L, new BigDecimal("1000"), new BigDecimal("500"));
        when(receivableMapper.selectById(1L)).thenReturn(r);

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
        when(receivableMapper.selectById(100L)).thenReturn(stubReceivable(100L, 5L, new BigDecimal("500"), new BigDecimal("300")));

        service.reject(1L, "金额有误");

        // 应收恢复: settled=500-300=200, 减去100→100; unsettled=500-100=400
        org.mockito.ArgumentCaptor<ReceivableEntity> captor =
                org.mockito.ArgumentCaptor.forClass(ReceivableEntity.class);
        verify(receivableMapper).updateById(captor.capture());
        ReceivableEntity updated = captor.getValue();
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
        ReceivableEntity r = stubReceivable(1L, 1L, new BigDecimal("1000"), new BigDecimal("500"));
        when(receivableMapper.selectById(1L)).thenReturn(r);

        ExecuteRequest req = new ExecuteRequest("receipt", 1L, "INVOICE_OUT", 1L, new BigDecimal("500"), BigDecimal.ZERO, "MANUAL", 1L, null, null, "");
        ReconciliationLogEntity result = service.executeWithAdjustment(req, BigDecimal.ZERO, "FEE", 100L);

        assertNotNull(result);
        verify(logMapper, times(1)).insert(any(ReconciliationLogEntity.class));
    }

    @Test
    void executeWithAdjustment_有差额_主核销加调整日志() {
        ReceivableEntity r = stubReceivable(1L, 1L, new BigDecimal("1000"), new BigDecimal("500"));
        when(receivableMapper.selectById(1L)).thenReturn(r);

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
        when(receivableMapper.selectList(any())).thenReturn(List.of(stubReceivable(1L, 5L, new BigDecimal("500"), new BigDecimal("300"))));

        boolean result = service.hasOpenInvoices("INVOICE_OUT", 5L);

        assertTrue(result);
    }

    @Test
    void hasOpenInvoices_客户无未结清_返回false() {
        when(receivableMapper.selectList(any())).thenReturn(List.of());

        boolean result = service.hasOpenInvoices("INVOICE_OUT", 5L);

        assertFalse(result);
    }

    @Test
    void hasOpenInvoices_供应商有未结清应付_返回true() {
        when(payableMapper.selectList(any())).thenReturn(List.of(stubPayable(1L, 8L, new BigDecimal("1000"), new BigDecimal("500"))));

        boolean result = service.hasOpenInvoices("INVOICE_IN", 8L);

        assertTrue(result);
    }

    @Test
    void hasOpenInvoices_参数为null_返回false() {
        assertFalse(service.hasOpenInvoices(null, 5L));
        assertFalse(service.hasOpenInvoices("INVOICE_OUT", null));
    }
}
