package com.huicai.base.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huicai.common.context.EnterpriseContextHolder;
import com.huicai.common.exception.BusinessException;
import com.huicai.base.system.entity.UserEntity;
import com.huicai.base.system.entity.UserRoleEntity;
import com.huicai.base.system.mapper.DeptMapper;
import com.huicai.base.system.mapper.UserMapper;
import com.huicai.base.system.mapper.UserRoleMapper;
import com.huicai.base.system.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
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
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final DeptMapper deptMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public IPage<UserEntity> pageUser(long page, long size, String keyword, Long deptId, String status) {
        // P0（2026-10-07 实测修复）：t_user 在 SHARED_TABLES 内，第二层防线不注入企业条件；
        // 而本方法原 wrapper 只有 deleted/keyword/deptId/status ⇒ **跨企业返回全部用户**
        // （实测企业 990007 能看到企业 1 的用户名/真实姓名/邮箱/手机号）。
        // 故企业维度必须由应用层显式补上，且**无上下文时 fail-closed**。
        Long ctx = requireEnterpriseContext("查询用户列表");
        LambdaQueryWrapper<UserEntity> wrapper = new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getDeleted, 0)
                .eq(UserEntity::getEnterpriseId, ctx);

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
        // P0：原实现直接回显别家企业用户的 PII（可按猜测 id 遍历）。此处按当前企业做归属校验。
        assertOwnedByCurrentEnterprise(user);
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
        // P0：UserSaveDTO 刻意不含 enterpriseId，且 UserEntity **不继承 BaseEntity**
        // ⇒ insertFill 压根不触发 ⇒ 新用户 enterprise_id 会是 NULL。
        // 后果有二：①列表按企业过滤后这些用户**永远不可见**（自己建的管理员看不到自己建的人）；
        // ②它们成为「不属于任何企业」的游离数据（同预付款 1a-1 的静默错位形态）。
        // 故这里显式落当前企业，且不接受客户端指定。
        user.setEnterpriseId(requireEnterpriseContext("创建用户"));
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
        // P0：原实现可带着别家企业的 id 直接改其资料与角色
        assertOwnedByCurrentEnterprise(userMapper.selectById(user.getId()));
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
        assertOwnedByCurrentEnterprise(userMapper.selectById(id));
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setStatus(status);
        userMapper.updateById(user);
    }

    @Override
    public void resetPassword(Long id, String newPassword) {
        assertOwnedByCurrentEnterprise(userMapper.selectById(id));
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setPassword(passwordEncoder.encode(newPassword));
        userMapper.updateById(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignRoles(Long userId, List<Long> roleIds) {
        assertOwnedByCurrentEnterprise(userMapper.selectById(userId));
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
        assertOwnedByCurrentEnterprise(userMapper.selectById(id));
        userMapper.update(null, new LambdaUpdateWrapper<UserEntity>()
                .set(UserEntity::getDeleted, 1)
                .eq(UserEntity::getId, id));
    }

    /**
     * 取当前企业上下文；缺失即抛错（fail-closed）。
     *
     * <p>为什么必须 fail-closed 而不是「无上下文就不加条件」：
     * 那会让「忘记设置上下文」退化成**返回全量用户**，是本缺陷最危险的一档。
     */
    private Long requireEnterpriseContext(String scene) {
        Long ctx = EnterpriseContextHolder.get();
        if (ctx == null) {
            throw new BusinessException("无当前企业上下文，无法" + scene);
        }
        return ctx;
    }

    /**
     * 断言该用户归属当前企业，否则拒绝。
     *
     * <p><b>为什么必须先查再断言</b>：越权写入若只「不抛异常」而静默跳过，
     * 调用方会收到成功回执却什么都没改 —— 比直接报错更难排查
     * （同 AGENTS §4.3 第 10 条「{@code if (doc != null)} 包住回滚逻辑 = 静默失败」）。
     *
     * <p><b>为什么不用「过滤掉」而要「抛异常」</b>：按 id 访问时，
     * 「查不到」与「无权限」对调用方是同一结果，容易被上层当成不存在而重试或降级；
     * 显式抛出才能让越权尝试在审计日志里留痕。
     */
    private void assertOwnedByCurrentEnterprise(UserEntity user) {
        Long ctx = requireEnterpriseContext("操作用户");
        if (user == null) {
            throw new BusinessException("用户不存在或不属于当前企业");
        }
        if (!ctx.equals(user.getEnterpriseId())) {
            throw new BusinessException("无权访问其他企业的用户");
        }
    }
}
