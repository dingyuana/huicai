package com.huicai.agency.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huicai.agency.tenant.mapper.AgencyEnterpriseMapper;
import com.huicai.agency.user.entity.AgencyUserEntity;
import com.huicai.agency.user.mapper.AgencyUserEnterpriseMapper;
import com.huicai.agency.user.mapper.AgencyUserMapper;
import com.huicai.base.system.util.SecurityUtils;
import com.huicai.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * SPEC-P120：代理端企业管理的角色守卫。
 *
 * <h2>为什么需要它</h2>
 * {@code EnterpriseController} 的 {@code switch} 端点早已按 {@code agencyRole}
 * 分流校验（经理/审核员走代理全量、会计/助理走派工关系），但<b>同控制器的其余
 * 9 个端点完全无角色校验</b> —— 会计登录后可新建/修改/删除/启停客户企业，
 * 并可 {@code page} 出本代理（或任意代理）的全部企业。
 *
 * <p>这是 §4.5 第 43 条「只解释一条路径就等于整表放行」的同型：
 * 上次（P114）修了 switch，漏了 list。
 *
 * <h2>职责划分</h2>
 * <ul>
 *   <li><b>管理类操作</b>（新建/修改/删除/绑定/解绑/启停）—— 仅 {@code AGENCY_ADMIN}；</li>
 *   <li><b>可见范围</b> —— {@code AGENCY_ADMIN}/{@code REVIEWER} 见本代理全部企业；
 *       {@code ACCOUNTANT}/{@code ASSISTANT} <b>仅见派给自己的</b>。</li>
 * </ul>
 *
 * <p>⚠️ 会计/助理<b>不</b>被禁止「看到本代理的企业」这一动作本身，但
 * {@link #resolveVisibleEnterpriseIds} 决定了他们<b>能拿到哪些 id</b>，
 * 从而使 {@code page} 无法通过自填 {@code agencyId} 越权取数。
 */
@Component
@RequiredArgsConstructor
public class AgencyEnterpriseRoleGuard {

    private final AgencyEnterpriseMapper agencyEnterpriseMapper;
    private final AgencyUserEnterpriseMapper agencyUserEnterpriseMapper;
    private final AgencyUserMapper agencyUserMapper;

    /** 仅代理经理可执行企业管理操作。 */
    public void requireAgencyAdmin(String operation) {
        String userType = SecurityUtils.getCurrentUserType();
        // ⚠️ SUPER_ADMIN 是系统运维身份，不属于代理内角色体系，
        //    不能因缺 agencyRole 而被误拦（实施初版即漏了这条，8 例转红）。
        if ("SUPER_ADMIN".equals(userType)) {
            return;
        }
        if (!"AGENCY".equals(userType)) {
            throw BusinessException.forbidden("无权执行该操作: " + operation);
        }
        if (!"AGENCY_ADMIN".equals(SecurityUtils.getCurrentAgencyRole())) {
            throw BusinessException.forbidden(
                    "仅代理经理可" + operation + "，当前角色无权");
        }
    }

    /**
     * 解析当前用户可见的企业 id 集合。
     *
     * <p>经理/审核员：本代理全部企业；会计/助理：<b>仅派工给本人的</b>。
     */
    public List<Long> resolveVisibleEnterpriseIds() {
        Long agencyId = SecurityUtils.getCurrentAgencyId();
        if (agencyId == null) {
            throw BusinessException.forbidden("无权访问企业列表");
        }
        String role = SecurityUtils.getCurrentAgencyRole();
        if ("AGENCY_ADMIN".equals(role) || "REVIEWER".equals(role)) {
            return agencyEnterpriseMapper.getEnterpriseIdsByAgencyId(agencyId);
        }
        // 会计 / 助理：按派工关系
        List<Long> assigned = assignedEnterpriseIdsOrEmpty();
        if (assigned.isEmpty() && !hasAgencyUserRecord()) {
            throw BusinessException.forbidden("无权访问企业列表");
        }
        return assigned;
    }

    /** 当前用户是否为代理成员（区别于「代理成员但无派工」——后者应返回空列表而非拒绝） */
    private boolean hasAgencyUserRecord() {
        try {
            return agencyUserMapper.selectCount(new LambdaQueryWrapper<AgencyUserEntity>()
                    .eq(AgencyUserEntity::getUserId, SecurityUtils.getCurrentUserId())
                    .eq(AgencyUserEntity::getDeleted, 0)) > 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 断言当前用户有权访问指定企业（读路径）。
     *
     * <p><b>按 userType 分流</b>（SPEC-P120 实施中两度修正）：
     * <ul>
     *   <li>{@code SUPER_ADMIN} —— 不受限（系统运维）；</li>
     *   <li>{@code ENTERPRISE}（企业自有用户）—— 可见集 =
     *       <b>归属企业 ∪ 其作为代理成员被派工的企业</b>；</li>
     *   <li>{@code AGENCY} —— 经理/审核员见本代理名下任意企业；
     *       会计/助理仅限派工给自己的。</li>
     * </ul>
     *
     * <p>⚠️ 两次踩坑记录（均为「守卫收得过紧」）：
     * <ol>
     *   <li>初版只写 AGENCY 分支 ⇒ {@code ENTERPRISE} 自有用户被误判无权
     *       （{@code TenantSwitchRealDBTest} 5 例 + {@code EnterpriseControllerTest} 3 例转红）；</li>
     *   <li>二版把 ENTERPRISE 写成「必须等于归属企业」⇒ 又挡住
     *       {@code memberSwitchRewritesContextAndIsolatesData}
     *       （该用户归属 A，但经代理派工被授权访问 B）。</li>
     * </ol>
     * <b>教训：守卫必须先答「谁会被放行」，再答「该拦谁」——
     * 只写拒斥分支，收紧时必然误伤合法路径。</b>
     */
    public void requireVisibleEnterprise(Long enterpriseId) {
        String userType = SecurityUtils.getCurrentUserType();
        if ("SUPER_ADMIN".equals(userType)) {
            return;
        }
        // AGENCY（代理内角色）：经理/审核员见本代理全部，会计/助理仅见派工
        if ("AGENCY".equals(userType)) {
            if (!resolveVisibleEnterpriseIds().contains(enterpriseId)) {
                throw BusinessException.forbidden("您无权访问该客户企业");
            }
            return;
        }
        // ENTERPRISE 及其它：可见集 = 归属企业 ∪ 作为代理成员被派工的企业
        List<Long> visible = new ArrayList<>();
        Long home = SecurityUtils.getCurrentEnterpriseId();
        if (home != null) {
            visible.add(home);
        }
        visible.addAll(assignedEnterpriseIdsOrEmpty());

        if (!visible.contains(enterpriseId)) {
            throw BusinessException.forbidden("无权访问其它企业的数据");
        }
    }

    /**
     * 取当前用户作为代理成员被派工的企业 id；非代理成员或查询失败时返回空列表。
     *
     * <p>⚠️ 刻意<b>不抛异常</b>：纯企业用户（{@code userType=ENTERPRISE} 且无代理成员记录）
     * 在 {@code t_agency_user} 查不到行是正常态，不该被当成「无权」。
     */
    private List<Long> assignedEnterpriseIdsOrEmpty() {
        try {
            AgencyUserEntity agencyUser = agencyUserMapper.selectOne(
                    new LambdaQueryWrapper<AgencyUserEntity>()
                            .eq(AgencyUserEntity::getUserId, SecurityUtils.getCurrentUserId())
                            .eq(AgencyUserEntity::getDeleted, 0));
            if (agencyUser == null) {
                return List.of();
            }
            List<Long> ids = agencyUserEnterpriseMapper
                    .getEnterpriseIdsByAgencyUserId(agencyUser.getId());
            return ids == null ? List.of() : ids;
        } catch (Exception e) {
            return List.of();
        }
    }
}