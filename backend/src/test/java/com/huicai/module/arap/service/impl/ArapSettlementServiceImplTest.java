package com.huicai.module.arap.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.common.exception.BusinessException;
import com.huicai.module.arap.entity.ArapSettlementEntity;
import com.huicai.module.arap.entity.ArapSettlementEntryEntity;
import com.huicai.module.arap.entity.ReceivableEntity;
import com.huicai.module.arap.mapper.ArapSettlementEntryMapper;
import com.huicai.module.arap.mapper.ArapSettlementMapper;
import com.huicai.module.arap.mapper.PayableMapper;
import com.huicai.module.arap.mapper.ReceivableMapper;
import com.huicai.module.finance.entity.BusinessDocEntity;
import com.huicai.module.finance.entity.VoucherEntity;
import com.huicai.module.finance.entity.VoucherEntryEntity;
import com.huicai.module.finance.mapper.BusinessDocMapper;
import com.huicai.module.finance.mapper.VoucherEntryMapper;
import com.huicai.module.finance.mapper.VoucherMapper;
import com.huicai.module.finance.service.VoucherNoService;
import com.huicai.module.finance.service.VoucherTemplateService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ArapSettlementServiceImplTest {

    @Mock private ArapSettlementMapper mapper;
    @Mock private ArapSettlementEntryMapper entryMapper;
    @Mock private ReceivableMapper receivableMapper;
    @Mock private PayableMapper payableMapper;
    @Mock private BusinessDocMapper businessDocMapper;
    @Mock private VoucherTemplateService voucherTemplateService;
    @Mock private VoucherMapper voucherMapper;
    @Mock private VoucherEntryMapper voucherEntryMapper;
    @Mock private VoucherNoService voucherNoService;

    @InjectMocks private ArapSettlementServiceImpl service;

    @Test
    void pageQuery_带status_调selectPage() {
        when(mapper.selectPage(any(), any())).thenReturn(null);
        IPage<ArapSettlementEntity> r = service.pageQuery("DRAFT", null, 1, 20);
        assertNull(r);
        verify(mapper).selectPage(any(), any());
    }

    @Test
    void getById_存在_返回entity() {
        ArapSettlementEntity e = new ArapSettlementEntity();
        e.setId(1L);
        when(mapper.selectById(1L)).thenReturn(e);
        ArapSettlementEntity r = service.getById(1L);
        assertNotNull(r);
    }

    @Test
    void getById_不存在_throw() {
        when(mapper.selectById(99L)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.getById(99L));
        assertTrue(ex.getMessage().contains("核销单不存在"));
    }

    @Test
    void create_默认statusDRAFT_总额等于entry之和() {
        ArapSettlementEntity e = new ArapSettlementEntity();
        e.setSettlementType("RECEIVE");
        e.setPeriod("202606");

        ArapSettlementEntryEntity e1 = new ArapSettlementEntryEntity();
        e1.setSettledAmount(new BigDecimal("100"));
        ArapSettlementEntryEntity e2 = new ArapSettlementEntryEntity();
        e2.setSettledAmount(new BigDecimal("200"));

        ArapSettlementEntity r = service.create(e, List.of(e1, e2));
        assertEquals("DRAFT", r.getStatus());
        assertEquals(0, new BigDecimal("300").compareTo(r.getTotalAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(r.getDiscountAmount()));
        assertNotNull(r.getSettlementNo());
        assertTrue(r.getSettlementNo().startsWith("JS-"));
        verify(mapper).insert(e);
        verify(entryMapper, times(2)).insert(any(ArapSettlementEntryEntity.class));
    }

    @Test
    void create_付款类型_前缀FS() {
        ArapSettlementEntity e = new ArapSettlementEntity();
        e.setSettlementType("PAY");
        e.setPeriod("202606");
        ArapSettlementEntryEntity entry = new ArapSettlementEntryEntity();
        entry.setSettledAmount(new BigDecimal("100"));
        ArapSettlementEntity r = service.create(e, List.of(entry));
        assertTrue(r.getSettlementNo().startsWith("FS-"));
    }

    @Test
    void confirm_DRAFT状态_改CONFIRMED并更新业务单据() {
        ArapSettlementEntity e = new ArapSettlementEntity();
        e.setId(1L);
        e.setStatus("DRAFT");
        when(mapper.selectById(1L)).thenReturn(e);

        ArapSettlementEntryEntity entry = new ArapSettlementEntryEntity();
        entry.setBusinessDocId(100L);
        entry.setSettledAmount(new BigDecimal("200"));
        when(entryMapper.selectList(any())).thenReturn(List.of(entry));

        BusinessDocEntity doc = new BusinessDocEntity();
        doc.setId(100L);
        doc.setAmount(new BigDecimal("1000"));
        doc.setSettledAmount(BigDecimal.ZERO);
        doc.setUnsettledAmount(new BigDecimal("1000"));
        doc.setStatus("APPROVED");
        when(businessDocMapper.selectById(100L)).thenReturn(doc);
        when(businessDocMapper.updateById(any(BusinessDocEntity.class))).thenReturn(1);

        ArapSettlementEntity out = service.confirm(1L);
        assertEquals("CONFIRMED", out.getStatus());
        assertEquals(0, new BigDecimal("200").compareTo(doc.getSettledAmount()));
        assertEquals(0, new BigDecimal("800").compareTo(doc.getUnsettledAmount()));
        verify(businessDocMapper).updateById(doc);
        verify(mapper).updateById(e);
    }

    @Test
    void confirm_非DRAFT_throw() {
        ArapSettlementEntity e = new ArapSettlementEntity();
        e.setId(1L);
        e.setStatus("CONFIRMED");
        when(mapper.selectById(1L)).thenReturn(e);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.confirm(1L));
        assertTrue(ex.getMessage().contains("仅草稿"));
    }

    @Test
    void delete_DRAFT状态_删entry和主单() {
        ArapSettlementEntity e = new ArapSettlementEntity();
        e.setId(1L);
        e.setStatus("DRAFT");
        when(mapper.selectById(1L)).thenReturn(e);
        service.delete(1L);
        verify(entryMapper).delete(any());
        verify(mapper).deleteById(1L);
    }

    @Test
    void delete_非DRAFT_throw() {
        ArapSettlementEntity e = new ArapSettlementEntity();
        e.setId(1L);
        e.setStatus("CONFIRMED");
        when(mapper.selectById(1L)).thenReturn(e);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.delete(1L));
        assertTrue(ex.getMessage().contains("仅草稿"));
    }

    @Test
    void confirm_receivableIdOldPath_throws() {
        // P38-F5: 旧格式 receivableId 应抛 BusinessException
        ArapSettlementEntity e = new ArapSettlementEntity();
        e.setId(1L);
        e.setStatus("DRAFT");
        when(mapper.selectById(1L)).thenReturn(e);
        when(entryMapper.selectList(any())).thenReturn(List.of(
                new ArapSettlementEntryEntity() {{ setReceivableId(100L); setSettledAmount(new BigDecimal("100")); }}
        ));
        BusinessException ex = assertThrows(BusinessException.class, () -> service.confirm(1L));
        assertTrue(ex.getMessage().contains("receivableId") || ex.getMessage().contains("旧格式"));
    }

    @Test
    void confirm_payableIdOldPath_throws() {
        // P38-F5: 旧格式 payableId 应抛 BusinessException
        ArapSettlementEntity e = new ArapSettlementEntity();
        e.setId(1L);
        e.setStatus("DRAFT");
        when(mapper.selectById(1L)).thenReturn(e);
        when(entryMapper.selectList(any())).thenReturn(List.of(
                new ArapSettlementEntryEntity() {{ setPayableId(200L); setSettledAmount(new BigDecimal("100")); }}
        ));
        BusinessException ex = assertThrows(BusinessException.class, () -> service.confirm(1L));
        assertTrue(ex.getMessage().contains("payableId") || ex.getMessage().contains("旧格式"));
    }
}
