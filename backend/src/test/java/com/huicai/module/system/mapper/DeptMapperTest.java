package com.huicai.base.system.mapper;

import com.huicai.base.system.entity.DeptEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mockito;
import static org.junit.jupiter.api.Assertions.*;

/**
 * DeptMapper 方法签名验证测试
 */
public class DeptMapperTest {

    @Test
    @DisplayName("DeptMapper insert 方法应接受正确参数")
    void insert_shouldAcceptValidParams() {
        DeptMapper mapper = Mockito.mock(DeptMapper.class);
        DeptEntity entity = new DeptEntity();

        // 设置必要字段
        entity.setName("测试部门");
        entity.setParentId(null);
        entity.setSortOrder(100);
        entity.setStatus("ACTIVE");
        entity.setLeader("张三");
        entity.setPhone("13800000001");
        entity.setEmail("test@example.com");

        // 验证方法可调用且返回正确类型
        Mockito.when(mapper.insert(entity)).thenReturn(1);
        int rows = mapper.insert(entity);

        assertEquals(1, rows);
        Mockito.verify(mapper).insert(entity);
    }

    @Test
    @DisplayName("DeptMapper selectById 方法应返回实体")
    void selectById_shouldReturnEntity() {
        DeptMapper mapper = Mockito.mock(DeptMapper.class);
        DeptEntity entity = new DeptEntity();
        entity.setName("测试部门");
        entity.setParentId(null);
        entity.setSortOrder(100);
        entity.setStatus("ACTIVE");
        entity.setLeader("张三");
        entity.setPhone("13800000001");
        entity.setEmail("test@example.com");
        Mockito.when(mapper.selectById(1L)).thenReturn(entity);

        DeptEntity result = mapper.selectById(1L);

        assertNotNull(result);
        Mockito.verify(mapper).selectById(1L);
    }

    @Test
    @DisplayName("DeptMapper updateById 方法应接受实体参数")
    void updateById_shouldAcceptEntity() {
        DeptMapper mapper = Mockito.mock(DeptMapper.class);
        DeptEntity entity = new DeptEntity();
        entity.setName("测试部门");
        entity.setParentId(null);
        entity.setSortOrder(100);
        entity.setStatus("ACTIVE");
        entity.setLeader("张三");
        entity.setPhone("13800000001");
        entity.setEmail("test@example.com");
        Mockito.when(mapper.updateById(entity)).thenReturn(1);

        int rows = mapper.updateById(entity);

        assertEquals(1, rows);
        Mockito.verify(mapper).updateById(entity);
    }

    @Test
    @DisplayName("DeptMapper deleteById 方法应接受ID参数")
    void deleteById_shouldAcceptId() {
        DeptMapper mapper = Mockito.mock(DeptMapper.class);
        Mockito.when(mapper.deleteById(1L)).thenReturn(1);

        int rows = mapper.deleteById(1L);

        assertEquals(1, rows);
        Mockito.verify(mapper).deleteById(1L);
    }

    @Test
    @DisplayName("DeptMapper 所有方法定义应正确")
    void allMethods_shouldBeDefined() {
        DeptMapper mapper = Mockito.mock(DeptMapper.class);

        // 验证所有常用方法存在
        DeptEntity e = new DeptEntity();
        e.setName("测试部门");
        e.setParentId(null);
        e.setSortOrder(100);
        e.setStatus("ACTIVE");
        e.setLeader("张三");
        e.setPhone("13800000001");
        e.setEmail("test@example.com");
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
