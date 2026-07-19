package com.huicai.base.auth.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.base.auth.entity.UserEntity;

import java.util.List;

public interface UserService {
    IPage<UserEntity> pageUser(long page, long size, String keyword, Long deptId, String status);
    UserEntity getById(Long id);
    UserEntity getByUsername(String username);
    void create(UserEntity user);
    void update(UserEntity user);
    void updateStatus(Long id, String status);
    void resetPassword(Long id, String newPassword);
    void assignRoles(Long userId, List<Long> roleIds);
    void delete(Long id);
}
