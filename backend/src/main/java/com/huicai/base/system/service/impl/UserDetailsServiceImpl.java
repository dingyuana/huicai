package com.huicai.base.system.service.impl;

import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huicai.base.system.entity.UserEntity;
import com.huicai.base.system.entity.RoleEntity;
import com.huicai.base.system.entity.MenuEntity;
import com.huicai.base.system.mapper.UserMapper;
import com.huicai.base.system.mapper.UserRoleMapper;
import com.huicai.base.system.mapper.RoleMenuMapper;
import com.huicai.base.system.mapper.MenuMapper;
import lombok.RequiredArgsConstructor;
import com.huicai.config.security.LoginUser;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
/**
 * 类级事务：P102/M5b —— 走租户表的读写路径必须有事务，否则切面不触发、
 * app.enterprise_id 设不进去，非超级用户下 RLS 会把本企业数据也过滤掉（读 0 行）。
 */
@Transactional
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final RoleMenuMapper roleMenuMapper;
    private final MenuMapper menuMapper;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserEntity userEntity = userMapper.selectOne(
                new LambdaQueryWrapper<UserEntity>()
                        .eq(UserEntity::getUsername, username)
                        .eq(UserEntity::getDeleted, 0));

        if (userEntity == null) {
            throw new UsernameNotFoundException("用户不存在: " + username);
        }

        if ("INACTIVE".equals(userEntity.getStatus())) {
            throw new UsernameNotFoundException("用户已被停用");
        }

        if ("LOCKED".equals(userEntity.getStatus())) {
            throw new UsernameNotFoundException("用户已被锁定");
        }

        // Get user roles
        List<Long> roleIds = userRoleMapper.getRoleIdsByUserId(userEntity.getId());
        List<String> roles = new ArrayList<>();

        // Get permissions from all roles
        List<String> permissions = new ArrayList<>();
        for (Long roleId : roleIds) {
            List<Long> menuIds = roleMenuMapper.getMenuIdsByRoleId(roleId);
            if (menuIds != null && !menuIds.isEmpty()) {
                List<MenuEntity> menus = menuMapper.selectBatchIds(menuIds);
                for (MenuEntity menu : menus) {
                    if (menu.getPermissionCode() != null && !menu.getPermissionCode().isEmpty()) {
                        permissions.add(menu.getPermissionCode());
                    }
                }
            }
        }

        // Add role codes as authorities (ROLE_ prefix)
        if (roleIds != null) {
            roles = userRoleMapper.getRoleIdsByUserId(userEntity.getId())
                    .stream()
                    .map(String::valueOf)
                    .collect(Collectors.toList());
        }

        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        // Add permission codes as authorities
        for (String perm : permissions) {
            authorities.add(new SimpleGrantedAuthority(perm));
        }

        return new LoginUser(userEntity, authorities);
    }
}
