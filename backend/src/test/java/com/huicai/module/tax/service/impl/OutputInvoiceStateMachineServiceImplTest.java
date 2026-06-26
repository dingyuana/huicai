package com.huicai.module.tax.service.impl;

import com.huicai.common.exception.BusinessException;
import com.huicai.common.test.StateMachineTestHelper;
import com.huicai.module.arap.constant.ArapStatus;
import com.huicai.module.arap.entity.ReceivableEntity;
import com.huicai.module.arap.mapper.ReceivableMapper;
import com.huicai.module.arap.service.ReceivableStateMachineService;
import com.huicai.module.finance.entity.BusinessDocEntity;
import com.huicai.module.finance.entity.BusinessDocEntryEntity;
import com.huicai.module.finance.entity.VoucherEntity;
import com.huicai.module.finance.entity.VoucherEntryEntity;
import com.huicai.module.finance.mapper.BusinessDocEntryMapper;
import com.huicai.module.finance.mapper.BusinessDocMapper;
import com.huicai.module.finance.mapper.VoucherEntryMapper;
import com.huicai.module.finance.mapper.VoucherMapper;
import com.huicai.module.finance.service.BusinessDocService;
import com.huicai.module.tax.constant.InvoiceStatus;
import com.huicai.module.tax.entity.OutputInvoiceEntity;
import com.huicai.module.tax.mapper.OutputInvoiceMapper;
import com.huicai.module.tax.service.TaxService;
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
 * 也自动创建业务单(DRAFT) + 应收单(DRAFT)（P31 修正）。
 *
 * @see <a href="file://docs/process/state-machine-test-checklist.md">状态机测试契约检查清单</a>
 */
@ExtendWith(MockitoExtension.class)
class OutputInvoiceStateMachineServiceImplTest {

    @Mock
    private OutputInvoiceMapper invoiceMapper;

    @Mock
    private BusinessDocService businessDocService;

    @Mock
    private ReceivableStateMachineService receivableStateMachineService;

    @Mock
    private BusinessDocMapper docMapper;

    @Mock
    private BusinessDocEntryMapper docEntryMapper;

    @Mock
    private ReceivableMapper receivableMapper;

    @Mock
    private VoucherMapper voucherMapper;

    @Mock
    private VoucherEntryMapper voucherEntryMapper;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    @Mock
    private TaxService taxService;

    private OutputInvoiceStateMachineServiceImpl service;
    private static final Long USER_ID = 1L;
    private static final Long INVOICE_ID = 100L;

    @BeforeEach
    void setup() {
        service = new OutputInvoiceStateMachineServiceImpl(
                invoiceMapper, businessDocService, receivableStateMachineService,
                docMapper, docEntryMapper, receivableMapper, redisTemplate);
        // 注入 lazy 依赖（用于 createBusinessDocAndReceivableAfterConfirm 中的 taxService 调用）
        try {
            var field = OutputInvoiceStateMachineServiceImpl.class.getDeclaredField("taxService");
            field.setAccessible(true);
            field.set(service, taxService);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
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
        StateMachineTestHelper.verifyNoDocumentCreated(docMapper, docEntryMapper);
        StateMachineTestHelper.verifyNoReceivableCreated(receivableMapper);
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
    @DisplayName("confirm_正向_状态变更+创建业务单和应收单")
    void confirm_positive_statusChanged() {
        // given
        OutputInvoiceEntity inv = invoice(InvoiceStatus.PENDING_REVIEW);
        when(invoiceMapper.selectById(INVOICE_ID)).thenReturn(inv);
        lenient().when(valueOps.increment(anyString())).thenReturn(1L);

        // when
        service.confirm(INVOICE_ID, USER_ID);

        // then — 正向：状态变更
        assertEquals(InvoiceStatus.CONFIRMED, inv.getStatus());
        assertEquals(USER_ID, inv.getUpdatedBy());
        // then — 正向：创建业务单
        verify(docMapper).insert(any(BusinessDocEntity.class));
        verify(docEntryMapper).insert(any(BusinessDocEntryEntity.class));
        // then — 正向：创建应收单
        verify(receivableMapper).insert(any(ReceivableEntity.class));
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
        StateMachineTestHelper.verifyNoDocumentCreated(docMapper, docEntryMapper);
        StateMachineTestHelper.verifyNoReceivableCreated(receivableMapper);
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
        StateMachineTestHelper.verifyNoDocumentCreated(docMapper, docEntryMapper);
        StateMachineTestHelper.verifyNoReceivableCreated(receivableMapper);
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
        service.markVouchered(INVOICE_ID, 200L, USER_ID);

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
        service.markVouchered(INVOICE_ID, 200L, USER_ID);

        // then — 负向：标记已生成凭证不应再创建新凭证
        StateMachineTestHelper.verifyNoVoucherCreated(voucherMapper, voucherEntryMapper);
        StateMachineTestHelper.verifyNoDocumentCreated(docMapper, docEntryMapper);
        StateMachineTestHelper.verifyNoReceivableCreated(receivableMapper);
    }

    @Test
    @DisplayName("markVouchered_负向_非已确认状态_抛异常")
    void markVouchered_negative_wrongStatus_throws() {
        // given
        OutputInvoiceEntity inv = invoice(InvoiceStatus.VOUCHERED);
        when(invoiceMapper.selectById(INVOICE_ID)).thenReturn(inv);

        // when/then
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.markVouchered(INVOICE_ID, 200L, USER_ID));
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
        StateMachineTestHelper.verifyNoDocumentCreated(docMapper, docEntryMapper);
        StateMachineTestHelper.verifyNoReceivableCreated(receivableMapper);
    }

    @Test
    @DisplayName("onReconciliationUpdate_负向_非已生成凭证状态_抛异常")
    void onReconciliationUpdate_negative_wrongStatus_throws() {
        // given
        OutputInvoiceEntity inv = invoice(InvoiceStatus.CONFIRMED);
        when(invoiceMapper.selectById(INVOICE_ID)).thenReturn(inv);

        // when/then
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.onReconciliationUpdate(INVOICE_ID, BigDecimal.ZERO, USER_ID));
        StateMachineTestHelper.assertBusinessErrorContains(ex, "仅已生成凭证的发票可核销");
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
        StateMachineTestHelper.verifyNoDocumentCreated(docMapper, docEntryMapper);
        StateMachineTestHelper.verifyNoReceivableCreated(receivableMapper);
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
}
