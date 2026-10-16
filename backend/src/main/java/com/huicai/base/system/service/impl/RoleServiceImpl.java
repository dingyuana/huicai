package com.huicai.base.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huicai.common.security.Ref;
import com.huicai.common.security.ReferenceGuard;
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
/**
 * 类级事务（P102/M5b，2026-10-03）：走租户表的路径必须有事务，否则
 * TenantRlsInitializer 切面不触发、app.enterprise_id 设不进去，
 * 应用以非超级用户连接时 RLS 会把本企业数据也过滤掉（读 0 行）。
 * 方法级 @Transactional 优先级更高，不受此影响。
 */
@Transactional
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
    @ReferenceGuard(
            parent = "t_role",
            label = "角色",
            children = {
                    @Ref(table = "t_role_menu", column = "role_id", label = "角色菜单授权"),
                    @Ref(table = "t_user_role", column = "role_id", label = "用户角色")
            })
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
        // ⚠️ status 字面量必须是**大写 ACTIVE**。
        //
        // 缺陷（2026-10-12 实测）：原写 `.eq(getStatus, "active")`（小写），
        // 而 t_role.status 实际存 `ACTIVE`（6 字符）⇒ SQL 恒不匹配 ⇒ 返回空列表。
        // 症状：用户管理页「角色」下拉框空白，且**前端不报错** ——
        // `getAllRoles()` 无 try/catch，空数组被当作合法结果静默渲染。
        //
        // ⚠️ 与 §4.2 第 14 条同型：同名 status 在不同表的 CHECK 允许集与大小写不同，
        //   禁止照抄别处字符串或凭语感猜。改前先查真实值：
        //   SELECT DISTINCT status, length(status) FROM t_role;  ⇒ ACTIVE / 6
        return roleMapper.selectList(
                new LambdaQueryWrapper<RoleEntity>()
                        .eq(RoleEntity::getDeleted, 0)
                        .eq(RoleEntity::getStatus, "ACTIVE")
                        .orderByAsc(RoleEntity::getSortOrder));
    }
}
