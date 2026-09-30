package com.huicai.base.system.mapper;

import com.huicai.base.system.entity.UserEntity;
import com.huicai.common.test.AbstractMapperTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;

/**
 * User Mapper 真实 DB 测试.
 * 验证 NOT NULL 约束、多租户字段落库.
 *
 * <h3>历史说明（REQ-2026-121 / 慢测 C 类）</h3>
 * 本测试原用 {@code status="enabled"} 与 {@code userType="employee"}，二者都不在
 * DB 的 CHECK 允许集内，导致插入在到达被测约束前就被拦下：
 * <ul>
 *   <li>{@code chk_user_status} = ACTIVE / INACTIVE / LOCKED</li>
 *   <li>{@code chk_user_type}   = SUPER_ADMIN / AGENCY / ENTERPRISE</li>
 * </ul>
 * 注意 {@code chk_user_status} 会<b>先于</b> {@code chk_user_type} 报错，
 * 所以原先只看到 status 的报错，掩盖了 userType 同样非法的问题 —— 两个都已修正。
 */
class UserMapperRealDBTest extends AbstractMapperTest {

    @Autowired
    private UserMapper userMapper;

    private static final String STATUS_ACTIVE = "ACTIVE";      // chk_user_status
    private static final String TYPE_ENTERPRISE = "ENTERPRISE"; // chk_user_type

    @Test
    @DisplayName("插入用户应返回自增主键")
    void insert_shouldReturnId() {
        UserEntity entity = new UserEntity();
        entity.setUsername("test_user_001");
        entity.setPassword("encoded_pass");
        entity.setRealName("测试用户");
        entity.setStatus(STATUS_ACTIVE);
        entity.setDeleted(0);
        entity.setCreatedBy(1L);
        entity.setUpdatedBy(1L);
        entity.setUserType(TYPE_ENTERPRISE);
        entity.setEnterpriseId(DEFAULT_ENTERPRISE_ID);

        assertEquals(1, userMapper.insert(entity));
        assertNotNull(entity.getId());
    }

    @Test
    @DisplayName("按主键回查应返回用户名")
    void selectById_shouldReturnUser() {
        UserEntity entity = new UserEntity();
        entity.setUsername("test_user_002");
        entity.setPassword("encoded_pass");
        entity.setRealName("测试用户2");
        entity.setStatus(STATUS_ACTIVE);
        entity.setDeleted(0);
        entity.setCreatedBy(1L);
        entity.setUpdatedBy(1L);
        entity.setUserType(TYPE_ENTERPRISE);
        entity.setEnterpriseId(DEFAULT_ENTERPRISE_ID);
        userMapper.insert(entity);

        UserEntity found = userMapper.selectById(entity.getId());
        assertNotNull(found);
        assertEquals("test_user_002", found.getUsername());
    }

    @Test
    @DisplayName("缺少 username 应触发 NOT NULL 约束")
    void insert_shouldFailWithoutUsername() {
        UserEntity entity = new UserEntity();
        entity.setPassword("encoded_pass");
        entity.setStatus(STATUS_ACTIVE);
        entity.setDeleted(0);
        entity.setCreatedBy(1L);
        entity.setUpdatedBy(1L);
        entity.setUserType(TYPE_ENTERPRISE);
        assertThrows(Exception.class, () -> userMapper.insert(entity),
                "username 为 NOT NULL");
    }
}
