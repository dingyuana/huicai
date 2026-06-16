package com.huicai.module.finance.service.impl;

import com.huicai.common.exception.BusinessException;
import com.huicai.module.arap.mapper.CustomerMapper;
import com.huicai.module.arap.mapper.VendorMapper;
import com.huicai.module.finance.dto.BusinessDocDTO;
import com.huicai.module.finance.entity.BusinessDocEntity;
import com.huicai.module.finance.entity.BusinessDocEntryEntity;
import com.huicai.module.finance.mapper.BusinessDocEntryMapper;
import com.huicai.module.finance.mapper.BusinessDocMapper;
import com.huicai.module.finance.mapper.VoucherEntryMapper;
import com.huicai.module.finance.mapper.VoucherMapper;
import com.huicai.module.finance.service.VoucherNoService;
import com.huicai.module.system.entity.PeriodEntity;
import com.huicai.module.system.entity.UserEntity;
import com.huicai.module.system.mapper.SubjectMapper;
import com.huicai.module.system.mapper.UserMapper;
import com.huicai.module.system.service.PeriodService;
import com.huicai.module.system.service.VoucherTypeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * BusinessDocServiceImpl 单元测试 — 防御性 update() 验证
 * <p>
 * 覆盖前端 loadDoc() 漏字段导致 supplierId 等被清空的回归场景。
 * 核心约定: 当 dto 中可选字段为 null 或 blank 时, 保留 entity 现有值, 不得覆盖为 null.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BusinessDocServiceImplTest {

    @Mock private BusinessDocMapper docMapper;
    @Mock private BusinessDocEntryMapper docEntryMapper;
    @Mock private VoucherMapper voucherMapper;
    @Mock private VoucherEntryMapper voucherEntryMapper;
    @Mock private VoucherNoService voucherNoService;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private PeriodService periodService;
    @Mock private SubjectMapper subjectMapper;
    @Mock private VoucherTypeService voucherTypeService;
    @Mock private CustomerMapper customerMapper;
    @Mock private VendorMapper vendorMapper;
    @Mock private UserMapper userMapper;

    @org.mockito.InjectMocks private BusinessDocServiceImpl service;

    private static final Long DOC_ID = 346L;
    private static final Long USER_ID = 100L;

    @BeforeEach
    void setUp() {
        // periodService.lambdaQuery()...one() 链式调用 → 返回 OPEN 期间
        PeriodEntity openPeriod = new PeriodEntity();
        openPeriod.setStatus("OPEN");
        when(periodService.lambdaQuery()).thenReturn(mock(com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper.class));
        var chain = periodService.lambdaQuery();
        when(chain.eq(any(), any())).thenReturn(chain);
        when(chain.one()).thenReturn(openPeriod);

        // getDetail() 末尾的 populatePartyNames / populateUserNames
        when(vendorMapper.selectBatchIds(anyList())).thenReturn(Collections.emptyList());
        when(customerMapper.selectBatchIds(anyList())).thenReturn(Collections.emptyList());
        when(userMapper.selectBatchIds(anyList())).thenReturn(Collections.emptyList());

        // docEntryMapper 路径
        when(docEntryMapper.deleteByDocId(anyLong())).thenReturn(0);
        when(docEntryMapper.selectByDocId(anyLong())).thenReturn(Collections.emptyList());
    }

    private BusinessDocEntity stubDrafDoc() {
        BusinessDocEntity e = new BusinessDocEntity();
        e.setId(DOC_ID);
        e.setDocNo("FK2026060001");
        e.setDocType("PAYMENT");
        e.setDocDate(LocalDate.of(2026, 6, 15));
        e.setPeriod("202606");
        e.setAmount(new BigDecimal("1000.00"));
        e.setStatus("DRAFT");
        e.setSupplierId(99L);                // 已有供应商
        e.setCustomerId(null);
        e.setApplicantId(50L);
        e.setDeptId(10L);
        e.setSummary("支付货款");
        e.setAttachmentIds("att-1,att-2");
        e.setCreatedBy(1L);
        e.setVoucherId(null);
        e.setSubmittedAt(null);
        return e;
    }

    private BusinessDocDTO stubDto() {
        BusinessDocDTO dto = new BusinessDocDTO();
        dto.setId(DOC_ID);
        dto.setDocType("PAYMENT");
        dto.setDocDate(LocalDate.of(2026, 6, 15));
        dto.setPeriod("202606");
        dto.setAmount(new BigDecimal("1200.00"));  // 改金额
        dto.setEntries(Collections.emptyList());  // update() 会迭代 entries
        // 故意不设置 supplierId / customerId / applicantId / deptId / summary / attachmentIds
        return dto;
    }

    // ==================== 防御性 update ====================

    @Test
    void update_dto缺所有可选字段_全部保留entity原值() {
        BusinessDocEntity existing = stubDrafDoc();
        BusinessDocDTO dto = stubDto();
        when(docMapper.selectById(DOC_ID)).thenReturn(existing);

        service.update(dto, USER_ID);

        ArgumentCaptor<BusinessDocEntity> captor = ArgumentCaptor.forClass(BusinessDocEntity.class);
        verify(docMapper).updateById(captor.capture());
        BusinessDocEntity saved = captor.getValue();

        // 必填字段被覆盖
        assertEquals("PAYMENT", saved.getDocType());
        assertEquals("202606", saved.getPeriod());
        assertEquals(0, saved.getAmount().compareTo(new BigDecimal("1200.00")));

        // 可选字段全部保留原值 (这是关键断言)
        assertEquals(99L, saved.getSupplierId());
        assertNull(saved.getCustomerId());
        assertEquals(50L, saved.getApplicantId());
        assertEquals(10L, saved.getDeptId());
        assertEquals("支付货款", saved.getSummary());
        assertEquals("att-1,att-2", saved.getAttachmentIds());
    }

    @Test
    void update_dtoSupplierId为null_保留原值() {
        BusinessDocEntity existing = stubDrafDoc();
        BusinessDocDTO dto = stubDto();
        dto.setSupplierId(null);  // 显式 null
        when(docMapper.selectById(DOC_ID)).thenReturn(existing);

        service.update(dto, USER_ID);

        ArgumentCaptor<BusinessDocEntity> captor = ArgumentCaptor.forClass(BusinessDocEntity.class);
        verify(docMapper).updateById(captor.capture());
        assertEquals(99L, captor.getValue().getSupplierId());
    }

    @Test
    void update_dto显式传新SupplierId_覆盖原值() {
        BusinessDocEntity existing = stubDrafDoc();
        BusinessDocDTO dto = stubDto();
        dto.setSupplierId(200L);  // 显式新值
        when(docMapper.selectById(DOC_ID)).thenReturn(existing);

        service.update(dto, USER_ID);

        ArgumentCaptor<BusinessDocEntity> captor = ArgumentCaptor.forClass(BusinessDocEntity.class);
        verify(docMapper).updateById(captor.capture());
        assertEquals(200L, captor.getValue().getSupplierId());
    }

    @Test
    void update_dto显式传CustomerId_原值是null也允许设值() {
        BusinessDocEntity existing = stubDrafDoc();
        existing.setCustomerId(null);
        BusinessDocDTO dto = stubDto();
        dto.setCustomerId(77L);
        when(docMapper.selectById(DOC_ID)).thenReturn(existing);

        service.update(dto, USER_ID);

        ArgumentCaptor<BusinessDocEntity> captor = ArgumentCaptor.forClass(BusinessDocEntity.class);
        verify(docMapper).updateById(captor.capture());
        assertEquals(77L, captor.getValue().getCustomerId());
    }

    @Test
    void update_dtoAttachmentIds为Blank_保留原值() {
        BusinessDocEntity existing = stubDrafDoc();
        BusinessDocDTO dto = stubDto();
        dto.setAttachmentIds("   ");  // 空白字符串
        when(docMapper.selectById(DOC_ID)).thenReturn(existing);

        service.update(dto, USER_ID);

        ArgumentCaptor<BusinessDocEntity> captor = ArgumentCaptor.forClass(BusinessDocEntity.class);
        verify(docMapper).updateById(captor.capture());
        assertEquals("att-1,att-2", captor.getValue().getAttachmentIds());
    }

    @Test
    void update_dto只改summary_其他字段全保留() {
        BusinessDocEntity existing = stubDrafDoc();
        BusinessDocDTO dto = stubDto();
        dto.setSummary("改后的摘要");
        when(docMapper.selectById(DOC_ID)).thenReturn(existing);

        service.update(dto, USER_ID);

        ArgumentCaptor<BusinessDocEntity> captor = ArgumentCaptor.forClass(BusinessDocEntity.class);
        verify(docMapper).updateById(captor.capture());
        BusinessDocEntity saved = captor.getValue();
        assertEquals("改后的摘要", saved.getSummary());
        assertEquals(99L, saved.getSupplierId());
        assertEquals(50L, saved.getApplicantId());
        assertEquals(10L, saved.getDeptId());
        assertEquals("att-1,att-2", saved.getAttachmentIds());
    }

    // ==================== 状态守卫 ====================

    @Test
    void update_id为空_throwBadRequest() {
        BusinessDocDTO dto = stubDto();
        dto.setId(null);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.update(dto, USER_ID));
        assertTrue(ex.getMessage().contains("更新时单据ID不能为空"));
    }

    @Test
    void update_id不存在_throwNotFound() {
        when(docMapper.selectById(999L)).thenReturn(null);
        BusinessDocDTO dto = stubDto();
        dto.setId(999L);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.update(dto, USER_ID));
        assertTrue(ex.getMessage().contains("单据不存在"));
    }

    @Test
    void update_非DRAFT状态_throwBadRequest() {
        BusinessDocEntity existing = stubDrafDoc();
        existing.setStatus("SUBMITTED");
        when(docMapper.selectById(DOC_ID)).thenReturn(existing);

        BusinessDocDTO dto = stubDto();
        BusinessException ex = assertThrows(BusinessException.class, () -> service.update(dto, USER_ID));
        assertTrue(ex.getMessage().contains("仅草稿状态"));
    }

    // ==================== submittedAt / voucherId 保护 (回归) ====================

    @Test
    void update_不得清空voucherId和submittedAt() {
        // 即便 dto 是 DRAFT 状态, 如果一个已生成凭证的 doc 被错误传入 update
        // (虽然 update 守卫会拒绝), 这里验证逻辑上不会清空 voucherId/submittedAt
        BusinessDocEntity existing = stubDrafDoc();
        existing.setVoucherId(555L);
        existing.setSubmittedBy(2L);
        existing.setSubmittedAt(java.time.LocalDateTime.of(2026, 6, 16, 10, 0));
        existing.setStatus("DRAFT");  // 强行 DRAFT
        when(docMapper.selectById(DOC_ID)).thenReturn(existing);

        BusinessDocDTO dto = stubDto();
        service.update(dto, USER_ID);

        ArgumentCaptor<BusinessDocEntity> captor = ArgumentCaptor.forClass(BusinessDocEntity.class);
        verify(docMapper).updateById(captor.capture());
        BusinessDocEntity saved = captor.getValue();
        // update() 不应该触碰这些字段
        assertEquals(555L, saved.getVoucherId());
        assertEquals(2L, saved.getSubmittedBy());
        assertNotNull(saved.getSubmittedAt());
    }
}
