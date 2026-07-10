package com.huicai.module.system.mapper;

import com.huicai.module.system.entity.UserEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mockito;
import static org.junit.jupiter.api.Assertions.*;

/**
 * UserMapper 方法签名验证测试
 */
public class UserMapperTest {

    @Test
    @DisplayName("UserMapper insert 方法应接受正确参数")
    void insert_shouldAcceptValidParams() {
        UserMapper mapper = Mockito.mock(UserMapper.class);
        UserEntity entity = new UserEntity();

        // 设置必要字段
        entity.setUsername("testuser");
        entity.setPassword("password123");
        entity.setRealName("测试用户");
        entity.setNickname("tester");
        entity.setEmail("test@example.com");
        entity.setPhone("13800000001");
        entity.setDeptId(1L);
        entity.setStatus("ACTIVE");
        entity.setCreatedBy(1L);

        // 验证方法可调用且返回正确类型
        Mockito.when(mapper.insert(entity)).thenReturn(1);
        int rows = mapper.insert(entity);

        assertEquals(1, rows);
        Mockito.verify(mapper).insert(entity);
    }

    @Test
    @DisplayName("UserMapper selectById 方法应返回实体")
    void selectById_shouldReturnEntity() {
        UserMapper mapper = Mockito.mock(UserMapper.class);
        UserEntity entity = new UserEntity();
        entity.setUsername("testuser");
        entity.setPassword("password123");
        entity.setRealName("测试用户");
        entity.setNickname("tester");
        entity.setEmail("test@example.com");
        entity.setPhone("13800000001");
        entity.setDeptId(1L);
        entity.setStatus("ACTIVE");
        entity.setCreatedBy(1L);
        Mockito.when(mapper.selectById(1L)).thenReturn(entity);

        UserEntity result = mapper.selectById(1L);

        assertNotNull(result);
        Mockito.verify(mapper).selectById(1L);
    }

    @Test
    @DisplayName("UserMapper updateById 方法应接受实体参数")
    void updateById_shouldAcceptEntity() {
        UserMapper mapper = Mockito.mock(UserMapper.class);
        UserEntity entity = new UserEntity();
        entity.setUsername("testuser");
        entity.setPassword("password123");
        entity.setRealName("测试用户");
        entity.setNickname("tester");
        entity.setEmail("test@example.com");
        entity.setPhone("13800000001");
        entity.setDeptId(1L);
        entity.setStatus("ACTIVE");
        entity.setCreatedBy(1L);
        Mockito.when(mapper.updateById(entity)).thenReturn(1);

        int rows = mapper.updateById(entity);

        assertEquals(1, rows);
        Mockito.verify(mapper).updateById(entity);
    }

    @Test
    @DisplayName("UserMapper deleteById 方法应接受ID参数")
    void deleteById_shouldAcceptId() {
        UserMapper mapper = Mockito.mock(UserMapper.class);
        Mockito.when(mapper.deleteById(1L)).thenReturn(1);

        int rows = mapper.deleteById(1L);

        assertEquals(1, rows);
        Mockito.verify(mapper).deleteById(1L);
    }

    @Test
    @DisplayName("UserMapper 所有方法定义应正确")
    void allMethods_shouldBeDefined() {
        UserMapper mapper = Mockito.mock(UserMapper.class);

        // 验证所有常用方法存在
        UserEntity e = new UserEntity();
        e.setUsername("testuser");
        e.setPassword("password123");
        e.setRealName("测试用户");
        e.setNickname("tester");
        e.setEmail("test@example.com");
        e.setPhone("13800000001");
        e.setDeptId(1L);
        e.setStatus("ACTIVE");
        e.setCreatedBy(1L);
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
