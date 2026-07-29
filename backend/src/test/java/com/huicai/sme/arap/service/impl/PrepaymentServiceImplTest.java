package com.huicai.sme.arap.service.impl;

import com.huicai.base.business.entity.ArapSettlementEntity;
import com.huicai.base.business.entity.ArapSettlementEntryEntity;
import com.huicai.base.business.entity.BusinessDocEntity;
import com.huicai.base.business.mapper.ArapSettlementEntryMapper;
import com.huicai.base.business.mapper.ArapSettlementMapper;
import com.huicai.base.business.mapper.BusinessDocMapper;
import com.huicai.sme.arap.service.ArapSettlementService;
import com.huicai.base.system.entity.Subject;
import com.huicai.base.system.mapper.SubjectMapper;
import com.huicai.base.voucher.entity.VoucherEntryEntity;
import com.huicai.base.voucher.entity.VoucherEntity;
import com.huicai.base.voucher.mapper.VoucherEntryMapper;
import com.huicai.base.voucher.mapper.VoucherMapper;
import com.huicai.base.voucher.service.VoucherNoService;
import com.huicai.common.exception.BusinessException;
import com.huicai.sme.arap.entity.PrepaymentEntity;
import com.huicai.sme.arap.mapper.PrepaymentMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * PrepaymentService 专用测试 — 覆盖预付款创建/确认/核销/反冲
 *
 * <p>P0 优先级：8 个方法在 PrepaymentControllerTest 中被 mock，核心财务流程无专用测试。
 */
@ExtendWith(MockitoExtension.class)
class PrepaymentServiceImplTest {

    @Mock
    private PrepaymentMapper prepaymentMapper;

    @Mock
    private BusinessDocMapper businessDocMapper;

    @Mock
    private ArapSettlementService settlementService;

    @Mock
    private ArapSettlementMapper settlementMapper;

    @Mock
    private ArapSettlementEntryMapper settlementEntryMapper;

    @Mock
    private VoucherMapper voucherMapper;

    @Mock
    private VoucherEntryMapper voucherEntryMapper;

    @Mock
    private VoucherNoService voucherNoService;

    @Mock
    private SubjectMapper subjectMapper;

    private PrepaymentServiceImpl service;

    private static final Long PREPAY_ID = 100L;
    private static final Long VENDOR_ID = 10L;
    private static final Long CUSTOMER_ID = 20L;
    private static final Long BUSINESS_DOC_ID = 200L;
    private static final String PERIOD = "202607";

    @BeforeEach
    void setUp() {
        service = new PrepaymentServiceImpl(
                prepaymentMapper, businessDocMapper, settlementService,
                settlementMapper, settlementEntryMapper,
                voucherMapper, voucherEntryMapper, voucherNoService, subjectMapper);
    }

    // ==================== create ====================

    @Test
    @DisplayName("create — 创建预付款，状态 DRAFT，未核销金额 = 金额")
    void create_设置状态DRAFT_未核销金额等于金额() {
        PrepaymentEntity entity = new PrepaymentEntity();
        entity.setVendorId(VENDOR_ID);
        entity.setAmount(new BigDecimal("50000.00"));

        when(prepaymentMapper.insert(any(PrepaymentEntity.class))).thenAnswer(invocation -> {
            PrepaymentEntity e = invocation.getArgument(0);
            e.setId(PREPAY_ID);
            return 1;
        });

        PrepaymentEntity result = service.create(entity);

        assertNotNull(result.getId());
        assertEquals("DRAFT", result.getStatus());
        assertEquals(0, result.getAmount().compareTo(new BigDecimal("50000.00")));
        assertEquals(0, result.getUnsettledAmount().compareTo(new BigDecimal("50000.00")));
        assertEquals(0, result.getSettledAmount().compareTo(BigDecimal.ZERO));
        assertNotNull(result.getTxDate());
        assertEquals(LocalDate.now(), result.getTxDate());
    }

    @Test
    @DisplayName("create — 预收款，使用 customerId")
    void create_预收款_使用customerId() {
        PrepaymentEntity entity = new PrepaymentEntity();
        entity.setCustomerId(CUSTOMER_ID);
        entity.setAmount(new BigDecimal("30000.00"));

        when(prepaymentMapper.insert(any(PrepaymentEntity.class))).thenAnswer(invocation -> {
            PrepaymentEntity e = invocation.getArgument(0);
            e.setId(PREPAY_ID);
            return 1;
        });

        PrepaymentEntity result = service.create(entity);

        assertNotNull(result.getId());
        assertEquals("DRAFT", result.getStatus());
        assertEquals(0, result.getAmount().compareTo(new BigDecimal("30000.00")));
    }

    // ==================== confirm ====================

    @Test
    @DisplayName("confirm — DRAFT 状态变为 CONFIRMED")
    void confirm_DRAFT_变CONFIRMED() {
        PrepaymentEntity entity = new PrepaymentEntity();
        entity.setId(PREPAY_ID);
        entity.setStatus("DRAFT");
        entity.setAmount(new BigDecimal("50000.00"));

        when(prepaymentMapper.selectById(PREPAY_ID)).thenReturn(entity);
        when(prepaymentMapper.updateById(any(PrepaymentEntity.class))).thenReturn(1);

        service.confirm(PREPAY_ID);

        // confirm() 返回 void，通过 ArgumentCaptor 验证状态变更
        ArgumentCaptor<PrepaymentEntity> captor = ArgumentCaptor.forClass(PrepaymentEntity.class);
        verify(prepaymentMapper).updateById(captor.capture());
        assertEquals("CONFIRMED", captor.getValue().getStatus());
    }

    @Test
    @DisplayName("confirm — 非 DRAFT 状态抛异常")
    void confirm_非DRAFT_抛异常() {
        PrepaymentEntity entity = new PrepaymentEntity();
        entity.setId(PREPAY_ID);
        entity.setStatus("CONFIRMED");

        when(prepaymentMapper.selectById(PREPAY_ID)).thenReturn(entity);

        assertThrows(BusinessException.class, () -> service.confirm(PREPAY_ID));
        verify(prepaymentMapper, never()).updateById(any(PrepaymentEntity.class));
    }

    @Test
    @DisplayName("confirm — 预付款不存在抛异常")
    void confirm_不存在_抛异常() {
        when(prepaymentMapper.selectById(PREPAY_ID)).thenReturn(null);

        assertThrows(BusinessException.class, () -> service.confirm(PREPAY_ID));
    }

    // ==================== applyToPayable (预付冲应付) ====================

    @Test
    @DisplayName("applyToPayable — 预付冲应付成功，生成核销单和凭证")
    void applyToPayable_成功_生成核销单和凭证() {
        // 1. 准备预付款 — CONFIRMED, unsettled=50000
        PrepaymentEntity prepay = new PrepaymentEntity();
        prepay.setId(PREPAY_ID);
        prepay.setVendorId(VENDOR_ID);
        prepay.setStatus("CONFIRMED");
        prepay.setAmount(new BigDecimal("50000.00"));
        prepay.setSettledAmount(BigDecimal.ZERO);
        prepay.setUnsettledAmount(new BigDecimal("50000.00"));
        when(prepaymentMapper.selectById(PREPAY_ID)).thenReturn(prepay);

        // 2. 准备应付单 — unsettled=50000, 状态 CONFIRMED
        BusinessDocEntity payable = new BusinessDocEntity();
        payable.setId(BUSINESS_DOC_ID);
        payable.setSupplierId(VENDOR_ID);
        payable.setDocType("PAYABLE");
        payable.setStatus("CONFIRMED");
        payable.setAmount(new BigDecimal("50000.00"));
        payable.setSettledAmount(BigDecimal.ZERO);
        payable.setUnsettledAmount(new BigDecimal("50000.00"));
        when(businessDocMapper.selectById(BUSINESS_DOC_ID)).thenReturn(payable);

        // 3. 科目映射 (findSubjectIdByCode 使用 selectList)
        Subject subjectPrepay = new Subject();
        subjectPrepay.setId(1123L);
        Subject subjectPayable = new Subject();
        subjectPayable.setId(2202L);
        when(subjectMapper.selectList(any())).thenReturn(List.of(subjectPrepay), List.of(subjectPayable));

        // 4. 凭证号生成
        when(voucherNoService.generateNextNo(anyString(), anyLong())).thenReturn("FK-202607-000001");

        // 5. 核销单插入
        when(settlementMapper.insert(any(ArapSettlementEntity.class))).thenReturn(1);
        when(settlementEntryMapper.insert(any(ArapSettlementEntryEntity.class))).thenReturn(1);
        when(voucherMapper.insert(any(VoucherEntity.class))).thenReturn(1);
        when(voucherEntryMapper.insert(any(VoucherEntryEntity.class))).thenReturn(1);

        // 执行
        service.applyToPayable(PREPAY_ID, BUSINESS_DOC_ID, null, PERIOD, 1L, "预付冲应付测试");

        // 验证：预付款状态更新
        ArgumentCaptor<PrepaymentEntity> prepayCaptor = ArgumentCaptor.forClass(PrepaymentEntity.class);
        verify(prepaymentMapper, atLeastOnce()).updateById(prepayCaptor.capture());
        assertEquals("APPLIED", prepayCaptor.getValue().getStatus());

        // 验证：应付单状态更新
        ArgumentCaptor<BusinessDocEntity> docCaptor = ArgumentCaptor.forClass(BusinessDocEntity.class);
        verify(businessDocMapper, atLeastOnce()).updateById(docCaptor.capture());
        assertEquals("SETTLED", docCaptor.getValue().getStatus());

        // 验证：创建了核销单
        verify(settlementMapper, atLeastOnce()).insert(any(ArapSettlementEntity.class));
        verify(settlementEntryMapper, atLeastOnce()).insert(any(ArapSettlementEntryEntity.class));

        // 验证：创建了凭证
        verify(voucherMapper, atLeastOnce()).insert(any(VoucherEntity.class));
        verify(voucherEntryMapper, atLeastOnce()).insert(any(VoucherEntryEntity.class));
    }

    @Test
    @DisplayName("applyToPayable — 预付款未确认抛异常")
    void applyToPayable_预付款未确认_抛异常() {
        PrepaymentEntity prepay = new PrepaymentEntity();
        prepay.setId(PREPAY_ID);
        prepay.setStatus("DRAFT");
        prepay.setUnsettledAmount(new BigDecimal("50000.00"));
        when(prepaymentMapper.selectById(PREPAY_ID)).thenReturn(prepay);

        assertThrows(BusinessException.class,
                () -> service.applyToPayable(PREPAY_ID, BUSINESS_DOC_ID, null, PERIOD, 1L, "测试"));
        verify(businessDocMapper, never()).selectById(any());
    }

    @Test
    @DisplayName("applyToPayable — 供应商不一致抛异常")
    void applyToPayable_供应商不一致_抛异常() {
        PrepaymentEntity prepay = new PrepaymentEntity();
        prepay.setId(PREPAY_ID);
        prepay.setVendorId(VENDOR_ID);
        prepay.setStatus("CONFIRMED");
        prepay.setAmount(new BigDecimal("50000.00"));
        prepay.setSettledAmount(BigDecimal.ZERO);
        prepay.setUnsettledAmount(new BigDecimal("50000.00"));
        when(prepaymentMapper.selectById(PREPAY_ID)).thenReturn(prepay);

        // 应付单供应商不同
        BusinessDocEntity payable = new BusinessDocEntity();
        payable.setId(BUSINESS_DOC_ID);
        payable.setSupplierId(999L);
        when(businessDocMapper.selectById(BUSINESS_DOC_ID)).thenReturn(payable);

        assertThrows(BusinessException.class,
                () -> service.applyToPayable(PREPAY_ID, BUSINESS_DOC_ID, null, PERIOD, 1L, "测试"));
    }

    // ==================== reverse ====================

    @Test
    @DisplayName("reverse — CONFIRMED 状态变为 REVERSED，金额恢复")
    void reverse_CONFIRMED_变REVERSED() {
        PrepaymentEntity entity = new PrepaymentEntity();
        entity.setId(PREPAY_ID);
        entity.setStatus("CONFIRMED");
        entity.setAmount(new BigDecimal("50000.00"));
        entity.setSettledAmount(new BigDecimal("20000.00"));
        entity.setUnsettledAmount(new BigDecimal("30000.00"));

        when(prepaymentMapper.selectById(PREPAY_ID)).thenReturn(entity);
        when(prepaymentMapper.updateById(any(PrepaymentEntity.class))).thenReturn(1);

        // reverse() 返回 void，通过 ArgumentCaptor 验证状态变更
        service.reverse(PREPAY_ID, 1L, "测试反冲");

        ArgumentCaptor<PrepaymentEntity> captor = ArgumentCaptor.forClass(PrepaymentEntity.class);
        verify(prepaymentMapper).updateById(captor.capture());
        PrepaymentEntity updated = captor.getValue();
        assertEquals("REVERSED", updated.getStatus());
        assertEquals(0, updated.getSettledAmount().compareTo(BigDecimal.ZERO));
        assertEquals(0, updated.getUnsettledAmount().compareTo(new BigDecimal("50000.00")));
    }

    @Test
    @DisplayName("reverse — DRAFT 状态不可反冲")
    void reverse_DRAFT_抛异常() {
        PrepaymentEntity entity = new PrepaymentEntity();
        entity.setId(PREPAY_ID);
        entity.setStatus("DRAFT");
        when(prepaymentMapper.selectById(PREPAY_ID)).thenReturn(entity);

        assertThrows(BusinessException.class, () -> service.reverse(PREPAY_ID, 1L, "测试"));
    }

    // ==================== getOpenPrepayments ====================

    @Test
    @DisplayName("getOpenPrepayments — 查询未结清预付款")
    void getOpenPrepayments_返回未结清列表() {
        when(prepaymentMapper.selectList(any())).thenReturn(java.util.List.of());

        var result = service.getOpenPrepayments(VENDOR_ID);

        assertNotNull(result);
        verify(prepaymentMapper).selectList(any());
    }
}