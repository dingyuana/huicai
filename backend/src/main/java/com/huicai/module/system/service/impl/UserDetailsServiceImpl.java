package com.huicai.module.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huicai.module.system.entity.UserEntity;
import com.huicai.module.system.entity.RoleEntity;
import com.huicai.module.system.entity.MenuEntity;
import com.huicai.module.system.mapper.UserMapper;
import com.huicai.module.system.mapper.UserRoleMapper;
import com.huicai.module.system.mapper.RoleMenuMapper;
import com.huicai.module.system.mapper.MenuMapper;
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

        if ("inactive".equals(userEntity.getStatus())) {
            throw new UsernameNotFoundException("用户已被停用");
        }

        if ("locked".equals(userEntity.getStatus())) {
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
