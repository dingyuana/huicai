package com.huicai.module.arap.service;

import com.huicai.common.exception.BusinessException;
import com.huicai.module.finance.entity.BusinessDocEntity;
import com.huicai.module.finance.mapper.BusinessDocMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DuplicatePaymentValidatorTest {

    @Mock
    private BusinessDocMapper businessDocMapper;

    @InjectMocks
    private DuplicatePaymentValidator validator;

    @Test
    @DisplayName("无重复发票号_校验通过")
    void noDuplicate_passes() {
        when(businessDocMapper.selectList(any())).thenReturn(List.of());
        assertDoesNotThrow(() -> validator.validate("INV001", 1L, true));
    }

    @Test
    @DisplayName("发票号为null_跳过校验")
    void nullInvoice_skips() {
        assertDoesNotThrow(() -> validator.validate(null, 1L, true));
    }

    @Test
    @DisplayName("已核销重复付款_抛异常")
    void settledDuplicate_throws() {
        BusinessDocEntity doc = new BusinessDocEntity();
        doc.setSettledAmount(BigDecimal.valueOf(50000));
        when(businessDocMapper.selectList(any())).thenReturn(List.of(doc));

        BusinessException ex = assertThrows(BusinessException.class,
            () -> validator.validate("INV001", 1L, true));
        assertTrue(ex.getMessage().contains("重复付款"));
    }

    @Test
    @DisplayName("未核销重复付款_严格模式抛异常")
    void unsettledDuplicate_strict_throws() {
        BusinessDocEntity doc = new BusinessDocEntity();
        doc.setSettledAmount(BigDecimal.ZERO);
        when(businessDocMapper.selectList(any())).thenReturn(List.of(doc));

        BusinessException ex = assertThrows(BusinessException.class,
            () -> validator.validate("INV001", 1L, true));
        assertTrue(ex.getMessage().contains("未核销"));
    }

    @Test
    @DisplayName("未核销重复付款_宽松模式通过")
    void unsettledDuplicate_nonStrict_passes() {
        BusinessDocEntity doc = new BusinessDocEntity();
        doc.setSettledAmount(BigDecimal.ZERO);
        when(businessDocMapper.selectList(any())).thenReturn(List.of(doc));

        assertDoesNotThrow(() -> validator.validate("INV001", 1L, false));
    }
}