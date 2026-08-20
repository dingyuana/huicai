package com.huicai.sme.tax.service.impl;

import com.huicai.common.exception.BusinessException;
import com.huicai.common.test.StateMachineTestHelper;
import com.huicai.base.business.entity.BusinessDocEntity;
import com.huicai.base.voucher.entity.VoucherEntity;
import com.huicai.base.business.mapper.BusinessDocMapper;
import com.huicai.base.voucher.mapper.VoucherEntryMapper;
import com.huicai.base.voucher.mapper.VoucherMapper;
import com.huicai.base.voucher.service.VoucherNoService;
import com.huicai.base.system.entity.Subject;
import com.huicai.base.system.mapper.SubjectMapper;
import com.huicai.sme.tax.constant.InvoiceStatus;
import com.huicai.base.business.entity.InputInvoiceEntity;
import com.huicai.base.business.mapper.InputInvoiceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * InputInvoiceStateMachineServiceImpl 单元测试 (P40).
 *
 * 覆盖全部 7 个状态转换方法，每个方法包含正向断言和负向断言。
 * 与 OutputInvoiceStateMachineServiceImplTest 对称。
 */
@ExtendWith(MockitoExtension.class)
class InputInvoiceStateMachineServiceImplTest {

    @Mock private InputInvoiceMapper invoiceMapper;
    @Mock private BusinessDocMapper businessDocMapper;
    @Mock private VoucherMapper voucherMapper;
    @Mock private VoucherEntryMapper voucherEntryMapper;
    @Mock private VoucherNoService voucherNoService;
    @Mock private SubjectMapper subjectMapper;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;

    private InputInvoiceStateMachineServiceImpl service;

    private static final Long USER_ID = 1L;
    private static final Long INVOICE_ID = 100L;

    @BeforeEach
    void setup() {
        service = new InputInvoiceStateMachineServiceImpl(
                invoiceMapper, businessDocMapper, voucherMapper, voucherEntryMapper,
                voucherNoService, subjectMapper, redisTemplate);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        lenient().when(valueOps.increment(anyString())).thenReturn(1L);
        lenient().when(voucherNoService.generateNextNo(anyString(), anyLong())).thenReturn("FK-TEST-001");
    }

    /** 创建测试进项发票 */
    private InputInvoiceEntity invoice(String status) {
        InputInvoiceEntity e = new InputInvoiceEntity();
        e.setId(INVOICE_ID);
        e.setStatus(status);
        e.setInvoiceDate(LocalDate.of(2026, 7, 1));
        e.setPeriod("202607");
        e.setVendorId(10L);
        e.setVendorName("测试供应商");
        e.setAmount(new BigDecimal("1000.00"));
        e.setTaxRate(new BigDecimal("13"));
        e.setTaxAmount(new BigDecimal("130.00"));
        e.setTotalAmount(new BigDecimal("1130.00"));
        e.setInvoiceNo("TEST-IN-001");
        return e;
    }

    // ===== 1. submitForReview =====

    @Test
    @DisplayName("submitForReview_正向_状态变更正确")
    void submitForReview_positive() {
        InputInvoiceEntity inv = invoice(InvoiceStatus.PENDING_CONFIRM);
        when(invoiceMapper.selectById(INVOICE_ID)).thenReturn(inv);

        service.submitForReview(INVOICE_ID, USER_ID);

        assertEquals(InvoiceStatus.PENDING_REVIEW, inv.getStatus());
        assertEquals(USER_ID, inv.getUpdatedBy());
        verify(invoiceMapper).updateById(inv);
    }

    @Test
    @DisplayName("submitForReview_负向_非待确认状态_抛异常")
    void submitForReview_negative_wrongStatus() {
        when(invoiceMapper.selectById(INVOICE_ID)).thenReturn(invoice(InvoiceStatus.CONFIRMED));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.submitForReview(INVOICE_ID, USER_ID));
        assertTrue(ex.getMessage().contains("仅待确认状态可提交审核"));
    }

    @Test
    @DisplayName("submitForReview_负向_发票不存在_抛异常")
    void submitForReview_negative_notFound() {
        when(invoiceMapper.selectById(INVOICE_ID)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.submitForReview(INVOICE_ID, USER_ID));
        assertTrue(ex.getMessage().contains("进项发票不存在"));
    }

    @Test
    @DisplayName("submitForReview_负向_无副作用")
    void submitForReview_negative_noSideEffects() {
        when(invoiceMapper.selectById(INVOICE_ID)).thenReturn(invoice(InvoiceStatus.PENDING_CONFIRM));

        service.submitForReview(INVOICE_ID, USER_ID);

        StateMachineTestHelper.verifyNoVoucherCreated(voucherMapper, voucherEntryMapper);
        verify(businessDocMapper, never()).insert(any(BusinessDocEntity.class));
    }

    // ===== 2. confirm =====

    @Test
    @DisplayName("confirm_正向_状态变更+创建应付单+创建凭证")
    void confirm_positive() {
        InputInvoiceEntity inv = invoice(InvoiceStatus.PENDING_REVIEW);
        when(invoiceMapper.selectById(INVOICE_ID)).thenReturn(inv);
        when(invoiceMapper.updateById(any(InputInvoiceEntity.class))).thenReturn(1);

        // 模拟科目查询
        Subject subject = new Subject();
        subject.setId(1L);
        subject.setCode("2202");
        subject.setName("应付账款");
        lenient().when(subjectMapper.selectList(any())).thenReturn(List.of(subject));

        service.confirm(INVOICE_ID, USER_ID);

        // 状态变更到 CONFIRMED（然后凭证生成后会改为 VOUCHERED）
        assertEquals(InvoiceStatus.VOUCHERED, inv.getStatus());
        // 创建业务单据
        verify(businessDocMapper).insert(any(BusinessDocEntity.class));
        // 创建凭证
        verify(voucherMapper).insert(any(VoucherEntity.class));
        // 创建凭证分录（借:存货 + 借:进项税 + 贷:应付 = 3条）
        verify(voucherEntryMapper, atLeast(1)).insert(any(com.huicai.base.voucher.entity.VoucherEntryEntity.class));
    }

    @Test
    @DisplayName("confirm_负向_非待审核状态_抛异常")
    void confirm_negative_wrongStatus() {
        when(invoiceMapper.selectById(INVOICE_ID)).thenReturn(invoice(InvoiceStatus.CONFIRMED));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.confirm(INVOICE_ID, USER_ID));
        assertTrue(ex.getMessage().contains("仅待审核状态可确认"));
    }

    // ===== 3. reject =====

    @Test
    @DisplayName("reject_正向_状态回退并记录原因")
    void reject_positive() {
        when(invoiceMapper.selectById(INVOICE_ID)).thenReturn(invoice(InvoiceStatus.PENDING_REVIEW));

        service.reject(INVOICE_ID, USER_ID, "金额有误");

        ArgumentCaptor<InputInvoiceEntity> captor = ArgumentCaptor.forClass(InputInvoiceEntity.class);
        verify(invoiceMapper).updateById(captor.capture());
        assertEquals(InvoiceStatus.PENDING_CONFIRM, captor.getValue().getStatus());
        assertEquals("金额有误", captor.getValue().getRejectReason());
    }

    @Test
    @DisplayName("reject_负向_非待审核状态_抛异常")
    void reject_negative_wrongStatus() {
        when(invoiceMapper.selectById(INVOICE_ID)).thenReturn(invoice(InvoiceStatus.CONFIRMED));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.reject(INVOICE_ID, USER_ID, "原因"));
        assertTrue(ex.getMessage().contains("仅待审核状态可驳回"));
    }

    @Test
    @DisplayName("reject_负向_原因为空_抛异常")
    void reject_negative_emptyReason() {
        lenient().when(invoiceMapper.selectById(INVOICE_ID)).thenReturn(invoice(InvoiceStatus.PENDING_REVIEW));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.reject(INVOICE_ID, USER_ID, "  "));
        assertTrue(ex.getMessage().contains("驳回必须填写原因"));
    }

    @Test
    @DisplayName("reject_负向_无副作用")
    void reject_negative_noSideEffects() {
        when(invoiceMapper.selectById(INVOICE_ID)).thenReturn(invoice(InvoiceStatus.PENDING_REVIEW));

        service.reject(INVOICE_ID, USER_ID, "原因");

        StateMachineTestHelper.verifyNoVoucherCreated(voucherMapper, voucherEntryMapper);
        verify(businessDocMapper, never()).insert(any(BusinessDocEntity.class));
    }

    // ===== 4. revertToReview =====

    @Test
    @DisplayName("revertToReview_正向_状态回退")
    void revertToReview_positive() {
        when(invoiceMapper.selectById(INVOICE_ID)).thenReturn(invoice(InvoiceStatus.CONFIRMED));

        service.revertToReview(INVOICE_ID, USER_ID);

        ArgumentCaptor<InputInvoiceEntity> captor = ArgumentCaptor.forClass(InputInvoiceEntity.class);
        verify(invoiceMapper).updateById(captor.capture());
        assertEquals(InvoiceStatus.PENDING_REVIEW, captor.getValue().getStatus());
    }

    @Test
    @DisplayName("revertToReview_负向_非已确认状态_抛异常")
    void revertToReview_negative_wrongStatus() {
        when(invoiceMapper.selectById(INVOICE_ID)).thenReturn(invoice(InvoiceStatus.PENDING_REVIEW));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.revertToReview(INVOICE_ID, USER_ID));
        assertTrue(ex.getMessage().contains("仅已确认状态可回退到待审核"));
    }

    @Test
    @DisplayName("revertToReview_负向_无副作用")
    void revertToReview_negative_noSideEffects() {
        when(invoiceMapper.selectById(INVOICE_ID)).thenReturn(invoice(InvoiceStatus.CONFIRMED));

        service.revertToReview(INVOICE_ID, USER_ID);

        StateMachineTestHelper.verifyNoVoucherCreated(voucherMapper, voucherEntryMapper);
        verify(businessDocMapper, never()).insert(any(BusinessDocEntity.class));
    }

    // ===== 5. markVouchered =====

    @Test
    @DisplayName("markVouchered_正向_状态变更并记录凭证ID")
    void markVouchered_positive() {
        when(invoiceMapper.selectById(INVOICE_ID)).thenReturn(invoice(InvoiceStatus.CONFIRMED));

        service.markVouchered(INVOICE_ID, 200L, "VCH-TEST-001", USER_ID);

        ArgumentCaptor<InputInvoiceEntity> captor = ArgumentCaptor.forClass(InputInvoiceEntity.class);
        verify(invoiceMapper).updateById(captor.capture());
        assertEquals(InvoiceStatus.VOUCHERED, captor.getValue().getStatus());
        assertEquals(200L, captor.getValue().getVoucherId());
        assertEquals("VCH-TEST-001", captor.getValue().getVoucherNo());
    }

    @Test
    @DisplayName("markVouchered_负向_非已确认状态_抛异常")
    void markVouchered_negative_wrongStatus() {
        when(invoiceMapper.selectById(INVOICE_ID)).thenReturn(invoice(InvoiceStatus.VOUCHERED));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.markVouchered(INVOICE_ID, 200L, "VCH-001", USER_ID));
        assertTrue(ex.getMessage().contains("仅已确认状态可生成凭证"));
    }

    // ===== 6. onReconciliationUpdate =====

    @Test
    @DisplayName("onReconciliationUpdate_正向_全额核销")
    void onReconciliationUpdate_positive_fully() {
        when(invoiceMapper.selectById(INVOICE_ID)).thenReturn(invoice(InvoiceStatus.VOUCHERED));

        service.onReconciliationUpdate(INVOICE_ID, BigDecimal.ZERO, USER_ID);

        ArgumentCaptor<InputInvoiceEntity> captor = ArgumentCaptor.forClass(InputInvoiceEntity.class);
        verify(invoiceMapper).updateById(captor.capture());
        assertEquals(InvoiceStatus.FULLY_RECONCILED, captor.getValue().getStatus());
    }

    @Test
    @DisplayName("onReconciliationUpdate_正向_部分核销")
    void onReconciliationUpdate_positive_partially() {
        when(invoiceMapper.selectById(INVOICE_ID)).thenReturn(invoice(InvoiceStatus.VOUCHERED));

        service.onReconciliationUpdate(INVOICE_ID, new BigDecimal("500.00"), USER_ID);

        ArgumentCaptor<InputInvoiceEntity> captor = ArgumentCaptor.forClass(InputInvoiceEntity.class);
        verify(invoiceMapper).updateById(captor.capture());
        assertEquals(InvoiceStatus.PARTIALLY_RECONCILED, captor.getValue().getStatus());
    }

    @Test
    @DisplayName("onReconciliationUpdate_负向_非已生成凭证状态_跳过")
    void onReconciliationUpdate_negative_skipIfNotVouchered() {
        when(invoiceMapper.selectById(INVOICE_ID)).thenReturn(invoice(InvoiceStatus.CONFIRMED));

        service.onReconciliationUpdate(INVOICE_ID, BigDecimal.ZERO, USER_ID);

        // 非VOUCHERED状态应该跳过，不更新
        verify(invoiceMapper, never()).updateById(any(InputInvoiceEntity.class));
    }

    // ===== 7. voidInvoice =====

    @Test
    @DisplayName("voidInvoice_正向_待确认状态可作废")
    void voidInvoice_positive() {
        when(invoiceMapper.selectById(INVOICE_ID)).thenReturn(invoice(InvoiceStatus.PENDING_CONFIRM));

        service.voidInvoice(INVOICE_ID, USER_ID, "开错发票");

        ArgumentCaptor<InputInvoiceEntity> captor = ArgumentCaptor.forClass(InputInvoiceEntity.class);
        verify(invoiceMapper).updateById(captor.capture());
        assertEquals(InvoiceStatus.VOIDED, captor.getValue().getStatus());
        assertTrue(captor.getValue().getRejectReason().contains("开错发票"));
    }

    @Test
    @DisplayName("voidInvoice_负向_终态不可作废")
    void voidInvoice_negative_terminal() {
        when(invoiceMapper.selectById(INVOICE_ID)).thenReturn(invoice(InvoiceStatus.VOIDED));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.voidInvoice(INVOICE_ID, USER_ID, "原因"));
        assertTrue(ex.getMessage().contains("当前状态不可作废"));
    }

    @Test
    @DisplayName("voidInvoice_负向_原因为空_抛异常")
    void voidInvoice_negative_emptyReason() {
        lenient().when(invoiceMapper.selectById(INVOICE_ID)).thenReturn(invoice(InvoiceStatus.PENDING_CONFIRM));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.voidInvoice(INVOICE_ID, USER_ID, ""));
        assertTrue(ex.getMessage().contains("作废必须填写原因"));
    }

    @Test
    @DisplayName("voidInvoice_负向_无副作用")
    void voidInvoice_negative_noSideEffects() {
        when(invoiceMapper.selectById(INVOICE_ID)).thenReturn(invoice(InvoiceStatus.PENDING_CONFIRM));

        service.voidInvoice(INVOICE_ID, USER_ID, "原因");

        StateMachineTestHelper.verifyNoVoucherCreated(voucherMapper, voucherEntryMapper);
        verify(businessDocMapper, never()).insert(any(BusinessDocEntity.class));
    }

    // ==================== reverseInvoice (P36 红冲链路) ====================

    @Test
    @DisplayName("reverseInvoice_正向_生成红字发票_原票标记REVERSED")
    void reverseInvoice_positive_redInvoice_created() {
        InputInvoiceEntity original = invoice(InvoiceStatus.CONFIRMED);
        original.setAmount(new BigDecimal("1000.00"));
        original.setTaxAmount(new BigDecimal("130.00"));
        original.setTotalAmount(new BigDecimal("1130.00"));
        original.setInvoiceNo("JM202607000001");
        when(invoiceMapper.selectById(INVOICE_ID)).thenReturn(original);
        when(invoiceMapper.insert(any(InputInvoiceEntity.class))).thenAnswer(inv -> {
            InputInvoiceEntity e = inv.getArgument(0);
            e.setId(200L);
            return 1;
        });
        when(invoiceMapper.updateById(any(InputInvoiceEntity.class))).thenReturn(1);

        Long redId = service.reverseInvoice(INVOICE_ID, USER_ID, "数量错误");

        assertNotNull(redId);
        assertEquals(200L, redId);
        verify(invoiceMapper).insert(any(InputInvoiceEntity.class));
        ArgumentCaptor<InputInvoiceEntity> captor = ArgumentCaptor.forClass(InputInvoiceEntity.class);
        verify(invoiceMapper).updateById(captor.capture());
        assertEquals(InvoiceStatus.REVERSED, captor.getValue().getStatus());
    }

    @Test
    @DisplayName("reverseInvoice_正向_红字发票金额为负数")
    void reverseInvoice_positive_redAmount_negative() {
        InputInvoiceEntity original = invoice(InvoiceStatus.VOUCHERED);
        original.setAmount(new BigDecimal("5000.00"));
        original.setTaxAmount(new BigDecimal("650.00"));
        original.setTotalAmount(new BigDecimal("5650.00"));
        when(invoiceMapper.selectById(INVOICE_ID)).thenReturn(original);
        when(invoiceMapper.insert(any(InputInvoiceEntity.class))).thenAnswer(inv -> {
            InputInvoiceEntity e = inv.getArgument(0);
            e.setId(300L);
            return 1;
        });
        when(invoiceMapper.updateById(any(InputInvoiceEntity.class))).thenReturn(1);

        service.reverseInvoice(INVOICE_ID, USER_ID, "退货");

        ArgumentCaptor<InputInvoiceEntity> captor = ArgumentCaptor.forClass(InputInvoiceEntity.class);
        verify(invoiceMapper).insert(captor.capture());
        InputInvoiceEntity red = captor.getValue();
        assertEquals(new BigDecimal("-5000.00"), red.getAmount());
        assertEquals(new BigDecimal("-650.00"), red.getTaxAmount());
        assertEquals(new BigDecimal("-5650.00"), red.getTotalAmount());
        assertEquals("PENDING_CONFIRM", red.getStatus());
    }

    @Test
    @DisplayName("reverseInvoice_正向_红字发票编号追加-R后缀")
    void reverseInvoice_positive_redInvoiceNo_suffix() {
        InputInvoiceEntity original = invoice(InvoiceStatus.CONFIRMED);
        original.setAmount(new BigDecimal("100.00"));
        original.setTaxAmount(BigDecimal.ZERO);
        original.setTotalAmount(new BigDecimal("100.00"));
        original.setInvoiceNo("JM202607000001");
        when(invoiceMapper.selectById(INVOICE_ID)).thenReturn(original);
        when(invoiceMapper.insert(any(InputInvoiceEntity.class))).thenAnswer(inv -> {
            InputInvoiceEntity e = inv.getArgument(0);
            e.setId(400L);
            return 1;
        });
        when(invoiceMapper.updateById(any(InputInvoiceEntity.class))).thenReturn(1);

        service.reverseInvoice(INVOICE_ID, USER_ID, "测试");

        ArgumentCaptor<InputInvoiceEntity> captor = ArgumentCaptor.forClass(InputInvoiceEntity.class);
        verify(invoiceMapper).insert(captor.capture());
        assertEquals("JM202607000001-R", captor.getValue().getInvoiceNo());
    }

    @Test
    @DisplayName("reverseInvoice_负向_空原因抛异常")
    void reverseInvoice_negative_emptyReason() {
        lenient().when(invoiceMapper.selectById(INVOICE_ID)).thenReturn(invoice(InvoiceStatus.CONFIRMED));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.reverseInvoice(INVOICE_ID, USER_ID, ""));
        assertTrue(ex.getMessage().contains("原因"));
        verify(invoiceMapper, never()).insert(any(InputInvoiceEntity.class));
    }

    @Test
    @DisplayName("reverseInvoice_负向_null原因抛异常")
    void reverseInvoice_negative_nullReason() {
        assertThrows(BusinessException.class,
                () -> service.reverseInvoice(INVOICE_ID, USER_ID, null));
    }

    @Test
    @DisplayName("reverseInvoice_负向_待确认状态不可红冲")
    void reverseInvoice_negative_pendingConfirm() {
        when(invoiceMapper.selectById(INVOICE_ID)).thenReturn(invoice(InvoiceStatus.PENDING_CONFIRM));

        assertThrows(BusinessException.class, () -> service.reverseInvoice(INVOICE_ID, USER_ID, "原因"));
        verify(invoiceMapper, never()).insert(any(InputInvoiceEntity.class));
    }

    @Test
    @DisplayName("reverseInvoice_负向_已作废状态不可红冲")
    void reverseInvoice_negative_voided() {
        when(invoiceMapper.selectById(INVOICE_ID)).thenReturn(invoice(InvoiceStatus.VOIDED));

        assertThrows(BusinessException.class, () -> service.reverseInvoice(INVOICE_ID, USER_ID, "原因"));
        verify(invoiceMapper, never()).insert(any(InputInvoiceEntity.class));
    }
}
