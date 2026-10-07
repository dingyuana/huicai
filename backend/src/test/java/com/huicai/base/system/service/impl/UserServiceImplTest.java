package com.huicai.base.system.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huicai.base.system.entity.UserEntity;
import com.huicai.base.system.mapper.DeptMapper;
import com.huicai.base.system.mapper.UserMapper;
import com.huicai.base.system.mapper.UserRoleMapper;
import com.huicai.common.context.EnterpriseContextHolder;
import com.huicai.common.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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

    /** 被测企业；与 stubEntity().enterpriseId 一致 */
    private static final Long ENTERPRISE_ID = 1L;

    @BeforeEach
    void setUpEnterpriseContext() {
        // P0 修复后：所有 t_user 读写都要求企业上下文（fail-closed），无上下文会抛异常
        EnterpriseContextHolder.set(ENTERPRISE_ID);
    }

    @AfterEach
    void clearEnterpriseContext() {
        EnterpriseContextHolder.clear();
    }

    private UserEntity stubEntity() {
        UserEntity e = new UserEntity();
        e.setId(1L);
        e.setUsername("admin");
        e.setRealName("管理员");
        e.setStatus("ACTIVE");
        e.setDeptId(1L);
        e.setEnterpriseId(ENTERPRISE_ID);
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
        when(userMapper.selectById(1L)).thenReturn(stubEntity());
        service.updateStatus(1L, "DISABLED");
        verify(userMapper).updateById(any(UserEntity.class));
    }

    /**
     * P0 负向断言：越权改别家企业用户必须抛错，且**不得发出 update**。
     *
     * <p>「不得发出 update」是关键 —— 只断言抛异常不够，
     * 若实现是先 update 再校验，数据已被改坏（AGENTS §4.3 第 10 条静默失败形态）。
     */
    @Test
    void updateStatus_别家企业用户_抛异常且不发出update() {
        UserEntity other = stubEntity();
        other.setEnterpriseId(999L);
        when(userMapper.selectById(1L)).thenReturn(other);

        assertThrows(BusinessException.class, () -> service.updateStatus(1L, "INACTIVE"));
        verify(userMapper, never()).updateById(any(UserEntity.class));
    }

    /** P0 负向断言：无企业上下文时 fail-closed，而不是返回全量用户 */
    @Test
    void pageUser_无企业上下文_抛异常而非返回全表() {
        EnterpriseContextHolder.clear();
        assertThrows(BusinessException.class, () -> service.pageUser(1, 20, null, null, null));
        verify(userMapper, never()).selectPage(any(Page.class), any());
    }

    @Test
    void resetPassword_调updateById() {
        when(userMapper.selectById(1L)).thenReturn(stubEntity());
        when(passwordEncoder.encode(any())).thenReturn("encoded");
        service.resetPassword(1L, "newpass123");
        verify(userMapper).updateById(any(UserEntity.class));
    }
}
