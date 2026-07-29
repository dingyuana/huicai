package com.huicai.base.masterdata.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huicai.base.business.mapper.BusinessDocMapper;
import com.huicai.base.masterdata.entity.CustomerEntity;
import com.huicai.base.masterdata.mapper.CustomerMapper;
import com.huicai.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
@DisplayName("客户管理服务单元测试")
class CustomerServiceImplTest {

    @Mock private CustomerMapper mapper;
    @Mock private BusinessDocMapper businessDocMapper;
    @InjectMocks private CustomerServiceImpl service;

    private CustomerEntity stubCustomer(Long id, String code, String name) {
        CustomerEntity entity = new CustomerEntity();
        entity.setId(id);
        entity.setCode(code);
        entity.setName(name);
        entity.setIsActive(true);
        return entity;
    }

    @Test
    @DisplayName("getById: 存在返回实体")
    void getById_存在_返回entity() {
        when(mapper.selectById(1L)).thenReturn(stubCustomer(1L, "C001", "客户A"));

        CustomerEntity r = service.getById(1L);

        assertNotNull(r);
        assertEquals("C001", r.getCode());
        assertEquals("客户A", r.getName());
    }

    @Test
    @DisplayName("getById: 不存在抛BusinessException")
    void getById_不存在_throw() {
        when(mapper.selectById(99L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.getById(99L));

        assertTrue(ex.getMessage().contains("客户不存在"));
    }

    @Test
    @DisplayName("create: 成功创建")
    void create_成功() {
        CustomerEntity entity = stubCustomer(null, "C001", "客户A");
        when(mapper.selectCount(any())).thenReturn(0L);
        when(mapper.insert(any(CustomerEntity.class))).thenReturn(1);

        CustomerEntity r = service.create(entity);

        assertNotNull(r);
        assertEquals("C001", r.getCode());
        verify(mapper).selectCount(any());
        verify(mapper).insert(any(CustomerEntity.class));
    }

    @Test
    @DisplayName("create: 编码重复抛异常")
    void create_编码重复_throw() {
        CustomerEntity entity = stubCustomer(null, "C001", "客户A");
        when(mapper.selectCount(any())).thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(entity));

        assertTrue(ex.getMessage().contains("客户编码已存在"));
        verify(mapper, never()).insert(any(CustomerEntity.class));
    }

    @Test
    @DisplayName("create: 默认值填充(isActive=true, creditLimit=0, creditDays=30)")
    void create_默认值填充() {
        CustomerEntity entity = new CustomerEntity();
        entity.setCode("C001");
        entity.setName("客户A");
        // isActive, creditLimit, creditDays 均未设置，期望默认值
        when(mapper.selectCount(any())).thenReturn(0L);
        when(mapper.insert(any(CustomerEntity.class))).thenReturn(1);

        CustomerEntity r = service.create(entity);

        assertTrue(r.getIsActive());
        assertEquals(BigDecimal.ZERO, r.getCreditLimit());
        assertEquals(Integer.valueOf(30), r.getCreditDays());
        verify(mapper).selectCount(any());
        verify(mapper).insert(any(CustomerEntity.class));
    }

    @Test
    @DisplayName("update: 更新成功")
    void update_成功() {
        CustomerEntity existing = stubCustomer(1L, "C001", "客户A");
        when(mapper.selectById(1L)).thenReturn(existing);
        when(mapper.selectCount(any())).thenReturn(0L);

        CustomerEntity input = new CustomerEntity();
        input.setId(1L);
        input.setCode("C002");
        input.setName("客户A-改");
        input.setContactPerson("张三");
        input.setPhone("13800000000");
        input.setEmail("test@test.com");
        input.setAddress("地址");
        input.setTaxNo("TAX001");
        input.setBankName("银行");
        input.setBankAccount("123456");
        input.setCreditLimit(new BigDecimal("50000"));
        input.setCreditDays(60);
        input.setSubjectId(10L);
        input.setIsActive(true);
        input.setRemark("备注");

        CustomerEntity r = service.update(input);

        ArgumentCaptor<CustomerEntity> captor = ArgumentCaptor.forClass(CustomerEntity.class);
        verify(mapper).updateById(captor.capture());
        CustomerEntity captured = captor.getValue();

        assertEquals("C002", captured.getCode());
        assertEquals("客户A-改", captured.getName());
        assertEquals("张三", captured.getContactPerson());
        assertEquals("13800000000", captured.getPhone());
        assertEquals(new BigDecimal("50000"), captured.getCreditLimit());
        assertEquals(Integer.valueOf(60), captured.getCreditDays());
        assertEquals("备注", captured.getRemark());
        assertNotNull(r);
    }

    @Test
    @DisplayName("update: 不存在抛异常")
    void update_不存在_throw() {
        when(mapper.selectById(99L)).thenReturn(null);

        CustomerEntity input = new CustomerEntity();
        input.setId(99L);
        input.setCode("C099");

        BusinessException ex = assertThrows(BusinessException.class, () -> service.update(input));

        assertTrue(ex.getMessage().contains("客户不存在"));
        verify(mapper, never()).updateById(any(CustomerEntity.class));
    }

    @Test
    @DisplayName("delete: 成功删除")
    void delete_成功() {
        when(mapper.deleteById(1L)).thenReturn(1);

        service.delete(1L);

        verify(mapper).deleteById(1L);
    }

    @Test
    @DisplayName("listAll: 返回激活列表")
    void listAll_返回激活列表() {
        List<CustomerEntity> list = List.of(
                stubCustomer(1L, "C001", "客户A"),
                stubCustomer(2L, "C002", "客户B")
        );
        when(mapper.selectList(any())).thenReturn(list);

        List<CustomerEntity> r = service.listAll();

        assertEquals(2, r.size());
        verify(mapper).selectList(any());
    }

    @Test
    @DisplayName("pageQuery: 关键字搜索")
    void pageQuery_关键字搜索() {
        Page<CustomerEntity> page = new Page<>(1, 20);
        page.setRecords(List.of(stubCustomer(1L, "C001", "客户A")));
        when(mapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        IPage<CustomerEntity> r = service.pageQuery("客户A", null, 1, 20);

        assertNotNull(r);
        assertEquals(1, r.getRecords().size());
        verify(mapper).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("pageQuery: 状态过滤")
    void pageQuery_状态过滤() {
        Page<CustomerEntity> page = new Page<>(1, 20);
        when(mapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        IPage<CustomerEntity> r = service.pageQuery(null, true, 1, 20);

        assertNotNull(r);
        verify(mapper).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("unsettledSummary: 返回汇总")
    void unsettledSummary_返回汇总() {
        List<Map<String, Object>> summary = List.of(
                Map.of("customer_id", 1L, "total_unsettled", new BigDecimal("10000"))
        );
        when(businessDocMapper.aggregateByCustomer()).thenReturn(summary);

        List<Map<String, Object>> r = service.unsettledSummary();

        assertEquals(1, r.size());
        assertEquals(new BigDecimal("10000"), r.get(0).get("total_unsettled"));
        verify(businessDocMapper).aggregateByCustomer();
    }
}