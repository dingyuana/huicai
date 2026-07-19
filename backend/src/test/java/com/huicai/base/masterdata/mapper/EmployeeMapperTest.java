package com.huicai.base.masterdata.mapper;

import com.huicai.base.masterdata.entity.EmployeeEntity;
import com.huicai.base.masterdata.mapper.EmployeeMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mockito;
import static org.junit.jupiter.api.Assertions.*;

public class EmployeeMapperTest {

    @Test
    @DisplayName("EmployeeMapper insert 方法应接受正确参数")
    void insert_shouldAcceptValidParams() {
        EmployeeMapper mapper = Mockito.mock(EmployeeMapper.class);
        EmployeeEntity entity = new EmployeeEntity();
        entity.setCode("EMP001");
        entity.setName("测试员工");
        entity.setDeptId(1L);
        entity.setPhone("13800000002");
        entity.setEmail("emp@example.com");
        entity.setBankName("工商银行");
        entity.setBankAccount("6222000087654321");
        entity.setIdCard("110101199001011234");
        entity.setIsActive(true);
        entity.setRemark("测试员工");
        Mockito.when(mapper.insert(entity)).thenReturn(1);
        int rows = mapper.insert(entity);
        assertEquals(1, rows);
        Mockito.verify(mapper).insert(entity);
    }

    @Test
    @DisplayName("EmployeeMapper selectById 方法应返回实体")
    void selectById_shouldReturnEntity() {
        EmployeeMapper mapper = Mockito.mock(EmployeeMapper.class);
        EmployeeEntity entity = new EmployeeEntity();
        entity.setCode("EMP001");
        entity.setName("测试员工");
        entity.setDeptId(1L);
        entity.setPhone("13800000002");
        entity.setEmail("emp@example.com");
        entity.setBankName("工商银行");
        entity.setBankAccount("6222000087654321");
        entity.setIdCard("110101199001011234");
        entity.setIsActive(true);
        entity.setRemark("测试员工");
        Mockito.when(mapper.selectById(1L)).thenReturn(entity);
        EmployeeEntity result = mapper.selectById(1L);
        assertNotNull(result);
        Mockito.verify(mapper).selectById(1L);
    }

    @Test
    @DisplayName("EmployeeMapper updateById 方法应接受实体参数")
    void updateById_shouldAcceptEntity() {
        EmployeeMapper mapper = Mockito.mock(EmployeeMapper.class);
        EmployeeEntity entity = new EmployeeEntity();
        entity.setCode("EMP001");
        entity.setName("测试员工");
        entity.setDeptId(1L);
        entity.setPhone("13800000002");
        entity.setEmail("emp@example.com");
        entity.setBankName("工商银行");
        entity.setBankAccount("6222000087654321");
        entity.setIdCard("110101199001011234");
        entity.setIsActive(true);
        entity.setRemark("测试员工");
        Mockito.when(mapper.updateById(entity)).thenReturn(1);
        int rows = mapper.updateById(entity);
        assertEquals(1, rows);
        Mockito.verify(mapper).updateById(entity);
    }

    @Test
    @DisplayName("EmployeeMapper deleteById 方法应接受ID参数")
    void deleteById_shouldAcceptId() {
        EmployeeMapper mapper = Mockito.mock(EmployeeMapper.class);
        Mockito.when(mapper.deleteById(1L)).thenReturn(1);
        int rows = mapper.deleteById(1L);
        assertEquals(1, rows);
        Mockito.verify(mapper).deleteById(1L);
    }

    @Test
    @DisplayName("EmployeeMapper 所有方法定义应正确")
    void allMethods_shouldBeDefined() {
        EmployeeMapper mapper = Mockito.mock(EmployeeMapper.class);
        EmployeeEntity e = new EmployeeEntity();
        e.setCode("EMP001");
        e.setName("测试员工");
        e.setDeptId(1L);
        e.setPhone("13800000002");
        e.setEmail("emp@example.com");
        e.setBankName("工商银行");
        e.setBankAccount("6222000087654321");
        e.setIdCard("110101199001011234");
        e.setIsActive(true);
        e.setRemark("测试员工");
        Mockito.when(mapper.insert(e)).thenReturn(1);
        Mockito.when(mapper.selectById(1L)).thenReturn(e);
        Mockito.when(mapper.updateById(e)).thenReturn(1);
        Mockito.when(mapper.deleteById(1L)).thenReturn(1);
        assertEquals(1, mapper.insert(e));
        assertNotNull(mapper.selectById(1L));
        assertEquals(1, mapper.updateById(e));
        assertEquals(1, mapper.deleteById(1L));
    }
}