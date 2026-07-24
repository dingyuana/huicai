package com.huicai.agency.user.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.agency.user.dto.AgencyUserCreateDTO;
import com.huicai.agency.user.dto.AgencyUserVO;
import com.huicai.agency.user.entity.AgencyUserEntity;
import com.huicai.agency.user.mapper.AgencyUserEnterpriseMapper;
import com.huicai.agency.user.mapper.AgencyUserMapper;
import com.huicai.base.system.entity.UserEntity;
import com.huicai.base.system.mapper.UserMapper;
import com.huicai.common.exception.BusinessException;
import com.huicai.config.security.LoginUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * AgencyUserService 单元测试 — 代理内角色管理
 */
@ExtendWith(MockitoExtension.class)
class AgencyUserServiceTest {

    @Mock
    private AgencyUserMapper agencyUserMapper;
    @Mock
    private AgencyUserEnterpriseMapper agencyUserEnterpriseMapper;
    @Mock
    private UserMapper userMapper;
    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AgencyUserServiceImpl service;

    private void setSecurityContext(String userType, String agencyRole) {
        UserEntity user = new UserEntity();
        user.setId(1L);
        user.setUsername("admin");
        user.setPassword("pass");
        user.setUserType(userType);
        user.setAgencyRole(agencyRole);
        LoginUser loginUser = new LoginUser(user, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities()));
    }

    @BeforeEach
    void setUp() {
        setSecurityContext("AGENCY", "AGENCY_ADMIN");
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ==================== create ====================

    @Test
    @DisplayName("场景9: 创建会计用户成功")
    void testCreateAccountant() {
        when(userMapper.selectByUsername("accountant01")).thenReturn(null);
        when(passwordEncoder.encode("pass123")).thenReturn("encoded");
        when(userMapper.insert(any(UserEntity.class))).thenAnswer(inv -> {
            UserEntity u = inv.getArgument(0);
            u.setId(10L);
            return 1;
        });
        when(agencyUserMapper.insert(any(AgencyUserEntity.class))).thenAnswer(inv -> {
            AgencyUserEntity e = inv.getArgument(0);
            e.setId(20L);
            return 1;
        });

        AgencyUserCreateDTO dto = new AgencyUserCreateDTO();
        dto.setUsername("accountant01");
        dto.setPassword("pass123");
        dto.setRealName("张会计");
        dto.setAgencyRole("ACCOUNTANT");
        dto.setAgencyId(1L);

        AgencyUserVO result = service.create(dto);

        assertNotNull(result);
        assertEquals("accountant01", result.getUsername());
        assertEquals("张会计", result.getRealName());
        assertEquals("ACCOUNTANT", result.getAgencyRole());
        assertEquals("ACTIVE", result.getStatus());
        assertEquals(0, result.getEnterpriseCount());
        assertEquals(1L, result.getAgencyId());

        // 验证 t_user 创建
        ArgumentCaptor<UserEntity> userCaptor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userMapper).insert(userCaptor.capture());
        assertEquals("AGENCY", userCaptor.getValue().getUserType());
        assertEquals("ACCOUNTANT", userCaptor.getValue().getAgencyRole());

        // 验证 t_agency_user 创建
        ArgumentCaptor<AgencyUserEntity> auCaptor = ArgumentCaptor.forClass(AgencyUserEntity.class);
        verify(agencyUserMapper).insert(auCaptor.capture());
        assertEquals("ACCOUNTANT", auCaptor.getValue().getAgencyRole());
        assertEquals("ACTIVE", auCaptor.getValue().getStatus());
    }

    @Test
    @DisplayName("场景14: 非管理员无权创建代理用户")
    void testNonAdminCannotCreate() {
        setSecurityContext("AGENCY", "ACCOUNTANT");

        AgencyUserCreateDTO dto = new AgencyUserCreateDTO();
        dto.setUsername("test");
        dto.setPassword("pass");
        dto.setRealName("Test");
        dto.setAgencyRole("ACCOUNTANT");
        dto.setAgencyId(1L);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(dto));
        assertEquals(403, ex.getCode());
        assertTrue(ex.getMessage().contains("无权管理代理用户"));
        verify(userMapper, never()).insert(Mockito.<UserEntity>any());
    }

    @Test
    @DisplayName("创建用户_用户名重复")
    void testCreateDuplicateUsername() {
        UserEntity existing = new UserEntity();
        existing.setUsername("accountant01");
        when(userMapper.selectByUsername("accountant01")).thenReturn(existing);

        AgencyUserCreateDTO dto = new AgencyUserCreateDTO();
        dto.setUsername("accountant01");
        dto.setPassword("pass");
        dto.setRealName("Test");
        dto.setAgencyRole("ACCOUNTANT");
        dto.setAgencyId(1L);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(dto));
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("用户名已存在"));
    }

    // ==================== suspend ====================

    @Test
    @DisplayName("场景16: 暂停活跃用户")
    void testSuspendUser() {
        AgencyUserEntity entity = new AgencyUserEntity();
        entity.setId(1L);
        entity.setUserId(10L);
        entity.setStatus("ACTIVE");
        entity.setDeleted(0);
        when(agencyUserMapper.selectById(1L)).thenReturn(entity);
        when(agencyUserMapper.updateById(any(AgencyUserEntity.class))).thenReturn(1);

        UserEntity user = new UserEntity();
        user.setId(10L);
        user.setStatus("ACTIVE");
        when(userMapper.selectById(10L)).thenReturn(user);
        when(userMapper.updateById(any(UserEntity.class))).thenReturn(1);

        assertDoesNotThrow(() -> service.suspend(1L));

        verify(agencyUserMapper).updateById(entity);
        assertEquals("SUSPENDED", entity.getStatus());
        assertEquals("INACTIVE", user.getStatus());
    }

    @Test
    @DisplayName("暂停非活跃用户失败")
    void testSuspendNonActiveFails() {
        AgencyUserEntity entity = new AgencyUserEntity();
        entity.setId(1L);
        entity.setStatus("SUSPENDED");
        entity.setDeleted(0);
        when(agencyUserMapper.selectById(1L)).thenReturn(entity);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.suspend(1L));
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("只有活跃状态的用户才能暂停"));
    }

    @Test
    @DisplayName("暂停不存在用户失败")
    void testSuspendNotFound() {
        when(agencyUserMapper.selectById(99L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.suspend(99L));
        assertEquals(404, ex.getCode());
    }

    @Test
    @DisplayName("非管理员无权暂停用户")
    void testNonAdminCannotSuspend() {
        setSecurityContext("AGENCY", "ACCOUNTANT");

        BusinessException ex = assertThrows(BusinessException.class, () -> service.suspend(1L));
        assertEquals(403, ex.getCode());
        assertTrue(ex.getMessage().contains("无权管理代理用户"));
    }

    // ==================== reactivate ====================

    @Test
    @DisplayName("场景16: 恢复暂停用户")
    void testReactivateUser() {
        AgencyUserEntity entity = new AgencyUserEntity();
        entity.setId(1L);
        entity.setUserId(10L);
        entity.setStatus("SUSPENDED");
        entity.setDeleted(0);
        when(agencyUserMapper.selectById(1L)).thenReturn(entity);
        when(agencyUserMapper.updateById(any(AgencyUserEntity.class))).thenReturn(1);

        UserEntity user = new UserEntity();
        user.setId(10L);
        user.setStatus("INACTIVE");
        when(userMapper.selectById(10L)).thenReturn(user);
        when(userMapper.updateById(any(UserEntity.class))).thenReturn(1);

        assertDoesNotThrow(() -> service.reactivate(1L));

        assertEquals("ACTIVE", entity.getStatus());
        assertEquals("ACTIVE", user.getStatus());
    }

    @Test
    @DisplayName("恢复非暂停用户失败")
    void testReactivateNonSuspendedFails() {
        AgencyUserEntity entity = new AgencyUserEntity();
        entity.setId(1L);
        entity.setStatus("ACTIVE");
        entity.setDeleted(0);
        when(agencyUserMapper.selectById(1L)).thenReturn(entity);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.reactivate(1L));
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("只有暂停状态的用户才能恢复"));
    }

    // ==================== terminate ====================

    @Test
    @DisplayName("场景16: 终止暂停用户")
    void testTerminateUser() {
        AgencyUserEntity entity = new AgencyUserEntity();
        entity.setId(1L);
        entity.setUserId(10L);
        entity.setStatus("SUSPENDED");
        entity.setDeleted(0);
        when(agencyUserMapper.selectById(1L)).thenReturn(entity);
        when(agencyUserMapper.updateById(any(AgencyUserEntity.class))).thenReturn(1);

        assertDoesNotThrow(() -> service.terminate(1L));

        assertEquals("TERMINATED", entity.getStatus());
        // terminate 不更新 t_user
        verify(userMapper, never()).updateById(any(UserEntity.class));
    }

    @Test
    @DisplayName("终止非暂停用户失败")
    void testTerminateNonSuspendedFails() {
        AgencyUserEntity entity = new AgencyUserEntity();
        entity.setId(1L);
        entity.setStatus("ACTIVE");
        entity.setDeleted(0);
        when(agencyUserMapper.selectById(1L)).thenReturn(entity);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.terminate(1L));
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("只有暂停状态的用户才能终止"));
    }

    // ==================== getById ====================

    @Test
    @DisplayName("获取代理用户详情")
    void testGetById() {
        AgencyUserEntity entity = new AgencyUserEntity();
        entity.setId(1L);
        entity.setAgencyId(1L);
        entity.setUserId(10L);
        entity.setAgencyRole("ACCOUNTANT");
        entity.setStatus("ACTIVE");
        entity.setDeleted(0);
        when(agencyUserMapper.selectById(1L)).thenReturn(entity);

        UserEntity user = new UserEntity();
        user.setUsername("accountant01");
        user.setRealName("张会计");
        when(userMapper.selectById(10L)).thenReturn(user);
        when(agencyUserEnterpriseMapper.getEnterpriseIdsByAgencyUserId(1L)).thenReturn(List.of(1L, 2L));

        AgencyUserVO result = service.getById(1L);

        assertNotNull(result);
        assertEquals("accountant01", result.getUsername());
        assertEquals("张会计", result.getRealName());
        assertEquals("ACCOUNTANT", result.getAgencyRole());
        assertEquals(2, result.getEnterpriseCount());
    }

    @Test
    @DisplayName("获取已删除代理用户返回404")
    void testGetByIdDeleted() {
        AgencyUserEntity entity = new AgencyUserEntity();
        entity.setDeleted(1);
        when(agencyUserMapper.selectById(1L)).thenReturn(entity);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.getById(1L));
        assertEquals(404, ex.getCode());
    }

    // ==================== 状态机全生命周期 ====================

    @Test
    @DisplayName("场景16: 状态机全生命周期 ACTIVE→SUSPENDED→TERMINATED")
    void testFullLifecycle() {
        // Phase 1: ACTIVE → SUSPENDED
        AgencyUserEntity entity = new AgencyUserEntity();
        entity.setId(1L);
        entity.setUserId(10L);
        entity.setStatus("ACTIVE");
        entity.setDeleted(0);
        when(agencyUserMapper.selectById(1L)).thenReturn(entity);
        when(agencyUserMapper.updateById(any(AgencyUserEntity.class))).thenReturn(1);

        UserEntity user = new UserEntity();
        user.setId(10L);
        user.setStatus("ACTIVE");
        when(userMapper.selectById(10L)).thenReturn(user);
        when(userMapper.updateById(any(UserEntity.class))).thenReturn(1);

        service.suspend(1L);
        assertEquals("SUSPENDED", entity.getStatus());

        // Phase 2: SUSPENDED → TERMINATED
        service.terminate(1L);
        assertEquals("TERMINATED", entity.getStatus());

        // 负向断言: 不能从 TERMINATED 再操作
        BusinessException ex1 = assertThrows(BusinessException.class, () -> service.suspend(1L));
        assertTrue(ex1.getMessage().contains("只有活跃状态的用户才能暂停"));

        BusinessException ex2 = assertThrows(BusinessException.class, () -> service.reactivate(1L));
        assertTrue(ex2.getMessage().contains("只有暂停状态的用户才能恢复"));

        BusinessException ex3 = assertThrows(BusinessException.class, () -> service.terminate(1L));
        assertTrue(ex3.getMessage().contains("只有暂停状态的用户才能终止"));
    }

    // ==================== page ====================

    @Test
    @DisplayName("分页查询代理用户列表")
    void testPage() {
        AgencyUserEntity entity = new AgencyUserEntity();
        entity.setId(1L);
        entity.setAgencyId(1L);
        entity.setUserId(10L);
        entity.setAgencyRole("ACCOUNTANT");
        entity.setStatus("ACTIVE");

        com.baomidou.mybatisplus.extension.plugins.pagination.Page<AgencyUserEntity> mockPage =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 10);
        mockPage.setRecords(List.of(entity));
        mockPage.setTotal(1);
        when(agencyUserMapper.selectPage(any(), any())).thenReturn(mockPage);

        UserEntity user = new UserEntity();
        user.setUsername("accountant01");
        user.setRealName("张会计");
        when(userMapper.selectById(10L)).thenReturn(user);
        when(agencyUserEnterpriseMapper.getEnterpriseIdsByAgencyUserId(1L)).thenReturn(List.of());

        IPage<AgencyUserVO> result = service.page(1, 10, 1L);

        assertEquals(1, result.getTotal());
        assertEquals("accountant01", result.getRecords().get(0).getUsername());
    }
}
