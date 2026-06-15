package com.huicai.module.arap.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.common.exception.BusinessException;
import com.huicai.module.arap.entity.CustomerEntity;
import com.huicai.module.arap.mapper.CustomerMapper;
import com.huicai.module.arap.mapper.ReceivableMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomerServiceImplTest {

    @Mock private CustomerMapper mapper;
    @Mock private ReceivableMapper receivableMapper;

    @InjectMocks private CustomerServiceImpl service;

    private CustomerEntity stubCustomer(Long id, String code, String name) {
        CustomerEntity c = new CustomerEntity();
        c.setId(id);
        c.setCode(code);
        c.setName(name);
        c.setIsActive(true);
        c.setCreditLimit(BigDecimal.ZERO);
        c.setCreditDays(30);
        return c;
    }

    @Test
    void pageQuery_带keyword_调selectPage() {
        when(mapper.selectPage(any(), any())).thenReturn(null);
        IPage<CustomerEntity> r = service.pageQuery("abc", true, 1, 20);
        assertNull(r);
        verify(mapper).selectPage(any(), any());
    }

    @Test
    void listAll_调selectList() {
        when(mapper.selectList(any())).thenReturn(List.of(stubCustomer(1L, "C001", "客户A")));
        List<CustomerEntity> r = service.listAll();
        assertEquals(1, r.size());
        verify(mapper).selectList(any());
    }

    @Test
    void getById_存在_返回entity() {
        when(mapper.selectById(1L)).thenReturn(stubCustomer(1L, "C001", "客户A"));
        CustomerEntity r = service.getById(1L);
        assertNotNull(r);
        assertEquals("C001", r.getCode());
    }

    @Test
    void getById_不存在_throw() {
        when(mapper.selectById(99L)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.getById(99L));
        assertTrue(ex.getMessage().contains("客户不存在"));
    }

    @Test
    void create_编码重复_throw() {
        CustomerEntity c = stubCustomer(null, "C001", "客户A");
        when(mapper.selectCount(any())).thenReturn(1L);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(c));
        assertTrue(ex.getMessage().contains("已存在"));
    }

    @Test
    void create_编码唯一_默认值填充() {
        CustomerEntity c = new CustomerEntity();
        c.setCode("C001");
        c.setName("客户A");
        // isActive/creditLimit/creditDays 都 null
        when(mapper.selectCount(any())).thenReturn(0L);

        CustomerEntity r = service.create(c);
        assertEquals(true, r.getIsActive());
        assertEquals(0, BigDecimal.ZERO.compareTo(r.getCreditLimit()));
        assertEquals(30, r.getCreditDays());
        verify(mapper).insert(c);
    }

    @Test
    void update_正常_字段被覆盖() {
        CustomerEntity existing = stubCustomer(1L, "OLD", "老名");
        CustomerEntity update = stubCustomer(1L, "NEW", "新名");
        update.setPhone("13800000000");
        when(mapper.selectById(1L)).thenReturn(existing);
        when(mapper.selectCount(any())).thenReturn(0L);

        CustomerEntity r = service.update(update);
        assertEquals("NEW", r.getCode());
        assertEquals("新名", r.getName());
        assertEquals("13800000000", r.getPhone());
        verify(mapper).updateById(existing);
    }

    @Test
    void unsettledSummary_调aggregateByCustomer() {
        when(receivableMapper.aggregateByCustomer()).thenReturn(List.of());
        List<Map<String, Object>> r = service.unsettledSummary();
        assertNotNull(r);
        verify(receivableMapper).aggregateByCustomer();
    }
}
