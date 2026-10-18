package com.huicai.agency.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huicai.agency.user.entity.AgencyUserEntity;
import com.huicai.agency.user.mapper.AgencyUserEnterpriseMapper;
import com.huicai.agency.user.mapper.AgencyUserMapper;
import com.huicai.base.system.util.SecurityUtils;
import com.huicai.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * SPEC-P116 —— 代理端「当前用户可见客户企业范围」的**唯一**解析入口。
 *
 * <h2>为什么必须是唯一入口</h2>
 * S-26 §1.1/§1.2 早就声明了四角色的可操作范围，但实现是**逐端点各写一遍、
 * 写漏了**：实测 7 个代理端读端点（{@code ServiceProgressController}×3、
 * {@code AgencySummaryController}×2、{@code ClientController}×2）<b>一个都没有收窄</b>
 * ⇒ {@code ACCOUNTANT} 今天能看到整个代理公司全部客户的账。
 *
 * <p>若本类仍按逐端点加过滤的方式补，下一个新端点照样漏。故所有代理端读路径
 * <b>必须</b>走这里，且新增端点不得绕过（由 SPEC-P116 的结构守卫断言）。
 *
 * <h2>分流规则（S-26 §1.1）</h2>
 * <table border="1">
 *   <tr><th>agency_role</th><th>可见范围</th><th>S-26 依据</th></tr>
 *   <tr><td>AGENCY_ADMIN</td><td>本代理全部客户</td><td>§1.1 「全部客户企业 + 管理会计」</td></tr>
 *   <tr><td>REVIEWER</td><td>本代理全部客户</td><td>§1.1 「全部企业（只读审核权限）」</td></tr>
 *   <tr><td>ACCOUNTANT</td><td><b>仅分配给自己的</b></td><td>§1.1 「仅分配给自己的客户企业」</td></tr>
 *   <tr><td>ASSISTANT</td><td><b>仅分配给自己的</b></td><td>§1.1 同 ACCOUNTANT</td></tr>
 *   <tr><td>SUPER_ADMIN</td><td>不限（跨代理）</td><td>§1.1 「所有（系统管理）」</td></tr>
 * </table>
 *
 * <h2>🔴 为什么不用 {@code null} 表示「不加条件」</h2>
 * {@code null} 语义在过滤器里是经典陷阱：调用方一旦忘判，就从「收窄」静默变成
 * 「全表可见」，而这正是本项目 S-26 失效的形态（§4.3 第 10 条静默失败）。
 * 故本类返回显式的 {@link Scope}，用 {@code allClients} 布尔表达「不加条件」。
 */
@Component
@RequiredArgsConstructor
public class AgencyDataScopeResolver {

    /** 管理/审核类角色：可见本代理全部客户。 */
    private static final Set<String> OVERSIGHT_ROLES =
            Set.of("AGENCY_ADMIN", "REVIEWER");

    private final AgencyUserMapper agencyUserMapper;
    private final AgencyUserEnterpriseMapper assignmentMapper;

    /**
     * 当前登录用户可见的客户企业范围。
     *
     * @return 永不返回 {@code null}；{@link Scope#allClients()} 为 true 时
     *         {@link Scope#enterpriseIds()} 无意义（调用方应不加过滤条件）
     */
    public Scope resolve() {
        String role = SecurityUtils.getCurrentAgencyRole();
        String userType = SecurityUtils.getCurrentUserType();

        // fail-loud：上下文缺 agency_role 时**不得**静默落到某个默认分支。
        // 若按「无权/全部」兜底，前者锁死全体代理用户、后者直接全表可见，
        // 两种都会把「上下文构造有问题」伪装成业务现象（SPEC-P115 同款决定）。
        if (role == null || role.isBlank()) {
            throw new BusinessException(500,
                    "登录上下文缺少代理角色(agency_role)，无法判定可见客户范围；"
                            + "请检查 LoginUser 构造是否写入 t_user.agency_role");
        }

        // 超管跨代理，不收窄（与 SPEC-P114 中 requireSameAgency 的豁免口径一致）
        if ("SUPER_ADMIN".equals(userType)) {
            return Scope.allClients();
        }

        // 监督类角色：可见本代理全部客户（S-26 §1.1）
        // ⚠️ 与「被分配了多少客户」无关 —— 否则监督者会被锁死在未分配给自己的客户外，
        //    那正是把 REVIEWER 降级成 ACCOUNTANT 的形态。
        if (OVERSIGHT_ROLES.contains(role)) {
            return Scope.allClients();
        }

        // 记账/助理类：仅分分配给自己的客户
        AgencyUserEntity me = currentAgencyUser();
        if (me == null) {
            // 有 agency_role 却没有成员资格行 ⇒ 上下文与数据不一致，fail-loud
            throw new BusinessException(500,
                    "登录上下文有 agency_role=[" + role + "] 但查不到对应的代理成员资格行"
                            + "（t_agency_user），无法判定可见客户范围");
        }
        List<Long> assigned = assignmentMapper.getEnterpriseIdsByAgencyUserId(me.getId());
        return Scope.only(assigned == null ? Set.of() : new LinkedHashSet<>(assigned));
    }

    /**
     * 取当前登录用户对应的代理成员资格行（t_agency_user）。
     */
    private AgencyUserEntity currentAgencyUser() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return null;
        }
        return agencyUserMapper.selectOne(
                new LambdaQueryWrapper<AgencyUserEntity>()
                        .eq(AgencyUserEntity::getUserId, userId)
                        .eq(AgencyUserEntity::getDeleted, 0)
                        .last("LIMIT 1"));
    }

    // ───────────────── 端点强制：两类语义，不混用 ─────────────────

    /**
     * **单企业查询**端点用：校验请求的 {@code enterpriseId} 在当前用户可见范围内。
     *
     * <p>⚠️ <b>为什么不让「不传企业」直接放行</b>：那正是 S-26 失效的形态 ——
     * 受限角色只要不带 {@code enterpriseId} 就能拿到本代理全部客户的数据。
     * 故受限角色**必须显式指定**，且指定值必须在可见集合内。
     *
     * @param requested 请求的客户企业 id；可为 {@code null}
     * @return 放行后应使用的企业 id（监督类角色原样返回，含 {@code null}=不限）
     * @throws BusinessException 403 指定了不可见的企业；400 受限却未指定企业
     */
    public Long enforceEnterpriseAccess(Long requested) {
        Scope scope = resolve();
        if (!scope.isRestricted()) {
            // 监督类角色：可见本代理全部客户，原样放行（含 null = 不限）
            return requested;
        }
        if (requested == null) {
            throw BusinessException.badRequest(
                    "请指定客户企业(enterpriseId)：当前角色只能查看分配给自己的客户，"
                            + "不支持列出全部客户");
        }
        if (!scope.enterpriseIds().contains(requested)) {
            throw BusinessException.forbidden(
                    "无权访问该客户企业：enterpriseId=" + requested
                            + " 未分配给当前用户");
        }
        return requested;
    }

    /**
     * **全局聚合视图**端点用（工作量统计、超期预警等 agency 级看板）：
     * 仅监督类角色可看；受限角色一律拒绝。
     *
     * <p>⚠️ <b>为什么不能给受限角色「过滤后返回」</b>：这类视图是跨客户聚合
     * （如「各会计的工作量排名」），把其中一个会计的数据滤掉，剩下的排序/占比
     * 依然泄露了他人信息，且聚合口径会静默失真。故直接拒绝，不过滤。
     *
     * @param action 动作名，用于报错文案（铁律 #14：报错须指明上下文）
     */
    public void requireOversight(String action) {
        Scope scope = resolve();
        if (scope.isRestricted()) {
            throw BusinessException.forbidden(
                    "无权访问「" + action + "」：该视图为代理级跨客户聚合，"
                            + "仅代理管理员/审核员可查看");
        }
    }

    /**
     * 可见范围。
     *
     * <p>⚠️ 组件名用 {@code unrestricted} 而非 {@code allClients}：
     * 后者与静态工厂 {@link #allClients()} 同名，record 会自动生成同款访问器 ⇒
     * 编译直接报 {@code invalid accessor method in record}。这是 record 的硬约束，
     * 不是风格问题。
     *
     * @param unrestricted    true = 本代理全部客户（查询不加 enterprise_id 条件）
     * @param enterpriseIds 仅当 {@code unrestricted=false} 有意义；空集合 = 谁都看不到
     */
    public record Scope(boolean unrestricted, Set<Long> enterpriseIds) {

        public static Scope allClients() {
            return new Scope(true, Collections.emptySet());
        }

        public static Scope only(Set<Long> enterpriseIds) {
            return new Scope(false, enterpriseIds == null
                    ? Collections.emptySet() : new LinkedHashSet<>(enterpriseIds));
        }

        /** 是否收窄到特定客户集合。 */
        public boolean isRestricted() {
            return !unrestricted;
        }
    }
}
