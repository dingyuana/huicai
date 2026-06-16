package com.huicai.module.arap.service.impl;

import com.huicai.common.exception.BusinessException;
import com.huicai.module.arap.entity.*;
import com.huicai.module.arap.mapper.*;
import com.huicai.module.arap.service.ReconciliationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PrepaymentServiceImplTest {

    @Mock private PrepaymentMapper prepaymentMapper;
    @Mock private PayableMapper payableMapper;
    @Mock private ReceivableMapper receivableMapper;
    @Mock private ReconciliationLogMapper logMapper;

    @InjectMocks private PrepaymentServiceImpl service;

    // ==================== 创建预付 ====================

    @Test
    void createPaymentPrepay_正常创建_返回实体() {
        when(prepaymentMapper.insert(any(PrepaymentEntity.class))).thenReturn(1);
        PrepaymentEntity result = service.createPaymentPrepay(
                10L, new BigDecimal("5000"), "202606",
                LocalDate.of(2026, 6, 15), "测试预付",
                "bank_txn", 1L, 100L, 200L, "1");
        assertNotNull(result);
        assertEquals("PAYMENT_PREPAY", result.getPrepayType());
        assertEquals(10L, result.getVendorId());
        assertEquals(0, new BigDecimal("5000").compareTo(result.getAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(result.getSettledAmount()));
        assertEquals("CONFIRMED", result.getStatus());
        verify(prepaymentMapper).insert(any(PrepaymentEntity.class));
    }

    @Test
    void createPaymentPrepay_供应商为空_抛异常() {
        assertThrows(BusinessException.class, () ->
                service.createPaymentPrepay(null, new BigDecimal("100"), "202606",
                        null, "", "", null, null, null, ""));
    }

    @Test
    void createPaymentPrepay_金额为零_抛异常() {
        assertThrows(BusinessException.class, () ->
                service.createPaymentPrepay(1L, BigDecimal.ZERO, "202606",
                        null, "", "", null, null, null, ""));
    }

    // ==================== 创建预收 ====================

    @Test
    void createReceiptPrepay_正常创建_返回实体() {
        when(prepaymentMapper.insert(any(PrepaymentEntity.class))).thenReturn(1);
        PrepaymentEntity result = service.createReceiptPrepay(
                20L, new BigDecimal("8000"), "202606",
                LocalDate.of(2026, 6, 15), "测试预收",
                "bank_txn", 1L, 100L, 200L, "1");
        assertNotNull(result);
        assertEquals("RECEIPT_PREPAY", result.getPrepayType());
        assertEquals(20L, result.getCustomerId());
        assertEquals(0, new BigDecimal("8000").compareTo(result.getAmount()));
        assertEquals("CONFIRMED", result.getStatus());
        verify(prepaymentMapper).insert(any(PrepaymentEntity.class));
    }

    @Test
    void createReceiptPrepay_客户为空_抛异常() {
        assertThrows(BusinessException.class, () ->
                service.createReceiptPrepay(null, new BigDecimal("100"), "202606",
                        null, "", "", null, null, null, ""));
    }

    // ==================== 预付冲应付 ====================

    @Test
    void settlePayable_有未结清应付_冲销成功() {
        PrepaymentEntity prepay = new PrepaymentEntity();
        prepay.setId(1L);
        prepay.setVendorId(5L);
        prepay.setAmount(new BigDecimal("5000"));
        prepay.setSettledAmount(BigDecimal.ZERO);
        prepay.setUnsettledAmount(new BigDecimal("5000"));
        prepay.setStatus("CONFIRMED");
        when(prepaymentMapper.selectById(1L)).thenReturn(prepay);

        PayableEntity payable = new PayableEntity();
        payable.setId(10L);
        payable.setVendorId(5L);
        payable.setAmount(new BigDecimal("3000"));
        payable.setSettledAmount(BigDecimal.ZERO);
        payable.setUnsettledAmount(new BigDecimal("3000"));
        when(payableMapper.selectById(10L)).thenReturn(payable);

        BigDecimal actual = service.settlePayable(1L, 10L, new BigDecimal("2000"), "冲销");

        assertEquals(0, new BigDecimal("2000").compareTo(actual));
        // 预付款更新
        verify(prepaymentMapper).updateById(prepay);
        assertEquals(0, new BigDecimal("2000").compareTo(prepay.getSettledAmount()));
        assertEquals(0, new BigDecimal("3000").compareTo(prepay.getUnsettledAmount()));
        // 应付更新
        verify(payableMapper).updateById(payable);
        assertEquals(0, new BigDecimal("2000").compareTo(payable.getSettledAmount()));
        assertEquals(0, new BigDecimal("1000").compareTo(payable.getUnsettledAmount()));
        // 核销日志
        verify(logMapper).insert(any(ReconciliationLogEntity.class));
    }

    @Test
    void settlePayable_供应商不一致_抛异常() {
        PrepaymentEntity prepay = new PrepaymentEntity();
        prepay.setId(1L);
        prepay.setVendorId(5L);
        prepay.setUnsettledAmount(new BigDecimal("5000"));
        prepay.setStatus("CONFIRMED");
        when(prepaymentMapper.selectById(1L)).thenReturn(prepay);

        PayableEntity payable = new PayableEntity();
        payable.setId(10L);
        payable.setVendorId(99L); // 不一致
        payable.setUnsettledAmount(new BigDecimal("3000"));
        when(payableMapper.selectById(10L)).thenReturn(payable);

        assertThrows(BusinessException.class, () ->
                service.settlePayable(1L, 10L, new BigDecimal("1000"), ""));
    }

    @Test
    void settlePayable_预付款不存在_抛异常() {
        when(prepaymentMapper.selectById(999L)).thenReturn(null);
        assertThrows(BusinessException.class, () ->
                service.settlePayable(999L, 10L, new BigDecimal("100"), ""));
    }

    @Test
    void settlePayable_应付不存在_抛异常() {
        PrepaymentEntity prepay = new PrepaymentEntity();
        prepay.setId(1L);
        prepay.setVendorId(5L);
        prepay.setUnsettledAmount(new BigDecimal("5000"));
        prepay.setStatus("CONFIRMED");
        when(prepaymentMapper.selectById(1L)).thenReturn(prepay);
        when(payableMapper.selectById(10L)).thenReturn(null);

        assertThrows(BusinessException.class, () ->
                service.settlePayable(1L, 10L, new BigDecimal("100"), ""));
    }

    // ==================== 预收冲应收 ====================

    @Test
    void settleReceivable_有未结清应收_冲销成功() {
        PrepaymentEntity prepay = new PrepaymentEntity();
        prepay.setId(1L);
        prepay.setCustomerId(3L);
        prepay.setAmount(new BigDecimal("8000"));
        prepay.setSettledAmount(BigDecimal.ZERO);
        prepay.setUnsettledAmount(new BigDecimal("8000"));
        prepay.setStatus("CONFIRMED");
        when(prepaymentMapper.selectById(1L)).thenReturn(prepay);

        ReceivableEntity recv = new ReceivableEntity();
        recv.setId(20L);
        recv.setCustomerId(3L);
        recv.setAmount(new BigDecimal("5000"));
        recv.setSettledAmount(BigDecimal.ZERO);
        recv.setUnsettledAmount(new BigDecimal("5000"));
        when(receivableMapper.selectById(20L)).thenReturn(recv);

        BigDecimal actual = service.settleReceivable(1L, 20L, new BigDecimal("3000"), "预收冲应收");

        assertEquals(0, new BigDecimal("3000").compareTo(actual));
        verify(prepaymentMapper).updateById(prepay);
        verify(receivableMapper).updateById(recv);
        verify(logMapper).insert(any(ReconciliationLogEntity.class));
    }

    @Test
    void settleReceivable_客户不一致_抛异常() {
        PrepaymentEntity prepay = new PrepaymentEntity();
        prepay.setId(1L);
        prepay.setCustomerId(3L);
        prepay.setUnsettledAmount(new BigDecimal("5000"));
        prepay.setStatus("CONFIRMED");
        when(prepaymentMapper.selectById(1L)).thenReturn(prepay);

        ReceivableEntity recv = new ReceivableEntity();
        recv.setId(20L);
        recv.setCustomerId(99L); // 不一致
        recv.setUnsettledAmount(new BigDecimal("3000"));
        when(receivableMapper.selectById(20L)).thenReturn(recv);

        assertThrows(BusinessException.class, () ->
                service.settleReceivable(1L, 20L, new BigDecimal("1000"), ""));
    }

    // ==================== 列表查询 ====================

    @Test
    void listPaymentPrepay_查询供应商预付款() {
        PrepaymentEntity p = new PrepaymentEntity();
        p.setId(1L);
        p.setPrepayType("PAYMENT_PREPAY");
        p.setVendorId(5L);
        when(prepaymentMapper.selectList(any())).thenReturn(List.of(p));

        List<PrepaymentEntity> result = service.listPaymentPrepay(5L);
        assertEquals(1, result.size());
        verify(prepaymentMapper).selectList(any());
    }

    @Test
    void listReceiptPrepay_查询客户预收款() {
        PrepaymentEntity p = new PrepaymentEntity();
        p.setId(1L);
        p.setPrepayType("RECEIPT_PREPAY");
        p.setCustomerId(3L);
        when(prepaymentMapper.selectList(any())).thenReturn(List.of(p));

        List<PrepaymentEntity> result = service.listReceiptPrepay(3L);
        assertEquals(1, result.size());
        verify(prepaymentMapper).selectList(any());
    }

    @Test
    void getById_查询详情() {
        PrepaymentEntity p = new PrepaymentEntity();
        p.setId(10L);
        when(prepaymentMapper.selectById(10L)).thenReturn(p);
        assertEquals(10L, service.getById(10L).getId());
    }

    // ==================== 确认/取消/反冲销 ====================

    @Test
    void confirm_DRAFT状态_变为CONFIRMED() {
        PrepaymentEntity p = new PrepaymentEntity();
        p.setId(1L);
        p.setStatus("DRAFT");
        when(prepaymentMapper.selectById(1L)).thenReturn(p);

        service.confirm(1L);

        assertEquals("CONFIRMED", p.getStatus());
        verify(prepaymentMapper).updateById(p);
    }

    @Test
    void confirm_非DRAFT_抛异常() {
        PrepaymentEntity p = new PrepaymentEntity();
        p.setId(1L);
        p.setStatus("CONFIRMED");
        when(prepaymentMapper.selectById(1L)).thenReturn(p);
        assertThrows(BusinessException.class, () -> service.confirm(1L));
    }

    @Test
    void cancel_CONFIRMED状态_变为CANCELLED() {
        PrepaymentEntity p = new PrepaymentEntity();
        p.setId(1L);
        p.setStatus("CONFIRMED");
        when(prepaymentMapper.selectById(1L)).thenReturn(p);

        service.cancel(1L);

        assertEquals("CANCELLED", p.getStatus());
        verify(prepaymentMapper).updateById(p);
    }

    @Test
    void cancel_SETTLED状态_抛异常() {
        PrepaymentEntity p = new PrepaymentEntity();
        p.setId(1L);
        p.setStatus("SETTLED");
        when(prepaymentMapper.selectById(1L)).thenReturn(p);
        assertThrows(BusinessException.class, () -> service.cancel(1L));
    }

    @Test
    void reverseSettle_SETTLED_恢复为CONFIRMED() {
        PrepaymentEntity p = new PrepaymentEntity();
        p.setId(1L);
        p.setStatus("SETTLED");
        p.setAmount(new BigDecimal("5000"));
        p.setSettledAmount(new BigDecimal("5000"));
        p.setUnsettledAmount(BigDecimal.ZERO);
        when(prepaymentMapper.selectById(1L)).thenReturn(p);

        service.reverseSettle(1L);

        assertEquals("CONFIRMED", p.getStatus());
        assertEquals(0, BigDecimal.ZERO.compareTo(p.getSettledAmount()));
        assertEquals(0, new BigDecimal("5000").compareTo(p.getUnsettledAmount()));
        verify(prepaymentMapper).updateById(p);
    }

    @Test
    void reverseSettle_不存在_抛异常() {
        when(prepaymentMapper.selectById(99L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.reverseSettle(99L));
    }
}
