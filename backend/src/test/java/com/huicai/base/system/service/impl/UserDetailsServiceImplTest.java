package com.huicai.base.system.service.impl;

import com.huicai.base.system.entity.MenuEntity;
import com.huicai.base.system.entity.UserEntity;
import com.huicai.base.system.mapper.MenuMapper;
import com.huicai.base.system.mapper.RoleMenuMapper;
import com.huicai.base.system.mapper.UserMapper;
import com.huicai.base.system.mapper.UserRoleMapper;
import com.huicai.config.security.LoginUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserDetailsServiceImplTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private UserRoleMapper userRoleMapper;
    @Mock
    private RoleMenuMapper roleMenuMapper;
    @Mock
    private MenuMapper menuMapper;

    @InjectMocks
    private UserDetailsServiceImpl userDetailsService;

    private UserEntity createUser(Long id, String username, String status) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setUsername(username);
        user.setPassword("encodedPassword");
        user.setStatus(status);
        user.setDeleted(0);
        return user;
    }

    private MenuEntity createMenu(Long id, String permissionCode) {
        MenuEntity menu = new MenuEntity();
        menu.setId(id);
        menu.setPermissionCode(permissionCode);
        return menu;
    }

    @Test
    @DisplayName("正常加载用户，返回 LoginUser 包含正确的用户名和权限")
    void loadUserByUsername_Success_NoRoles() {
        // given
        String username = "testuser";
        UserEntity user = createUser(1L, username, "ACTIVE");
        when(userMapper.selectOne(any())).thenReturn(user);
        when(userRoleMapper.getRoleIdsByUserId(1L)).thenReturn(Collections.emptyList());

        // when
        LoginUser loginUser = (LoginUser) userDetailsService.loadUserByUsername(username);

        // then
        assertNotNull(loginUser);
        assertEquals(username, loginUser.getUsername());
        assertTrue(loginUser.getAuthorities().isEmpty());
        verify(userMapper).selectOne(any());
        verify(userRoleMapper, times(2)).getRoleIdsByUserId(1L);
    }

    @Test
    @DisplayName("用户不存在抛 UsernameNotFoundException")
    void loadUserByUsername_UserNotFound() {
        // given
        String username = "nonexistent";
        when(userMapper.selectOne(any())).thenReturn(null);

        // when & then
        UsernameNotFoundException exception = assertThrows(UsernameNotFoundException.class,
                () -> userDetailsService.loadUserByUsername(username));
        assertEquals("用户不存在: " + username, exception.getMessage());
        verify(userMapper).selectOne(any());
        verifyNoInteractions(userRoleMapper, roleMenuMapper, menuMapper);
    }

    @Test
    @DisplayName("用户状态 INACTIVE 抛 UsernameNotFoundException")
    void loadUserByUsername_UserInactive() {
        // given
        String username = "inactiveuser";
        UserEntity user = createUser(2L, username, "INACTIVE");
        when(userMapper.selectOne(any())).thenReturn(user);

        // when & then
        UsernameNotFoundException exception = assertThrows(UsernameNotFoundException.class,
                () -> userDetailsService.loadUserByUsername(username));
        assertEquals("用户已被停用", exception.getMessage());
        verify(userMapper).selectOne(any());
        verifyNoInteractions(userRoleMapper, roleMenuMapper, menuMapper);
    }

    @Test
    @DisplayName("用户状态 LOCKED 抛 UsernameNotFoundException")
    void loadUserByUsername_UserLocked() {
        // given
        String username = "lockeduser";
        UserEntity user = createUser(3L, username, "LOCKED");
        when(userMapper.selectOne(any())).thenReturn(user);

        // when & then
        UsernameNotFoundException exception = assertThrows(UsernameNotFoundException.class,
                () -> userDetailsService.loadUserByUsername(username));
        assertEquals("用户已被锁定", exception.getMessage());
        verify(userMapper).selectOne(any());
        verifyNoInteractions(userRoleMapper, roleMenuMapper, menuMapper);
    }

    @Test
    @DisplayName("用户有角色和权限，返回正确的 authorities 列表")
    void loadUserByUsername_WithRolesAndPermissions() {
        // given
        String username = "admin";
        UserEntity user = createUser(10L, username, "ACTIVE");
        List<Long> roleIds = Arrays.asList(100L, 200L);

        MenuEntity menu1 = createMenu(1L, "system:user:list");
        MenuEntity menu2 = createMenu(2L, "system:user:create");
        MenuEntity menu3 = createMenu(3L, "system:role:list");

        when(userMapper.selectOne(any())).thenReturn(user);
        when(userRoleMapper.getRoleIdsByUserId(10L)).thenReturn(roleIds);
        when(roleMenuMapper.getMenuIdsByRoleId(100L)).thenReturn(Arrays.asList(1L, 2L));
        when(roleMenuMapper.getMenuIdsByRoleId(200L)).thenReturn(Arrays.asList(3L));
        when(menuMapper.selectBatchIds(Arrays.asList(1L, 2L))).thenReturn(Arrays.asList(menu1, menu2));
        when(menuMapper.selectBatchIds(Arrays.asList(3L))).thenReturn(Arrays.asList(menu3));

        // when
        LoginUser loginUser = (LoginUser) userDetailsService.loadUserByUsername(username);

        // then
        assertNotNull(loginUser);
        assertEquals(username, loginUser.getUsername());
        assertEquals(3, loginUser.getAuthorities().size());
        assertTrue(loginUser.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("system:user:list")));
        assertTrue(loginUser.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("system:user:create")));
        assertTrue(loginUser.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("system:role:list")));

        verify(userMapper).selectOne(any());
        verify(userRoleMapper, times(2)).getRoleIdsByUserId(10L);
        verify(roleMenuMapper).getMenuIdsByRoleId(100L);
        verify(roleMenuMapper).getMenuIdsByRoleId(200L);
        verify(menuMapper).selectBatchIds(Arrays.asList(1L, 2L));
        verify(menuMapper).selectBatchIds(Arrays.asList(3L));
    }
}