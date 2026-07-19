package com.huicai.base.system.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huicai.base.system.entity.UserEntity;
import com.huicai.base.system.mapper.DeptMapper;
import com.huicai.base.system.mapper.UserMapper;
import com.huicai.base.system.mapper.UserRoleMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock private UserMapper userMapper;
    @Mock private DeptMapper deptMapper;
    @Mock private UserRoleMapper userRoleMapper;
    @Mock private PasswordEncoder passwordEncoder;
    @InjectMocks private UserServiceImpl service;

    private UserEntity stubEntity() {
        UserEntity e = new UserEntity();
        e.setId(1L);
        e.setUsername("admin");
        e.setRealName("管理员");
        e.setStatus("ACTIVE");
        e.setDeptId(1L);
        return e;
    }

    @Test
    void getById_存在_返回Entity() {
        when(userMapper.selectById(1L)).thenReturn(stubEntity());
        UserEntity result = service.getById(1L);
        assertNotNull(result);
    }

    @Test
    void getByUsername_存在_返回Entity() {
        when(userMapper.selectByUsername("admin")).thenReturn(stubEntity());
        UserEntity result = service.getByUsername("admin");
        assertNotNull(result);
    }

    @Test
    void pageUser_调selectPage() {
        Page<UserEntity> pageResult = new Page<>(1, 20);
        pageResult.setRecords(java.util.Collections.singletonList(stubEntity()));
        when(userMapper.selectPage(any(Page.class), any())).thenReturn(pageResult);
        service.pageUser(1, 20, null, null, null);
        verify(userMapper).selectPage(any(Page.class), any());
    }

    @Test
    void updateStatus_调updateById() {
        service.updateStatus(1L, "DISABLED");
        verify(userMapper).updateById(any(UserEntity.class));
    }

    @Test
    void resetPassword_调updateById() {
        when(passwordEncoder.encode(any())).thenReturn("encoded");
        service.resetPassword(1L, "newpass123");
        verify(userMapper).updateById(any(UserEntity.class));
    }
}
