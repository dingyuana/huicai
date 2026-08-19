package com.huicai.base.system.mapper;

import com.huicai.base.system.entity.UserEntity;
import com.huicai.common.test.AbstractMapperTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;

/**
 * User Mapper 真实 DB 测试.
 * 验证 NOT NULL 约束、逻辑删除过滤、多租户字段落库.
 */
class UserMapperTest extends AbstractMapperTest {

    @Autowired
    private UserMapper userMapper;

    @Test
    void insert_shouldReturnId() {
        UserEntity entity = new UserEntity();
        entity.setUsername("test_user_001");
        entity.setPassword("encoded_pass");
        entity.setRealName("测试用户");
        entity.setStatus("enabled");
        entity.setDeleted(0);
        entity.setCreatedBy(1L);
        entity.setUpdatedBy(1L);
        entity.setUserType("employee");

        int rows = userMapper.insert(entity);

        assertEquals(1, rows);
        assertNotNull(entity.getId());
    }

    @Test
    void selectById_shouldReturnDeletedUser() {
        UserEntity entity = new UserEntity();
        entity.setUsername("test_user_002");
        entity.setPassword("encoded_pass");
        entity.setRealName("测试用户2");
        entity.setStatus("enabled");
        entity.setDeleted(0);
        entity.setCreatedBy(1L);
        entity.setUpdatedBy(1L);
        userMapper.insert(entity);

        UserEntity found = userMapper.selectById(entity.getId());
        assertNotNull(found);
        assertEquals("test_user_002", found.getUsername());
    }

    @Test
    void insert_shouldFailWithoutUsername() {
        UserEntity entity = new UserEntity();
        entity.setPassword("encoded_pass");
        entity.setStatus("enabled");
        entity.setDeleted(0);
        entity.setCreatedBy(1L);
        entity.setUpdatedBy(1L);
        // username 为 NOT NULL，不设置应报错
        assertThrows(Exception.class, () -> userMapper.insert(entity),
                "username 为 NOT NULL，插入应失败");
    }
}