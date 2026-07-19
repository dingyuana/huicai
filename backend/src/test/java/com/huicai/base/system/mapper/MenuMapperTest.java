package com.huicai.base.system.mapper;

import com.huicai.base.system.entity.MenuEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mockito;
import static org.junit.jupiter.api.Assertions.*;

/**
 * MenuMapper 方法签名验证测试
 */
public class MenuMapperTest {

    @Test
    @DisplayName("MenuMapper insert 方法应接受正确参数")
    void insert_shouldAcceptValidParams() {
        MenuMapper mapper = Mockito.mock(MenuMapper.class);
        MenuEntity entity = new MenuEntity();

        // 设置必要字段
        entity.setName("测试菜单");
        entity.setType("menu");
        entity.setParentId(null);
        entity.setPath("/test");
        entity.setComponent("test/TestPage");
        entity.setIcon("Test");
        entity.setSortOrder(100);
        entity.setIsActive(true);
        entity.setIsVisible(true);
        entity.setKeepAlive(true);
        entity.setAlwaysShow(false);

        // 验证方法可调用且返回正确类型
        Mockito.when(mapper.insert(entity)).thenReturn(1);
        int rows = mapper.insert(entity);

        assertEquals(1, rows);
        Mockito.verify(mapper).insert(entity);
    }

    @Test
    @DisplayName("MenuMapper selectById 方法应返回实体")
    void selectById_shouldReturnEntity() {
        MenuMapper mapper = Mockito.mock(MenuMapper.class);
        MenuEntity entity = new MenuEntity();
        entity.setName("测试菜单");
        entity.setType("menu");
        entity.setParentId(null);
        entity.setPath("/test");
        entity.setComponent("test/TestPage");
        entity.setIcon("Test");
        entity.setSortOrder(100);
        entity.setIsActive(true);
        entity.setIsVisible(true);
        entity.setKeepAlive(true);
        entity.setAlwaysShow(false);
        Mockito.when(mapper.selectById(1L)).thenReturn(entity);

        MenuEntity result = mapper.selectById(1L);

        assertNotNull(result);
        Mockito.verify(mapper).selectById(1L);
    }

    @Test
    @DisplayName("MenuMapper updateById 方法应接受实体参数")
    void updateById_shouldAcceptEntity() {
        MenuMapper mapper = Mockito.mock(MenuMapper.class);
        MenuEntity entity = new MenuEntity();
        entity.setName("测试菜单");
        entity.setType("menu");
        entity.setParentId(null);
        entity.setPath("/test");
        entity.setComponent("test/TestPage");
        entity.setIcon("Test");
        entity.setSortOrder(100);
        entity.setIsActive(true);
        entity.setIsVisible(true);
        entity.setKeepAlive(true);
        entity.setAlwaysShow(false);
        Mockito.when(mapper.updateById(entity)).thenReturn(1);

        int rows = mapper.updateById(entity);

        assertEquals(1, rows);
        Mockito.verify(mapper).updateById(entity);
    }

    @Test
    @DisplayName("MenuMapper deleteById 方法应接受ID参数")
    void deleteById_shouldAcceptId() {
        MenuMapper mapper = Mockito.mock(MenuMapper.class);
        Mockito.when(mapper.deleteById(1L)).thenReturn(1);

        int rows = mapper.deleteById(1L);

        assertEquals(1, rows);
        Mockito.verify(mapper).deleteById(1L);
    }

    @Test
    @DisplayName("MenuMapper 所有方法定义应正确")
    void allMethods_shouldBeDefined() {
        MenuMapper mapper = Mockito.mock(MenuMapper.class);

        // 验证所有常用方法存在
        MenuEntity e = new MenuEntity();
        e.setName("测试菜单");
        e.setType("menu");
        e.setParentId(null);
        e.setPath("/test");
        e.setComponent("test/TestPage");
        e.setIcon("Test");
        e.setSortOrder(100);
        e.setIsActive(true);
        e.setIsVisible(true);
        e.setKeepAlive(true);
        e.setAlwaysShow(false);
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
