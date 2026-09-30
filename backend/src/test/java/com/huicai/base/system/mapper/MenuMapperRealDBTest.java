package com.huicai.base.system.mapper;

import com.huicai.base.system.entity.MenuEntity;
import com.huicai.common.test.AbstractMapperTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Menu Mapper 真实 DB 测试.
 *
 * <h3>历史说明（REQ-2026-121）</h3>
 * {@code t_menu.menu_code} 是 NOT NULL 且无默认值，而 {@code MenuEntity} 原先
 * <b>没有 menuCode 字段</b>，导致任何经 MyBatis-Plus 插入菜单的路径都必然失败
 * （含生产 {@code MenuServiceImpl.create}）。已在 REQ-2026-121 补齐 Entity 字段。
 */
class MenuMapperRealDBTest extends AbstractMapperTest {

    @Autowired
    private MenuMapper menuMapper;

    @Test
    void insert_shouldReturnId() {
        MenuEntity entity = new MenuEntity();
        entity.setName("测试菜单");
        entity.setMenuCode("MENU_TEST_" + System.nanoTime());
        entity.setPermissionCode("system:test_" + System.currentTimeMillis());
        // chk_menu_type 允许集为大写 MENU / BUTTON / DIR
        entity.setType("MENU");
        entity.setParentId(0L);
        entity.setSortOrder(1);
        entity.setIsActive(true);
        entity.setDeleted(0);
        entity.setCreatedBy(1L);
        entity.setUpdatedBy(1L);

        assertEquals(1, menuMapper.insert(entity));
        assertNotNull(entity.getId());
    }
}