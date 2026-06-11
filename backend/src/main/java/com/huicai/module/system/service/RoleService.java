package com.huicai.module.system.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.module.system.entity.RoleEntity;

import java.util.List;

public interface RoleService {
    IPage<RoleEntity> pageRole(long page, long size, String keyword, String status);
    RoleEntity getById(Long id);
    void create(RoleEntity role);
    void update(RoleEntity role);
    void updateStatus(Long id, String status);
    List<Long> getRoleMenuIds(Long roleId);
    void assignMenus(Long roleId, List<Long> menuIds);
    void delete(Long id);
    List<RoleEntity> listAll();
}
