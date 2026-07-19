package com.huicai.base.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huicai.common.exception.BusinessException;
import com.huicai.base.auth.entity.UserEntity;
import com.huicai.base.auth.entity.UserRoleEntity;
import com.huicai.base.auth.mapper.DeptMapper;
import com.huicai.base.auth.mapper.UserMapper;
import com.huicai.base.auth.mapper.UserRoleMapper;
import com.huicai.base.auth.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final DeptMapper deptMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public IPage<UserEntity> pageUser(long page, long size, String keyword, Long deptId, String status) {
        LambdaQueryWrapper<UserEntity> wrapper = new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getDeleted, 0);

        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(UserEntity::getUsername, keyword)
                    .or().like(UserEntity::getRealName, keyword)
                    .or().like(UserEntity::getPhone, keyword));
        }
        if (deptId != null) {
            wrapper.eq(UserEntity::getDeptId, deptId);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(UserEntity::getStatus, status);
        }
        wrapper.orderByAsc(UserEntity::getCreatedAt);

        IPage<UserEntity> pageResult = userMapper.selectPage(new Page<>(page, size), wrapper);

        // Load dept names and role ids
        for (UserEntity user : pageResult.getRecords()) {
            if (user.getDeptId() != null) {
                var dept = deptMapper.selectById(user.getDeptId());
                if (dept != null) {
                    user.setDeptName(dept.getName());
                }
            }
            user.setRoleIds(userRoleMapper.getRoleIdsByUserId(user.getId()));
        }

        return pageResult;
    }

    @Override
    public UserEntity getById(Long id) {
        UserEntity user = userMapper.selectById(id);
        if (user != null) {
            user.setRoleIds(userRoleMapper.getRoleIdsByUserId(id));
            if (user.getDeptId() != null) {
                var dept = deptMapper.selectById(user.getDeptId());
                if (dept != null) {
                    user.setDeptName(dept.getName());
                }
            }
        }
        return user;
    }

    @Override
    public UserEntity getByUsername(String username) {
        return userMapper.selectByUsername(username);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(UserEntity user) {
        if (userMapper.selectByUsername(user.getUsername()) != null) {
            throw new BusinessException("用户名已存在");
        }
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        userMapper.insert(user);

        if (user.getRoleIds() != null && !user.getRoleIds().isEmpty()) {
            for (Long roleId : user.getRoleIds()) {
                UserRoleEntity ur = new UserRoleEntity();
                ur.setUserId(user.getId());
                ur.setRoleId(roleId);
                userRoleMapper.insert(ur);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(UserEntity user) {
        user.setPassword(null); // Don't update password through normal update
        userMapper.updateById(user);

        // Update roles
        if (user.getRoleIds() != null) {
            userRoleMapper.delete(new LambdaQueryWrapper<UserRoleEntity>().eq(UserRoleEntity::getUserId, user.getId()));
            for (Long roleId : user.getRoleIds()) {
                UserRoleEntity ur = new UserRoleEntity();
                ur.setUserId(user.getId());
                ur.setRoleId(roleId);
                userRoleMapper.insert(ur);
            }
        }
    }

    @Override
    public void updateStatus(Long id, String status) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setStatus(status);
        userMapper.updateById(user);
    }

    @Override
    public void resetPassword(Long id, String newPassword) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setPassword(passwordEncoder.encode(newPassword));
        userMapper.updateById(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignRoles(Long userId, List<Long> roleIds) {
        userRoleMapper.delete(new LambdaQueryWrapper<UserRoleEntity>().eq(UserRoleEntity::getUserId, userId));
        for (Long roleId : roleIds) {
            UserRoleEntity ur = new UserRoleEntity();
            ur.setUserId(userId);
            ur.setRoleId(roleId);
            userRoleMapper.insert(ur);
        }
    }

    @Override
    public void delete(Long id) {
        userMapper.update(null, new LambdaUpdateWrapper<UserEntity>()
                .set(UserEntity::getDeleted, 1)
                .eq(UserEntity::getId, id));
    }
}
