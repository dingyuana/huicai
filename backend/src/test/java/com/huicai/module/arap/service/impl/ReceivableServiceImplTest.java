package com.huicai.module.arap.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huicai.common.exception.BusinessException;
import com.huicai.module.arap.dto.ReceivableVO;
import com.huicai.module.arap.entity.ReceivableEntity;
import com.huicai.module.arap.mapper.ReceivableMapper;
import com.huicai.module.arap.mapper.CustomerMapper;
import com.huicai.module.system.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReceivableServiceImplTest {

    @Mock private ReceivableMapper mapper;
    @Mock private CustomerMapper customerMapper;
    @Mock private UserMapper userMapper;

    @InjectMocks private ReceivableServiceImpl service;

    private ReceivableEntity stubRec(Long id, Long customerId, BigDecimal unsettled) {
        ReceivableEntity r = new ReceivableEntity();
        r.setId(id);
        r.setCustomerId(customerId);
        r.setAmount(unsettled);
        r.setSettledAmount(BigDecimal.ZERO);
        r.setUnsettledAmount(unsettled);
        return r;
    }

    @Test
    void pageQuery_带customer和period_调selectPage() {
        Page<ReceivableEntity> emptyPage = new Page<>(1, 20, 0);
        when(mapper.selectPage(any(), any())).thenReturn(emptyPage);
        IPage<ReceivableVO> r = service.pageQuery(1L, "202606", 1, 20);
        assertNotNull(r);
        assertEquals(0, r.getTotal());
        verify(mapper).selectPage(any(), any());
    }

    @Test
    void getById_存在_返回entity() {
        when(mapper.selectById(1L)).thenReturn(stubRec(1L, 1L, new BigDecimal("500")));
        when(customerMapper.selectBatchIds(any())).thenReturn(Collections.emptyList());
        ReceivableVO r = service.getById(1L);
        assertNotNull(r);
    }

    @Test
    void getById_不存在_throw() {
        when(mapper.selectById(99L)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.getById(99L));
        assertTrue(ex.getMessage().contains("应收明细不存在"));
    }

    @Test
    void create_settledAmount为null_默认0() {
        ReceivableEntity r = new ReceivableEntity();
        r.setAmount(new BigDecimal("1000"));
        ReceivableEntity out = service.create(r);
        assertEquals(0, BigDecimal.ZERO.compareTo(out.getSettledAmount()));
        assertEquals(0, new BigDecimal("1000").compareTo(out.getUnsettledAmount()));
    }

    @Test
    void overdueList_调mapper() {
        when(mapper.overdueList()).thenReturn(List.of());
        List<Map<String, Object>> r = service.overdueList();
        assertNotNull(r);
        verify(mapper).overdueList();
    }

    @Test
    void agingAnalysis_无数据_全部bucket为0() {
        when(mapper.agingByCustomer(1L)).thenReturn(List.of());
        Map<String, Object> r = service.agingAnalysis(1L);
        assertEquals(0, ((BigDecimal) r.get("total")).compareTo(BigDecimal.ZERO));
    }

    @Test
    void overallAging_3条应收_总额等于sum_count等于3() {
        when(mapper.selectList(any())).thenReturn(List.of(
                stubRec(1L, 1L, new BigDecimal("100")),
                stubRec(2L, 1L, new BigDecimal("200")),
                stubRec(3L, 1L, new BigDecimal("300"))
        ));
        Map<String, Object> r = service.overallAging();
        assertEquals(0, new BigDecimal("600").compareTo((BigDecimal) r.get("totalAmount")));
        assertEquals(3, r.get("totalCount"));
    }
}
