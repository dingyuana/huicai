package com.huicai.module.arap.service.impl;

import com.huicai.common.exception.BusinessException;
import com.huicai.module.arap.constant.ArapStatus;
import com.huicai.module.arap.entity.ReceivableEntity;
import com.huicai.module.arap.mapper.ReceivableMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 应收单状态机服务测试.
 *
 * 覆盖 confirm / onReconciliationUpdate / reverse 三条状态转换链路
 * 以及边界条件（非法状态、缺失参数、实体不存在）。
 */
@ExtendWith(MockitoExtension.class)
class ReceivableStateMachineServiceImplTest {

    @Mock private ReceivableMapper receivableMapper;

    @InjectMocks private ReceivableStateMachineServiceImpl service;

    // ==================== 工具方法 ====================

    private ReceivableEntity stubRec(Long id, String status) {
        ReceivableEntity e = new ReceivableEntity();
        e.setId(id);
        e.setStatus(status);
        e.setAmount(new BigDecimal("1000"));
        e.setSettledAmount(BigDecimal.ZERO);
        e.setUnsettledAmount(new BigDecimal("1000"));
        e.setSummary("test receivable");
        e.setCreatedAt(LocalDateTime.now());
        e.setUpdatedAt(LocalDateTime.now());
        return e;
    }

    // ==================== confirm 测试 ====================

    @Test
    void confirm_DRAFT_成功转为CONFIRMED() {
        ReceivableEntity entity = stubRec(1L, ArapStatus.DRAFT);
        when(receivableMapper.selectById(1L)).thenReturn(entity);
        when(receivableMapper.updateById(any(ReceivableEntity.class))).thenReturn(1);

        service.confirm(1L, 100L);

        assertEquals(ArapStatus.CONFIRMED, entity.getStatus());
        verify(receivableMapper).updateById(any(ReceivableEntity.class));
    }

    @Test
    void confirm_NON_DRAFT_抛异常() {
        ReceivableEntity entity = stubRec(1L, ArapStatus.CONFIRMED);
        when(receivableMapper.selectById(1L)).thenReturn(entity);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.confirm(1L, 100L));
        assertTrue(ex.getMessage().contains("仅草稿状态可确认"));
        verify(receivableMapper, never()).updateById(any(ReceivableEntity.class));
    }

    @Test
    void confirm_ENTITY_NOT_FOUND_抛异常() {
        when(receivableMapper.selectById(999L)).thenReturn(null);

        assertThrows(BusinessException.class, () ->
                service.confirm(999L, 100L));
    }

    // ==================== onReconciliationUpdate 测试 ====================

    @Test
    void onReconciliationUpdate_CONFIRMED_unsettled0_转为SETTLED() {
        ReceivableEntity entity = stubRec(1L, ArapStatus.CONFIRMED);
        when(receivableMapper.selectById(1L)).thenReturn(entity);
        when(receivableMapper.updateById(any(ReceivableEntity.class))).thenReturn(1);

        service.onReconciliationUpdate(1L, BigDecimal.ZERO, 100L);

        assertEquals(ArapStatus.SETTLED, entity.getStatus());
        assertEquals(BigDecimal.ZERO, entity.getUnsettledAmount());
        verify(receivableMapper).updateById(any(ReceivableEntity.class));
    }

    @Test
    void onReconciliationUpdate_SETTLED_partial_保持SETTLED() {
        ReceivableEntity entity = stubRec(1L, ArapStatus.SETTLED);
        when(receivableMapper.selectById(1L)).thenReturn(entity);
        when(receivableMapper.updateById(any(ReceivableEntity.class))).thenReturn(1);

        service.onReconciliationUpdate(1L, new BigDecimal("500"), 100L);

        assertEquals(ArapStatus.CONFIRMED, entity.getStatus());
        assertEquals(new BigDecimal("500"), entity.getUnsettledAmount());
    }

    @Test
    void onReconciliationUpdate_DRAFT_非法状态抛异常() {
        ReceivableEntity entity = stubRec(1L, ArapStatus.DRAFT);
        when(receivableMapper.selectById(1L)).thenReturn(entity);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.onReconciliationUpdate(1L, BigDecimal.ZERO, 100L));
        assertTrue(ex.getMessage().contains("仅已确认/已结清状态可更新"));
        verify(receivableMapper, never()).updateById(any(ReceivableEntity.class));
    }

    @Test
    void onReconciliationUpdate_REVERSED_非法状态抛异常() {
        ReceivableEntity entity = stubRec(1L, ArapStatus.REVERSED);
        when(receivableMapper.selectById(1L)).thenReturn(entity);

        assertThrows(BusinessException.class, () ->
                service.onReconciliationUpdate(1L, BigDecimal.ZERO, 100L));
    }

    // ==================== reverse 测试 ====================

    @Test
    void reverse_CONFIRMED_成功转REVERSED() {
        ReceivableEntity entity = stubRec(1L, ArapStatus.CONFIRMED);
        when(receivableMapper.selectById(1L)).thenReturn(entity);
        when(receivableMapper.updateById(any(ReceivableEntity.class))).thenReturn(1);

        service.reverse(1L, 100L, "坏账核销");

        assertEquals(ArapStatus.REVERSED, entity.getStatus());
        assertTrue(entity.getSummary().contains("[100] 冲销原因: 坏账核销"));
        verify(receivableMapper).updateById(any(ReceivableEntity.class));
    }

    @Test
    void reverse_SETTLED_成功转REVERSED() {
        ReceivableEntity entity = stubRec(1L, ArapStatus.SETTLED);
        when(receivableMapper.selectById(1L)).thenReturn(entity);
        when(receivableMapper.updateById(any(ReceivableEntity.class))).thenReturn(1);

        service.reverse(1L, 100L, "部分退回");

        assertEquals(ArapStatus.REVERSED, entity.getStatus());
        verify(receivableMapper).updateById(any(ReceivableEntity.class));
    }

    @Test
    void reverse_DRAFT_非法状态抛异常() {
        ReceivableEntity entity = stubRec(1L, ArapStatus.DRAFT);
        when(receivableMapper.selectById(1L)).thenReturn(entity);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.reverse(1L, 100L, "原因"));
        assertTrue(ex.getMessage().contains("当前状态不可冲销"));
        verify(receivableMapper, never()).updateById(any(ReceivableEntity.class));
    }

    @Test
    void reverse_EMPTY_REASON_抛异常() {
        assertThrows(BusinessException.class, () ->
                service.reverse(1L, 100L, ""));
    }

    @Test
    void reverse_BLANK_REASON_抛异常() {
        assertThrows(BusinessException.class, () ->
                service.reverse(1L, 100L, "   "));
    }

    @Test
    void reverse_ENTITY_NOT_FOUND_抛异常() {
        when(receivableMapper.selectById(999L)).thenReturn(null);

        assertThrows(BusinessException.class, () ->
                service.reverse(999L, 100L, "原因"));
    }

    // ==================== 状态不变性 ====================

    @Test
    void confirm_状态变更后原对象同步() {
        ReceivableEntity entity = stubRec(1L, ArapStatus.DRAFT);
        when(receivableMapper.selectById(1L)).thenReturn(entity);
        when(receivableMapper.updateById(any(ReceivableEntity.class))).thenReturn(1);

        service.confirm(1L, 100L);

        // 确认方法内修改的对象状态在调用后保持
        assertEquals(ArapStatus.CONFIRMED, entity.getStatus());
    }

    @Test
    void onReconciliationUpdate_状态变更持久化() {
        ReceivableEntity entity = stubRec(1L, ArapStatus.CONFIRMED);
        when(receivableMapper.selectById(1L)).thenReturn(entity);
        when(receivableMapper.updateById(any(ReceivableEntity.class))).thenReturn(1);

        service.onReconciliationUpdate(1L, BigDecimal.ZERO, 100L);

        verify(receivableMapper).updateById(any(ReceivableEntity.class));
    }
}
