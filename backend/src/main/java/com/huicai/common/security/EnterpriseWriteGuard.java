package com.huicai.common.security;

import com.huicai.agency.tenant.constant.EnterpriseStatus;
import com.huicai.common.context.EnterpriseContextHolder;
import com.huicai.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * SPEC-P113 —— 非 ACTIVE 账套禁写。
 *
 * <p>{@code t_enterprise.status} 有 PENDING/ACTIVE/SUSPENDED/TERMINATED 四态状态机
 * （{@code EnterpriseStateMachineServiceImpl}，人工触发，符合铁律 #1），但该状态机
 * <b>只管改状态</b>，此前没有任何业务写入路径检查它 ⇒ 停用/待激活/已终止的账套
 * 仍可正常开单、录凭证、导发票。本切面在<b>服务端统一入口</b>补上这道准入判定。</p>
 *
 * <p><b>为何用切面而非逐点守卫</b>：全仓 {@code @PostMapping}/{@code @PutMapping}
 * 共 268 个写入端点，逐个加守卫必然漏（AGENTS §4.5 第 43 条已实证「只解释一条
 * 路径就整表放行」的教训）。竞品（用友 U8C / 金蝶云星空）同样把「停用后禁止
 * 录入」放在服务端统一入口，而非各业务模块自行判断。</p>
 *
 * <p><b>两条刻意的取舍</b>（SPEC-P113 §4 已锁定）：
 * <ol>
 *   <li><b>无企业上下文时放行</b> —— 系统初始化 / 定时任务 / 种子克隆走这条路，
 *       fail-closed 会把它们全挂掉。与 {@code EnterpriseDataPermissionInterceptor}
 *       的 fail-open 姿态一致，但此处显式记录该取舍。</li>
 *   <li><b>读路径不拦</b> —— 停用账套必须仍可查历史，否则无法对账审计（合规要求）。</li>
 * </ol>
 *
 * <p><b>已知风险</b>：Spring AOP 代理下，同类内自调用（{@code this.xxx()}）
 * <b>绕过切面</b>（§4.5 第 27 条「有注解 ≠ 会生效」同型）。故守卫测试除结构断言外，
 * 另有走 HTTP 代理的端到端用例证明切面真被触发。</p>
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class EnterpriseWriteGuard {

    private final JdbcTemplate jdbcTemplate;

    /** 只拦写操作；读路径刻意放行（停用账套仍需可查历史，见类注释）。 */
    @Around("@annotation(org.springframework.web.bind.annotation.PostMapping)"
            + " || @annotation(org.springframework.web.bind.annotation.PutMapping)"
            + " || @annotation(org.springframework.web.bind.annotation.DeleteMapping)")
    public Object assertEnterpriseWritable(ProceedingJoinPoint pjp) throws Throwable {
        Long enterpriseId = EnterpriseContextHolder.get();
        if (enterpriseId == null) {
            log.debug("无企业上下文，跳过账套状态准入校验（系统路径）：{}", pjp.getSignature());
            return pjp.proceed();
        }

        String status = currentStatus(enterpriseId);
        if (status == null) {
            // fail-closed：查不到账套即视为不可写，避免"主体不存在却能写数据"
            throw BusinessException.forbidden(
                    "账套不存在或已不可用，拒绝写入（enterpriseId=" + enterpriseId + "）");
        }
        if (!EnterpriseStatus.ACTIVE.getCode().equals(status)) {
            throw BusinessException.forbidden("账套当前状态为 " + status
                    + "（" + labelOf(status) + "），仅 ACTIVE 状态允许写入业务数据"
                    + "（enterpriseId=" + enterpriseId + "）");
        }
        return pjp.proceed();
    }

    private String currentStatus(Long enterpriseId) {
        try {
            List<String> r = jdbcTemplate.queryForList(
                    "SELECT status FROM t_enterprise WHERE id = ?", String.class, enterpriseId);
            return r.isEmpty() ? null : r.get(0);
        } catch (Exception e) {
            // fail-closed：状态查不到就不能放行，否则等于把准入判定交给运气
            log.error("查询账套状态失败，按 fail-closed 拒绝写入，enterpriseId={}", enterpriseId, e);
            throw BusinessException.forbidden(
                    "账套状态校验失败，拒绝写入（enterpriseId=" + enterpriseId + "）");
        }
    }

    private String labelOf(String code) {
        try {
            return EnterpriseStatus.valueOf(code).getLabel();
        } catch (IllegalArgumentException e) {
            return "未知状态";
        }
    }
}