package com.huicai.module.finance.service.impl;

import com.huicai.common.exception.BusinessException;
import com.huicai.module.finance.entity.BankStatementEntity;
import com.huicai.module.finance.mapper.BankJournalMapper;
import com.huicai.module.finance.mapper.BankStatementMapper;
import com.huicai.module.finance.service.ClassificationRuleService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BankStatementServiceTest {

    @Mock private BankStatementMapper statementMapper;
    @Mock private BankJournalMapper journalMapper;
    @Mock private ClassificationRuleService classificationRuleService;
    @Mock private FallbackHeuristicService fallbackHeuristic;

    @InjectMocks private BankStatementServiceImpl service;

    private BankStatementEntity stub(Long id, String classification) {
        BankStatementEntity s = new BankStatementEntity();
        s.setId(id);
        s.setAccountId(1L);
        s.setClassification(classification);
        return s;
    }

    // ==================== review ====================

    @Test
    void review_已分类_标记CONFIRMED() {
        when(statementMapper.selectById(1L)).thenReturn(stub(1L, "bank_fee"));
        when(statementMapper.updateById(any(BankStatementEntity.class))).thenReturn(1);

        BankStatementEntity result = service.review(1L);

        assertNotNull(result);
        assertEquals("CONFIRMED", result.getReviewStatus());
        assertEquals(1L, result.getReviewedBy());
        assertNotNull(result.getReviewedAt());
        verify(statementMapper).updateById(any(BankStatementEntity.class));
    }

    @Test
    void review_不存在_throwNotFound() {
        when(statementMapper.selectById(99L)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.review(99L));
        assertTrue(ex.getMessage().contains("不存在"));
        verify(statementMapper, never()).updateById(any(BankStatementEntity.class));
    }

    @Test
    void review_未分类_throwBadRequest() {
        when(statementMapper.selectById(1L)).thenReturn(stub(1L, null));
        BusinessException ex = assertThrows(BusinessException.class, () -> service.review(1L));
        assertTrue(ex.getMessage().contains("尚未分类"));
        verify(statementMapper, never()).updateById(any(BankStatementEntity.class));
    }

    // ==================== batchReview ====================

    @Test
    void batchReview_空列表_throwBadRequest() {
        BusinessException ex = assertThrows(BusinessException.class, () -> service.batchReview(List.of()));
        assertTrue(ex.getMessage().contains("为空"));
    }

    @Test
    void batchReview_null_throwBadRequest() {
        BusinessException ex = assertThrows(BusinessException.class, () -> service.batchReview(null));
        assertTrue(ex.getMessage().contains("为空"));
    }

    @Test
    void batchReview_混合成功失败_返回成功数() {
        when(statementMapper.selectById(1L)).thenReturn(stub(1L, "bank_fee"));
        when(statementMapper.selectById(2L)).thenReturn(stub(2L, null));
        when(statementMapper.selectById(3L)).thenReturn(null);
        when(statementMapper.updateById(any(BankStatementEntity.class))).thenReturn(1);

        int confirmed = service.batchReview(List.of(1L, 2L, 3L));

        assertEquals(1, confirmed);
        verify(statementMapper, times(1)).updateById(any(BankStatementEntity.class));
    }
}
