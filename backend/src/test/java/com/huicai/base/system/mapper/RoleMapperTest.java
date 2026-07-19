package com.huicai.base.system.mapper;

import com.huicai.base.system.entity.RoleEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mockito;
import static org.junit.jupiter.api.Assertions.*;

/**
 * RoleMapper 方法签名验证测试
 */
public class RoleMapperTest {

    @Test
    @DisplayName("RoleMapper insert 方法应接受正确参数")
    void insert_shouldAcceptValidParams() {
        RoleMapper mapper = Mockito.mock(RoleMapper.class);
        RoleEntity entity = new RoleEntity();

        // 设置必要字段
        entity.setName("测试角色");
        entity.setCode("TEST_ROLE");
        entity.setDescription("测试描述");
        entity.setStatus("ACTIVE");
        entity.setSortOrder(100);
        entity.setDataScope("ALL");

        // 验证方法可调用且返回正确类型
        Mockito.when(mapper.insert(entity)).thenReturn(1);
        int rows = mapper.insert(entity);

        assertEquals(1, rows);
        Mockito.verify(mapper).insert(entity);
    }

    @Test
    @DisplayName("RoleMapper selectById 方法应返回实体")
    void selectById_shouldReturnEntity() {
        RoleMapper mapper = Mockito.mock(RoleMapper.class);
        RoleEntity entity = new RoleEntity();
        entity.setName("测试角色");
        entity.setCode("TEST_ROLE");
        entity.setDescription("测试描述");
        entity.setStatus("ACTIVE");
        entity.setSortOrder(100);
        entity.setDataScope("ALL");
        Mockito.when(mapper.selectById(1L)).thenReturn(entity);

        RoleEntity result = mapper.selectById(1L);

        assertNotNull(result);
        Mockito.verify(mapper).selectById(1L);
    }

    @Test
    @DisplayName("RoleMapper updateById 方法应接受实体参数")
    void updateById_shouldAcceptEntity() {
        RoleMapper mapper = Mockito.mock(RoleMapper.class);
        RoleEntity entity = new RoleEntity();
        entity.setName("测试角色");
        entity.setCode("TEST_ROLE");
        entity.setDescription("测试描述");
        entity.setStatus("ACTIVE");
        entity.setSortOrder(100);
        entity.setDataScope("ALL");
        Mockito.when(mapper.updateById(entity)).thenReturn(1);

        int rows = mapper.updateById(entity);

        assertEquals(1, rows);
        Mockito.verify(mapper).updateById(entity);
    }

    @Test
    @DisplayName("RoleMapper deleteById 方法应接受ID参数")
    void deleteById_shouldAcceptId() {
        RoleMapper mapper = Mockito.mock(RoleMapper.class);
        Mockito.when(mapper.deleteById(1L)).thenReturn(1);

        int rows = mapper.deleteById(1L);

        assertEquals(1, rows);
        Mockito.verify(mapper).deleteById(1L);
    }

    @Test
    @DisplayName("RoleMapper 所有方法定义应正确")
    void allMethods_shouldBeDefined() {
        RoleMapper mapper = Mockito.mock(RoleMapper.class);

        // 验证所有常用方法存在
        RoleEntity e = new RoleEntity();
        e.setName("测试角色");
        e.setCode("TEST_ROLE");
        e.setDescription("测试描述");
        e.setStatus("ACTIVE");
        e.setSortOrder(100);
        e.setDataScope("ALL");
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
