package com.huicai.sme.tax.service.impl;

import com.huicai.common.exception.BusinessException;
import com.huicai.common.test.StateMachineTestHelper;
import com.huicai.base.business.entity.BusinessDocEntity;
import com.huicai.base.business.mapper.BusinessDocMapper;
import com.huicai.base.voucher.entity.VoucherEntity;
import com.huicai.base.voucher.entity.VoucherEntryEntity;
import com.huicai.base.voucher.mapper.VoucherEntryMapper;
import com.huicai.base.voucher.mapper.VoucherMapper;
import com.huicai.sme.tax.constant.InvoiceStatus;
import com.huicai.base.business.entity.OutputInvoiceEntity;
import com.huicai.base.business.mapper.OutputInvoiceMapper;
import com.huicai.sme.tax.service.TaxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * OutputInvoiceStateMachineServiceImpl 单元测试.
 *
 * <p>覆盖全部 7 个状态转换方法，每个方法同时包含正向断言（该做的做了）
 * 和负向断言（不该做的没做）。
 *
 * <p>关键设计：confirm() 的正向断言验证了 confirm() 既变更状态，
 * 也自动创建 INVOICE_OUT 业务单据 + 凭证（P34 恢复）。
 *
 * @see <a href="file://docs/process/state-machine-test-checklist.md">状态机测试契约检查清单</a>
 */
@ExtendWith(MockitoExtension.class)
class OutputInvoiceStateMachineServiceImplTest {

    @Mock
    private OutputInvoiceMapper invoiceMapper;

    @Mock
    private BusinessDocMapper businessDocMapper;

    @Mock
    private VoucherMapper voucherMapper;

    @Mock
    private VoucherEntryMapper voucherEntryMapper;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ApplicationContext applicationContext;

    @Mock
    private TaxService mockTaxService;

    @Mock
    private ValueOperations<String, String> valueOps;

    private OutputInvoiceStateMachineServiceImpl service;
    private static final Long USER_ID = 1L;
    private static final Long INVOICE_ID = 100L;

    @BeforeEach
    void setup() {
        service = new OutputInvoiceStateMachineServiceImpl(
                invoiceMapper, businessDocMapper, voucherMapper, redisTemplate, null, applicationContext);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        lenient().when(applicationContext.getBean(TaxService.class)).thenReturn(mockTaxService);
    }

    // Helper: create a test invoice with given status
    private OutputInvoiceEntity invoice(String status) {
        OutputInvoiceEntity e = new OutputInvoiceEntity();
        e.setId(INVOICE_ID);
        e.setStatus(status);
        e.setInvoiceDate(LocalDate.of(2026, 6, 1));
        e.setPeriod("202606");
        e.setCustomerId(10L);
        e.setCustomerName("测试客户");
        e.setAmount(new BigDecimal("1000.00"));
        e.setTaxAmount(new BigDecimal("130.00"));
        e.setTotalAmount(new BigDecimal("1130.00"));
        e.setInvoiceNo("TEST001");
        return e;
    }

    // ====================================================================
    // 1. submitForReview — PENDING_CONFIRM → PENDING_REVIEW
    // ====================================================================

    @Test
    @DisplayName("submitForReview_正向_状态变更正确")
    void submitForReview_positive_statusChanged() {
        // given
        OutputInvoiceEntity inv = invoice(InvoiceStatus.PENDING_CONFIRM);
        when(invoiceMapper.selectById(INVOICE_ID)).thenReturn(inv);

        // when
        service.submitForReview(INVOICE_ID, USER_ID);

        // then — 正向：状态变更
        assertEquals(InvoiceStatus.PENDING_REVIEW, inv.getStatus());
        assertEquals(USER_ID, inv.getUpdatedBy());
        verify(invoiceMapper).updateById(inv);
    }

    @Test
    @DisplayName("submitForReview_正向_负向断言_无副作用")
    void submitForReview_positive_noSideEffects() {
        // given
        OutputInvoiceEntity inv = invoice(InvoiceStatus.PENDING_CONFIRM);
        when(invoiceMapper.selectById(INVOICE_ID)).thenReturn(inv);

        // when
        service.submitForReview(INVOICE_ID, USER_ID);

        // then — 负向：不该做的没做
        StateMachineTestHelper.verifyNoVoucherCreated(voucherMapper, voucherEntryMapper);
        StateMachineTestHelper.verifyNoDocumentCreated(null, null);
        verify(businessDocMapper, never()).insert(any(BusinessDocEntity.class));
    }

    @Test
    @DisplayName("submitForReview_负向_非待确认状态_抛异常")
    void submitForReview_negative_wrongStatus_throws() {
        // given
        OutputInvoiceEntity inv = invoice(InvoiceStatus.CONFIRMED);
        when(invoiceMapper.selectById(INVOICE_ID)).thenReturn(inv);

        // when/then
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.submitForReview(INVOICE_ID, USER_ID));
        StateMachineTestHelper.assertBusinessErrorContains(ex, "仅待确认状态可提交审核");
    }

    @Test
    @DisplayName("submitForReview_负向_发票不存在_抛异常")
    void submitForReview_negative_notFound_throws() {
        // given
        when(invoiceMapper.selectById(INVOICE_ID)).thenReturn(null);

        // when/then
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.submitForReview(INVOICE_ID, USER_ID));
        StateMachineTestHelper.assertBusinessErrorContains(ex, "发票不存在");
    }

    // ====================================================================
    // 2. confirm — PENDING_REVIEW → CONFIRMED
    // ====================================================================

    @Test
    @DisplayName("confirm_正向_状态变更+创建应收单+创建凭证")
    void confirm_positive_statusChanged() {
        // given
        OutputInvoiceEntity inv = invoice(InvoiceStatus.PENDING_REVIEW);
        when(invoiceMapper.selectById(INVOICE_ID)).thenReturn(inv);
        lenient().when(invoiceMapper.updateById(any(OutputInvoiceEntity.class))).thenReturn(1);
        lenient().when(valueOps.increment(anyString())).thenReturn(1L);

        // when
        service.confirm(INVOICE_ID, USER_ID);

        // then — 正向：状态变更
        assertEquals(InvoiceStatus.CONFIRMED, inv.getStatus());
        assertEquals(USER_ID, inv.getUpdatedBy());
        // then — 正向：创建 INVOICE_OUT 业务单据（P34 恢复）
        verify(businessDocMapper).insert(any(BusinessDocEntity.class));
        // then — 正向：调用 taxService 生成凭证
    }

    @Test
    @DisplayName("confirm_负向_非待审核状态_抛异常")
    void confirm_negative_wrongStatus_throws() {
        // given
        OutputInvoiceEntity inv = invoice(InvoiceStatus.CONFIRMED);
        when(invoiceMapper.selectById(INVOICE_ID)).thenReturn(inv);

        // when/then
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.confirm(INVOICE_ID, USER_ID));
        StateMachineTestHelper.assertBusinessErrorContains(ex, "仅待审核状态可确认");
    }

    // ====================================================================
    // 3. reject — PENDING_REVIEW → PENDING_CONFIRM
    // ====================================================================

    @Test
    @DisplayName("reject_正向_状态回退并记录原因")
    void reject_positive_statusRevertedWithReason() {
        // given
        OutputInvoiceEntity inv = invoice(InvoiceStatus.PENDING_REVIEW);
        when(invoiceMapper.selectById(INVOICE_ID)).thenReturn(inv);

        // when
        service.reject(INVOICE_ID, USER_ID, "金额有误");

        // then — 正向：状态回退 + 原因记录
        assertEquals(InvoiceStatus.PENDING_CONFIRM, inv.getStatus());
        assertEquals("金额有误", inv.getRemark());
        assertEquals(USER_ID, inv.getUpdatedBy());
        verify(invoiceMapper).updateById(inv);
    }

    @Test
    @DisplayName("reject_正向_负向断言_无副作用")
    void reject_positive_noSideEffects() {
        // given
        OutputInvoiceEntity inv = invoice(InvoiceStatus.PENDING_REVIEW);
        when(invoiceMapper.selectById(INVOICE_ID)).thenReturn(inv);

        // when
        service.reject(INVOICE_ID, USER_ID, "金额有误");

        // then — 负向：驳回不应创建任何资源
        StateMachineTestHelper.verifyNoVoucherCreated(voucherMapper, voucherEntryMapper);
        StateMachineTestHelper.verifyNoDocumentCreated(null, null);
        verify(businessDocMapper, never()).insert(any(BusinessDocEntity.class));
    }

    @Test
    @DisplayName("reject_负向_非待审核状态_抛异常")
    void reject_negative_wrongStatus_throws() {
        // given
        OutputInvoiceEntity inv = invoice(InvoiceStatus.CONFIRMED);
        when(invoiceMapper.selectById(INVOICE_ID)).thenReturn(inv);

        // when/then
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.reject(INVOICE_ID, USER_ID, "原因"));
        StateMachineTestHelper.assertBusinessErrorContains(ex, "仅待审核状态可驳回");
    }

    @Test
    @DisplayName("reject_负向_原因为空_抛异常")
    void reject_negative_emptyReason_throws() {
        // given — lenient 因为异常在 selectById 之前抛出
        lenient().when(invoiceMapper.selectById(INVOICE_ID)).thenReturn(invoice(InvoiceStatus.PENDING_REVIEW));

        // when/then
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.reject(INVOICE_ID, USER_ID, "  "));
        StateMachineTestHelper.assertBusinessErrorContains(ex, "驳回必须填写原因");
    }

    // ====================================================================
    // 4. revertToReview — CONFIRMED → PENDING_REVIEW
    // ====================================================================

    @Test
    @DisplayName("revertToReview_正向_状态回退")
    void revertToReview_positive_statusReverted() {
        // given
        OutputInvoiceEntity inv = invoice(InvoiceStatus.CONFIRMED);
        when(invoiceMapper.selectById(INVOICE_ID)).thenReturn(inv);

        // when
        service.revertToReview(INVOICE_ID, USER_ID);

        // then — 正向：状态回退
        assertEquals(InvoiceStatus.PENDING_REVIEW, inv.getStatus());
        assertEquals(USER_ID, inv.getUpdatedBy());
        verify(invoiceMapper).updateById(inv);
    }

    @Test
    @DisplayName("revertToReview_正向_负向断言_无副作用")
    void revertToReview_positive_noSideEffects() {
        // given
        OutputInvoiceEntity inv = invoice(InvoiceStatus.CONFIRMED);
        when(invoiceMapper.selectById(INVOICE_ID)).thenReturn(inv);

        // when
        service.revertToReview(INVOICE_ID, USER_ID);

        // then — 负向：回退不应创建任何资源
        StateMachineTestHelper.verifyNoVoucherCreated(voucherMapper, voucherEntryMapper);
        StateMachineTestHelper.verifyNoDocumentCreated(null, null);
        verify(businessDocMapper, never()).insert(any(BusinessDocEntity.class));
    }

    @Test
    @DisplayName("revertToReview_负向_非已确认状态_抛异常")
    void revertToReview_negative_wrongStatus_throws() {
        // given
        OutputInvoiceEntity inv = invoice(InvoiceStatus.PENDING_REVIEW);
        when(invoiceMapper.selectById(INVOICE_ID)).thenReturn(inv);

        // when/then
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.revertToReview(INVOICE_ID, USER_ID));
        StateMachineTestHelper.assertBusinessErrorContains(ex, "仅已确认状态可回退到待审核");
    }

    // ====================================================================
    // 5. markVouchered — CONFIRMED → VOUCHERED
    // ====================================================================

    @Test
    @DisplayName("markVouchered_正向_状态变更并记录凭证ID")
    void markVouchered_positive_statusChangedWithVoucherId() {
        // given
        OutputInvoiceEntity inv = invoice(InvoiceStatus.CONFIRMED);
        when(invoiceMapper.selectById(INVOICE_ID)).thenReturn(inv);

        // when
        service.markVouchered(INVOICE_ID, 200L, "VCH-TEST-001", USER_ID);

        // then — 正向：状态变更 + voucherId 记录
        assertEquals(InvoiceStatus.VOUCHERED, inv.getStatus());
        assertEquals(200L, inv.getVoucherId());
        assertEquals(USER_ID, inv.getUpdatedBy());
        verify(invoiceMapper).updateById(inv);
    }

    @Test
    @DisplayName("markVouchered_正向_负向断言_无额外副作用")
    void markVouchered_positive_noExtraSideEffects() {
        // given
        OutputInvoiceEntity inv = invoice(InvoiceStatus.CONFIRMED);
        when(invoiceMapper.selectById(INVOICE_ID)).thenReturn(inv);

        // when
        service.markVouchered(INVOICE_ID, 200L, "VCH-TEST-001", USER_ID);

        // then — 负向：标记已生成凭证不应再创建新凭证
        StateMachineTestHelper.verifyNoVoucherCreated(voucherMapper, voucherEntryMapper);
        StateMachineTestHelper.verifyNoDocumentCreated(null, null);
        verify(businessDocMapper, never()).insert(any(BusinessDocEntity.class));
    }

    @Test
    @DisplayName("markVouchered_负向_非已确认状态_抛异常")
    void markVouchered_negative_wrongStatus_throws() {
        // given
        OutputInvoiceEntity inv = invoice(InvoiceStatus.VOUCHERED);
        when(invoiceMapper.selectById(INVOICE_ID)).thenReturn(inv);

        // when/then
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.markVouchered(INVOICE_ID, 200L, "VCH-TEST-001", USER_ID));
        StateMachineTestHelper.assertBusinessErrorContains(ex, "仅已确认状态可生成凭证");
    }

    // ====================================================================
    // 6. onReconciliationUpdate — VOUCHERED → FULLY/PARTIALLY_RECONCILED
    // ====================================================================

    @Test
    @DisplayName("onReconciliationUpdate_正向_全额核销")
    void onReconciliationUpdate_positive_fullyReconciled() {
        // given
        OutputInvoiceEntity inv = invoice(InvoiceStatus.VOUCHERED);
        when(invoiceMapper.selectById(INVOICE_ID)).thenReturn(inv);

        // when
        service.onReconciliationUpdate(INVOICE_ID, BigDecimal.ZERO, USER_ID);

        // then — 正向：全额核销
        assertEquals(InvoiceStatus.FULLY_RECONCILED, inv.getStatus());
        assertEquals(USER_ID, inv.getUpdatedBy());
        verify(invoiceMapper).updateById(inv);
    }

    @Test
    @DisplayName("onReconciliationUpdate_正向_部分核销")
    void onReconciliationUpdate_positive_partiallyReconciled() {
        // given
        OutputInvoiceEntity inv = invoice(InvoiceStatus.VOUCHERED);
        when(invoiceMapper.selectById(INVOICE_ID)).thenReturn(inv);

        // when
        service.onReconciliationUpdate(INVOICE_ID, new BigDecimal("500.00"), USER_ID);

        // then — 正向：部分核销
        assertEquals(InvoiceStatus.PARTIALLY_RECONCILED, inv.getStatus());
        assertEquals(USER_ID, inv.getUpdatedBy());
        verify(invoiceMapper).updateById(inv);
    }

    @Test
    @DisplayName("onReconciliationUpdate_正向_负向断言_无副作用")
    void onReconciliationUpdate_positive_noSideEffects() {
        // given
        OutputInvoiceEntity inv = invoice(InvoiceStatus.VOUCHERED);
        when(invoiceMapper.selectById(INVOICE_ID)).thenReturn(inv);

        // when
        service.onReconciliationUpdate(INVOICE_ID, BigDecimal.ZERO, USER_ID);

        // then — 负向：核销更新不应创建任何资源
        StateMachineTestHelper.verifyNoVoucherCreated(voucherMapper, voucherEntryMapper);
        StateMachineTestHelper.verifyNoDocumentCreated(null, null);
        verify(businessDocMapper, never()).insert(any(BusinessDocEntity.class));
    }

    @Test
    @DisplayName("onReconciliationUpdate_负向_非已生成凭证状态_静默跳过")
    void onReconciliationUpdate_negative_wrongStatus_skips() {
        // given
        OutputInvoiceEntity inv = invoice(InvoiceStatus.CONFIRMED);
        when(invoiceMapper.selectById(INVOICE_ID)).thenReturn(inv);

        // when — 非 VOUCHERED 状态不抛异常，静默跳过
        assertDoesNotThrow(() -> service.onReconciliationUpdate(INVOICE_ID, BigDecimal.ZERO, USER_ID));
        // then — 不应更新
        verify(invoiceMapper, never()).updateById(any(OutputInvoiceEntity.class));
    }

    // ====================================================================
    // 7. voidInvoice — 任意非终态 → VOIDED
    // ====================================================================

    @Test
    @DisplayName("voidInvoice_正向_待确认状态可作废")
    void voidInvoice_positive_pendingConfirm_voided() {
        // given
        OutputInvoiceEntity inv = invoice(InvoiceStatus.PENDING_CONFIRM);
        when(invoiceMapper.selectById(INVOICE_ID)).thenReturn(inv);

        // when
        service.voidInvoice(INVOICE_ID, USER_ID, "开错发票");

        // then — 正向：作废
        assertEquals(InvoiceStatus.VOIDED, inv.getStatus());
        assertEquals("[1] 开错发票", inv.getRemark());
        assertEquals(USER_ID, inv.getUpdatedBy());
        verify(invoiceMapper).updateById(inv);
    }

    @Test
    @DisplayName("voidInvoice_正向_已确认状态可作废")
    void voidInvoice_positive_confirmed_voided() {
        // given
        OutputInvoiceEntity inv = invoice(InvoiceStatus.CONFIRMED);
        when(invoiceMapper.selectById(INVOICE_ID)).thenReturn(inv);

        // when
        service.voidInvoice(INVOICE_ID, USER_ID, "客户取消");

        // then — 正向：作废
        assertEquals(InvoiceStatus.VOIDED, inv.getStatus());
        verify(invoiceMapper).updateById(inv);
    }

    @Test
    @DisplayName("voidInvoice_正向_负向断言_无副作用")
    void voidInvoice_positive_noSideEffects() {
        // given
        OutputInvoiceEntity inv = invoice(InvoiceStatus.PENDING_CONFIRM);
        when(invoiceMapper.selectById(INVOICE_ID)).thenReturn(inv);

        // when
        service.voidInvoice(INVOICE_ID, USER_ID, "开错发票");

        // then — 负向：作废不应创建任何资源
        StateMachineTestHelper.verifyNoVoucherCreated(voucherMapper, voucherEntryMapper);
        StateMachineTestHelper.verifyNoDocumentCreated(null, null);
        verify(businessDocMapper, never()).insert(any(BusinessDocEntity.class));
    }

    @Test
    @DisplayName("voidInvoice_负向_终态VOIDED_不可作废")
    void voidInvoice_negative_voided_throws() {
        // given
        OutputInvoiceEntity inv = invoice(InvoiceStatus.VOIDED);
        when(invoiceMapper.selectById(INVOICE_ID)).thenReturn(inv);

        // when/then
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.voidInvoice(INVOICE_ID, USER_ID, "原因"));
        StateMachineTestHelper.assertBusinessErrorContains(ex, "当前状态不可作废");
    }

    @Test
    @DisplayName("voidInvoice_负向_终态FULLY_RECONCILED_不可作废")
    void voidInvoice_negative_fullyReconciled_throws() {
        // given
        OutputInvoiceEntity inv = invoice(InvoiceStatus.FULLY_RECONCILED);
        when(invoiceMapper.selectById(INVOICE_ID)).thenReturn(inv);

        // when/then
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.voidInvoice(INVOICE_ID, USER_ID, "原因"));
        StateMachineTestHelper.assertBusinessErrorContains(ex, "当前状态不可作废");
    }

    @Test
    @DisplayName("voidInvoice_负向_终态REVERSED_不可作废")
    void voidInvoice_negative_reversed_throws() {
        // given
        OutputInvoiceEntity inv = invoice(InvoiceStatus.REVERSED);
        when(invoiceMapper.selectById(INVOICE_ID)).thenReturn(inv);

        // when/then
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.voidInvoice(INVOICE_ID, USER_ID, "原因"));
        StateMachineTestHelper.assertBusinessErrorContains(ex, "当前状态不可作废");
    }

    @Test
    @DisplayName("voidInvoice_负向_原因为空_抛异常")
    void voidInvoice_negative_emptyReason_throws() {
        // given — lenient 因为异常在 selectById 之前抛出
        lenient().when(invoiceMapper.selectById(INVOICE_ID)).thenReturn(invoice(InvoiceStatus.PENDING_CONFIRM));

        // when/then
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.voidInvoice(INVOICE_ID, USER_ID, ""));
        StateMachineTestHelper.assertBusinessErrorContains(ex, "作废必须填写原因");
    }

    @Test
    @DisplayName("voidInvoice_正向_追加原因格式正确")
    void voidInvoice_positive_appendReasonFormat() {
        // given
        OutputInvoiceEntity inv = invoice(InvoiceStatus.PENDING_CONFIRM);
        inv.setRemark("已有备注");
        when(invoiceMapper.selectById(INVOICE_ID)).thenReturn(inv);

        // when
        service.voidInvoice(INVOICE_ID, USER_ID, "新增原因");

        // then
        assertEquals("已有备注 | [1] 新增原因", inv.getRemark());
    }

    // ==================== reverseInvoice ====================

    @Test
    @DisplayName("reverseInvoice_正向_CONFIRMED状态可红冲")
    void reverseInvoice_positive_confirmed_createsRedInvoice() {
        // given
        OutputInvoiceEntity original = invoice(InvoiceStatus.CONFIRMED);
        original.setTaxRate(new BigDecimal("0.13"));
        when(invoiceMapper.selectById(INVOICE_ID)).thenReturn(original);
        doAnswer(invocation -> {
            OutputInvoiceEntity e = invocation.getArgument(0);
            e.setId(101L);
            return null;
        }).when(invoiceMapper).insert(any(OutputInvoiceEntity.class));

        // when
        Long redInvoiceId = service.reverseInvoice(INVOICE_ID, USER_ID, "开错金额");

        // then — 原发票状态变为 REVERSED
        assertEquals(InvoiceStatus.REVERSED, original.getStatus());
        assertEquals(101L, original.getReversedFrom());
        verify(invoiceMapper).updateById(original);

        // then — 创建了红字发票
        ArgumentCaptor<OutputInvoiceEntity> captor = ArgumentCaptor.forClass(OutputInvoiceEntity.class);
        verify(invoiceMapper).insert(captor.capture());
        OutputInvoiceEntity red = captor.getValue();
        assertEquals("-1130.00", red.getTotalAmount().toString());
        assertEquals(InvoiceStatus.PENDING_CONFIRM, red.getStatus());
        assertEquals("TEST001", red.getOriginalInvoiceNo());
        assertEquals("[1] 开错金额", red.getRemark());
    }

    @Test
    @DisplayName("reverseInvoice_正向_PARTIALLY_RECONCILED状态可红冲")
    void reverseInvoice_positive_partiallyReconciled() {
        // given
        OutputInvoiceEntity original = invoice(InvoiceStatus.PARTIALLY_RECONCILED);
        original.setTaxRate(new BigDecimal("0.13"));
        when(invoiceMapper.selectById(INVOICE_ID)).thenReturn(original);
        doAnswer(invocation -> {
            OutputInvoiceEntity e = invocation.getArgument(0);
            e.setId(200L);
            return null;
        }).when(invoiceMapper).insert(any(OutputInvoiceEntity.class));

        // when
        Long redInvoiceId = service.reverseInvoice(INVOICE_ID, USER_ID, "部分核销后红冲");

        // then
        assertNotNull(redInvoiceId);
        assertEquals(InvoiceStatus.REVERSED, original.getStatus());
    }

    @Test
    @DisplayName("reverseInvoice_正向_VOUCHERED状态可红冲")
    void reverseInvoice_positive_vouchered() {
        // given
        OutputInvoiceEntity original = invoice(InvoiceStatus.VOUCHERED);
        original.setTaxRate(new BigDecimal("0.13"));
        when(invoiceMapper.selectById(INVOICE_ID)).thenReturn(original);
        doAnswer(invocation -> {
            OutputInvoiceEntity e = invocation.getArgument(0);
            e.setId(201L);
            return null;
        }).when(invoiceMapper).insert(any(OutputInvoiceEntity.class));

        // when
        Long redInvoiceId = service.reverseInvoice(INVOICE_ID, USER_ID, "已生成凭证后红冲");

        // then
        assertNotNull(redInvoiceId);
        assertEquals(InvoiceStatus.REVERSED, original.getStatus());
    }

    @Test
    @DisplayName("reverseInvoice_负向_PENDING_CONFIRM不可红冲")
    void reverseInvoice_negative_pendingConfirm_throws() {
        // given
        when(invoiceMapper.selectById(INVOICE_ID)).thenReturn(invoice(InvoiceStatus.PENDING_CONFIRM));

        // when/then
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.reverseInvoice(INVOICE_ID, USER_ID, "原因"));
        StateMachineTestHelper.assertBusinessErrorContains(ex, "当前状态不可红冲");
    }

    @Test
    @DisplayName("reverseInvoice_负向_VOIDED不可红冲")
    void reverseInvoice_negative_voided_throws() {
        // given
        when(invoiceMapper.selectById(INVOICE_ID)).thenReturn(invoice(InvoiceStatus.VOIDED));

        // when/then
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.reverseInvoice(INVOICE_ID, USER_ID, "原因"));
        StateMachineTestHelper.assertBusinessErrorContains(ex, "当前状态不可红冲");
    }

    @Test
    @DisplayName("reverseInvoice_负向_FULLY_RECONCILED不可红冲")
    void reverseInvoice_negative_fullyReconciled_throws() {
        // given
        when(invoiceMapper.selectById(INVOICE_ID)).thenReturn(invoice(InvoiceStatus.FULLY_RECONCILED));

        // when/then
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.reverseInvoice(INVOICE_ID, USER_ID, "原因"));
        StateMachineTestHelper.assertBusinessErrorContains(ex, "当前状态不可红冲");
    }

    @Test
    @DisplayName("reverseInvoice_负向_原因空字符串抛异常")
    void reverseInvoice_negative_emptyReason_throws() {
        // given
        lenient().when(invoiceMapper.selectById(INVOICE_ID)).thenReturn(invoice(InvoiceStatus.CONFIRMED));

        // when/then
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.reverseInvoice(INVOICE_ID, USER_ID, ""));
        StateMachineTestHelper.assertBusinessErrorContains(ex, "红冲必须填写原因");
    }

    @Test
    @DisplayName("reverseInvoice_负向_已被红冲不可重复红冲")
    void reverseInvoice_negative_alreadyReversed_throws() {
        // given
        OutputInvoiceEntity original = invoice(InvoiceStatus.CONFIRMED);
        original.setReversedFrom(999L);  // 已有红冲记录
        when(invoiceMapper.selectById(INVOICE_ID)).thenReturn(original);

        // when/then
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.reverseInvoice(INVOICE_ID, USER_ID, "原因"));
        StateMachineTestHelper.assertBusinessErrorContains(ex, "已被红冲");
    }

    @Test
    @DisplayName("reverseInvoice_负向_发票不存在抛异常")
    void reverseInvoice_negative_notFound_throws() {
        // given
        when(invoiceMapper.selectById(INVOICE_ID)).thenReturn(null);

        // when/then
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.reverseInvoice(INVOICE_ID, USER_ID, "原因"));
        StateMachineTestHelper.assertBusinessErrorContains(ex, "发票不存在");
    }

    @Test
    @DisplayName("reverseInvoice_正向_金额取反正确")
    void reverseInvoice_positive_amountNegated() {
        // given
        OutputInvoiceEntity original = invoice(InvoiceStatus.CONFIRMED);
        original.setTaxRate(new BigDecimal("0.13"));
        original.setAmount(new BigDecimal("1000.00"));
        original.setTaxAmount(new BigDecimal("130.00"));
        original.setTotalAmount(new BigDecimal("1130.00"));
        when(invoiceMapper.selectById(INVOICE_ID)).thenReturn(original);

        // when
        service.reverseInvoice(INVOICE_ID, USER_ID, "金额更正");

        // then
        ArgumentCaptor<OutputInvoiceEntity> captor = ArgumentCaptor.forClass(OutputInvoiceEntity.class);
        verify(invoiceMapper).insert(captor.capture());
        OutputInvoiceEntity red = captor.getValue();
        assertEquals("-1000.00", red.getAmount().toString());
        assertEquals("-130.00", red.getTaxAmount().toString());
        assertEquals("-1130.00", red.getTotalAmount().toString());
    }

    @Test
    @DisplayName("reverseInvoice_正向_发票号追加-R后缀")
    void reverseInvoice_positive_invoiceNoSuffix() {
        // given
        OutputInvoiceEntity original = invoice(InvoiceStatus.CONFIRMED);
        original.setTaxRate(new BigDecimal("0.13"));
        when(invoiceMapper.selectById(INVOICE_ID)).thenReturn(original);

        // when
        service.reverseInvoice(INVOICE_ID, USER_ID, "原因");

        // then
        ArgumentCaptor<OutputInvoiceEntity> captor = ArgumentCaptor.forClass(OutputInvoiceEntity.class);
        verify(invoiceMapper).insert(captor.capture());
        assertEquals("TEST001-R", captor.getValue().getInvoiceNo());
    }

    // ==================== P36: 红字发票级联测试 ====================

    @Test
    @DisplayName("confirm_红字发票_创建红字业务单据(source=RED_FLUSH)")
    void confirm_redInvoice_createsRedBusinessDoc() {
        // given — 红字发票（金额<0）
        OutputInvoiceEntity redInvoice = invoice(InvoiceStatus.PENDING_REVIEW);
        redInvoice.setAmount(new BigDecimal("-1000.00"));
        redInvoice.setTotalAmount(new BigDecimal("-1130.00"));
        redInvoice.setTaxAmount(new BigDecimal("-130.00"));
        redInvoice.setTaxRate(new BigDecimal("0.13"));
        redInvoice.setOriginalInvoiceNo("TEST001");
        when(invoiceMapper.selectById(INVOICE_ID)).thenReturn(redInvoice);
        when(businessDocMapper.selectCount(any())).thenReturn(0L);
        lenient().when(invoiceMapper.updateById(any(OutputInvoiceEntity.class))).thenReturn(1);
        doAnswer(inv -> {
            BusinessDocEntity d = inv.getArgument(0);
            d.setId(500L);
            return null;
        }).when(businessDocMapper).insert(any(BusinessDocEntity.class));
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        lenient().when(applicationContext.getBean(TaxService.class)).thenReturn(mockTaxService);
        lenient().when(valueOps.increment(anyString())).thenReturn(1L);

        // when
        service.confirm(INVOICE_ID, USER_ID);

        // then — 业务单据 source=RED_FLUSH
        ArgumentCaptor<BusinessDocEntity> docCaptor = ArgumentCaptor.forClass(BusinessDocEntity.class);
        verify(businessDocMapper).insert(docCaptor.capture());
        BusinessDocEntity doc = docCaptor.getValue();
        assertEquals("RED_FLUSH", doc.getSource());
        assertTrue(doc.getSummary().startsWith("红冲:"));
        assertEquals(new BigDecimal("-1130.00"), doc.getAmount());
    }

    @Test
    @DisplayName("confirm_红字发票_关联原业务单据(reversedFrom)")
    void confirm_redInvoice_linksToOriginalBusinessDoc() {
// given — 红字发票有 reversedFrom 指向蓝字发票
        OutputInvoiceEntity redInvoice = invoice(InvoiceStatus.PENDING_REVIEW);
        redInvoice.setAmount(new BigDecimal("-1000.00"));
        redInvoice.setTotalAmount(new BigDecimal("-1130.00"));
        redInvoice.setTaxRate(new BigDecimal("0.13"));
        redInvoice.setOriginalInvoiceNo("TEST001");
        redInvoice.setReversedFrom(200L); // 指向蓝字发票 ID
        when(invoiceMapper.selectById(INVOICE_ID)).thenReturn(redInvoice);
        when(businessDocMapper.selectCount(any())).thenReturn(0L);
        lenient().when(invoiceMapper.updateById(any(OutputInvoiceEntity.class))).thenReturn(1);
        // 存在蓝字业务单据
        BusinessDocEntity blueDoc = new BusinessDocEntity();
        blueDoc.setId(300L);
        blueDoc.setInvoiceNo("TEST001");
        when(businessDocMapper.selectOne(any())).thenReturn(blueDoc);
        doAnswer(inv -> {
            BusinessDocEntity d = inv.getArgument(0);
            d.setId(500L);
            return null;
        }).when(businessDocMapper).insert(any(BusinessDocEntity.class));
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        lenient().when(applicationContext.getBean(TaxService.class)).thenReturn(mockTaxService);
        lenient().when(valueOps.increment(anyString())).thenReturn(1L);

        // when
        service.confirm(INVOICE_ID, USER_ID);

        // then — 红字业务单据 reversedFrom 指向蓝字业务单据
        ArgumentCaptor<BusinessDocEntity> docCaptor = ArgumentCaptor.forClass(BusinessDocEntity.class);
        verify(businessDocMapper).insert(docCaptor.capture());
        assertEquals(300L, docCaptor.getValue().getReversedFrom());
    }

    @Test
    @DisplayName("confirm_蓝字发票_source=IMPORTED")
    void confirm_blueInvoice_normalSource() {
        // given — 正常蓝字发票
        OutputInvoiceEntity blueInvoice = invoice(InvoiceStatus.PENDING_REVIEW);
        blueInvoice.setAmount(new BigDecimal("1000.00"));
        blueInvoice.setTotalAmount(new BigDecimal("1130.00"));
        blueInvoice.setTaxRate(new BigDecimal("0.13"));
        when(invoiceMapper.selectById(INVOICE_ID)).thenReturn(blueInvoice);
        when(businessDocMapper.selectCount(any())).thenReturn(0L);
        lenient().when(invoiceMapper.updateById(any(OutputInvoiceEntity.class))).thenReturn(1);
        doAnswer(inv -> {
            BusinessDocEntity d = inv.getArgument(0);
            d.setId(500L);
            return null;
        }).when(businessDocMapper).insert(any(BusinessDocEntity.class));
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        lenient().when(applicationContext.getBean(TaxService.class)).thenReturn(mockTaxService);
        lenient().when(valueOps.increment(anyString())).thenReturn(1L);

        // when
        service.confirm(INVOICE_ID, USER_ID);

        // then — 正常 source
        ArgumentCaptor<BusinessDocEntity> docCaptor = ArgumentCaptor.forClass(BusinessDocEntity.class);
        verify(businessDocMapper).insert(docCaptor.capture());
        assertEquals("IMPORTED", docCaptor.getValue().getSource());
        assertFalse(docCaptor.getValue().getSummary().startsWith("红冲:"));
    }

    @Test
    @DisplayName("P32-C4: 并发 confirm 时乐观锁拦截冲突")
    void testConcurrentConfirmFailsWithOptimisticLock() {
        // given
        OutputInvoiceEntity invoice = invoice(InvoiceStatus.PENDING_REVIEW);
        invoice.setAmount(BigDecimal.valueOf(1000));
        invoice.setInvoiceNo("TEST-CONCURRENT-001");
        when(invoiceMapper.selectById(INVOICE_ID)).thenReturn(invoice);
        when(invoiceMapper.updateById(any(OutputInvoiceEntity.class))).thenReturn(0); // 模拟乐观锁冲突

        // when & then
        assertThrows(OptimisticLockingFailureException.class,
                () -> service.confirm(INVOICE_ID, USER_ID));
    }

    @Test
    @DisplayName("P38-F1: confirm 后 BusinessDoc 状态为 VOUCHERED")
    void confirm_businessDocStatusVouchered() {
        // given
        OutputInvoiceEntity inv = invoice(InvoiceStatus.PENDING_REVIEW);
        when(invoiceMapper.selectById(INVOICE_ID)).thenReturn(inv);
        lenient().when(invoiceMapper.updateById(any(OutputInvoiceEntity.class))).thenReturn(1);
        when(businessDocMapper.selectCount(any())).thenReturn(0L);
        doAnswer(invOnInsert -> {
            BusinessDocEntity d = invOnInsert.getArgument(0);
            d.setId(500L);
            return null;
        }).when(businessDocMapper).insert(any(BusinessDocEntity.class));
        // 模拟 voucher 生成成功，回写 doc
        lenient().when(businessDocMapper.selectOne(any())).thenAnswer(a -> {
            BusinessDocEntity d = new BusinessDocEntity();
            d.setId(500L);
            d.setStatus("DRAFT");
            d.setInvoiceNo("TEST001");
            return d;
        });
        doAnswer(invOnUpdate -> {
            // 模拟 taxService 生成凭证后 markVouchered 修改了发票
            inv.setVoucherId(200L);
            inv.setVoucherNo("VCH-TEST-001");
            return null;
        }).when(mockTaxService).generateVoucherFromInvoice(anyLong(), anyLong());
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        lenient().when(applicationContext.getBean(TaxService.class)).thenReturn(mockTaxService);
        lenient().when(valueOps.increment(anyString())).thenReturn(1L);

        // when
        service.confirm(INVOICE_ID, USER_ID);

        // then — BusinessDoc UPDATE 时状态为 VOUCHERED
        ArgumentCaptor<BusinessDocEntity> docCaptor = ArgumentCaptor.forClass(BusinessDocEntity.class);
        verify(businessDocMapper, atLeast(1)).updateById(docCaptor.capture());
        BusinessDocEntity updatedDoc = docCaptor.getValue();
        assertEquals("VOUCHERED", updatedDoc.getStatus());
    }

    @Test
    @DisplayName("P38-F2: confirm 后 auditedBy/auditedAt 已设置")
    void confirm_auditedByAuditedAtSet() {
        // given
        OutputInvoiceEntity inv = invoice(InvoiceStatus.PENDING_REVIEW);
        when(invoiceMapper.selectById(INVOICE_ID)).thenReturn(inv);
        lenient().when(invoiceMapper.updateById(any(OutputInvoiceEntity.class))).thenReturn(1);
        doAnswer(invOnInsert -> {
            BusinessDocEntity d = invOnInsert.getArgument(0);
            d.setId(500L);
            return null;
        }).when(businessDocMapper).insert(any(BusinessDocEntity.class));
        lenient().when(businessDocMapper.selectCount(any())).thenReturn(0L);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        lenient().when(applicationContext.getBean(TaxService.class)).thenReturn(mockTaxService);
        lenient().when(valueOps.increment(anyString())).thenReturn(1L);

        // when
        service.confirm(INVOICE_ID, USER_ID);

        // then
        assertEquals(USER_ID, inv.getAuditedBy());
        assertNotNull(inv.getAuditedAt());
    }

    // ==================== TDD Demo: reverseInvoice remark format (complementary coverage) ====================

    @Test
    @DisplayName("reverseInvoice_正向_备注格式_有原备注时正确拼接")
    void reverseInvoice_existingRemark_appendsCorrectly() {
        // GIVEN — 原发票已有备注
        OutputInvoiceEntity original = invoice(InvoiceStatus.CONFIRMED);
        original.setRemark("原始审核意见");
        when(invoiceMapper.selectById(INVOICE_ID)).thenReturn(original);

        // mock 返回一个新 ID
        doAnswer(inv -> {
            OutputInvoiceEntity e = inv.getArgument(0);
            e.setId(999L);
            return null;
        }).when(invoiceMapper).insert(any(OutputInvoiceEntity.class));

        // WHEN — 执行红冲
        Long redId = service.reverseInvoice(INVOICE_ID, USER_ID, "金额更正");

        // THEN — 验证新发票的 remark 格式： "原始审核意见 | [1] 金额更正"
        ArgumentCaptor<OutputInvoiceEntity> captor = ArgumentCaptor.forClass(OutputInvoiceEntity.class);
        verify(invoiceMapper).insert(captor.capture());
        OutputInvoiceEntity red = captor.getValue();
        assertEquals("原始审核意见 | [1] 金额更正", red.getRemark());
    }

    @Test
    @DisplayName("reverseInvoice_正向_备注格式_无原备注时只记录新原因")
    void reverseInvoice_noExistingRemark_setsJustReason() {
        // GIVEN — 原发票无备注（null）
        OutputInvoiceEntity original = invoice(InvoiceStatus.CONFIRMED);
        original.setRemark(null);
        when(invoiceMapper.selectById(INVOICE_ID)).thenReturn(original);

        doAnswer(inv -> {
            OutputInvoiceEntity e = inv.getArgument(0);
            e.setId(888L);
            return null;
        }).when(invoiceMapper).insert(any(OutputInvoiceEntity.class));

        // WHEN
        Long redId = service.reverseInvoice(INVOICE_ID, USER_ID, "开票错误");

        // THEN — remark 应为 "[1] 开票错误"（无前缀）
        ArgumentCaptor<OutputInvoiceEntity> captor = ArgumentCaptor.forClass(OutputInvoiceEntity.class);
        verify(invoiceMapper).insert(captor.capture());
        OutputInvoiceEntity red = captor.getValue();
        assertEquals("[1] 开票错误", red.getRemark());
    }

    @Test
    @DisplayName("reverseInvoice_正向_备注格式_空原备注时正确处理")
    void reverseInvoice_emptyOriginalRemark_handlesCorrectly() {
        // GIVEN — 原发票备注为空字符串
        OutputInvoiceEntity original = invoice(InvoiceStatus.CONFIRMED);
        original.setRemark("");
        when(invoiceMapper.selectById(INVOICE_ID)).thenReturn(original);

        doAnswer(inv -> {
            OutputInvoiceEntity e = inv.getArgument(0);
            e.setId(777L);
            return null;
        }).when(invoiceMapper).insert(any(OutputInvoiceEntity.class));

        // WHEN
        Long redId = service.reverseInvoice(INVOICE_ID, USER_ID, "税号填错");

        // THEN — remark 应为 "[1] 税号填错"（不是 "| [1] ..."）
        ArgumentCaptor<OutputInvoiceEntity> captor = ArgumentCaptor.forClass(OutputInvoiceEntity.class);
        verify(invoiceMapper).insert(captor.capture());
        OutputInvoiceEntity red = captor.getValue();
        assertEquals("[1] 税号填错", red.getRemark());
    }
}
