package com.huicai.base.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huicai.common.exception.BusinessException;
import com.huicai.base.system.entity.RoleEntity;
import com.huicai.base.system.entity.RoleMenuEntity;
import com.huicai.base.system.entity.UserRoleEntity;
import com.huicai.base.system.mapper.RoleMapper;
import com.huicai.base.system.mapper.RoleMenuMapper;
import com.huicai.base.system.mapper.UserRoleMapper;
import com.huicai.base.system.service.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {

    private final RoleMapper roleMapper;
    private final RoleMenuMapper roleMenuMapper;
    private final UserRoleMapper userRoleMapper;

    @Override
    public IPage<RoleEntity> pageRole(long page, long size, String keyword, String status) {
        LambdaQueryWrapper<RoleEntity> wrapper = new LambdaQueryWrapper<RoleEntity>()
                .eq(RoleEntity::getDeleted, 0);

        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(RoleEntity::getName, keyword)
                    .or().like(RoleEntity::getCode, keyword));
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(RoleEntity::getStatus, status);
        }
        wrapper.orderByAsc(RoleEntity::getSortOrder);

        return roleMapper.selectPage(new Page<>(page, size), wrapper);
    }

    @Override
    public RoleEntity getById(Long id) {
        return roleMapper.selectById(id);
    }

    @Override
    public void create(RoleEntity role) {
        RoleEntity existing = roleMapper.selectOne(
                new LambdaQueryWrapper<RoleEntity>()
                        .eq(RoleEntity::getCode, role.getCode())
                        .eq(RoleEntity::getDeleted, 0));
        if (existing != null) {
            throw new BusinessException("角色编码已存在");
        }
        roleMapper.insert(role);
    }

    @Override
    public void update(RoleEntity role) {
        roleMapper.updateById(role);
    }

    @Override
    public void updateStatus(Long id, String status) {
        RoleEntity role = new RoleEntity();
        role.setId(id);
        role.setStatus(status);
        roleMapper.updateById(role);
    }

    @Override
    public List<Long> getRoleMenuIds(Long roleId) {
        return roleMenuMapper.getMenuIdsByRoleId(roleId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignMenus(Long roleId, List<Long> menuIds) {
        roleMenuMapper.delete(new LambdaQueryWrapper<RoleMenuEntity>().eq(RoleMenuEntity::getRoleId, roleId));
        for (Long menuId : menuIds) {
            RoleMenuEntity rm = new RoleMenuEntity();
            rm.setRoleId(roleId);
            rm.setMenuId(menuId);
            roleMenuMapper.insert(rm);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Long userCount = userRoleMapper.selectCount(
                new LambdaQueryWrapper<UserRoleEntity>().eq(UserRoleEntity::getRoleId, id));
        if (userCount > 0) {
            throw new BusinessException("该角色下还有用户，无法删除");
        }
        roleMapper.deleteById(id);
    }

    @Override
    public List<RoleEntity> listAll() {
        return roleMapper.selectList(
                new LambdaQueryWrapper<RoleEntity>()
                        .eq(RoleEntity::getDeleted, 0)
                        .eq(RoleEntity::getStatus, "active")
                        .orderByAsc(RoleEntity::getSortOrder));
    }
}
