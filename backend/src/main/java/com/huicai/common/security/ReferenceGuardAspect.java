package com.huicai.common.security;

import com.huicai.common.context.EnterpriseContextHolder;
import com.huicai.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SPEC-P134 —— 引用完整性守卫切面。
 *
 * <p>在 {@link ReferenceGuard} 标注的删除方法执行<b>之前</b>，逐个统计子表里有多少行
 * 仍指向该记录；只要有一处 &gt; 0，就抛 {@link BusinessException} 并<b>不执行删除</b>。</p>
 *
 * <p><b>三条刻意的设计取舍</b>（SPEC-P134 §5）：</p>
 * <ol>
 *   <li><b>子表查询失败 ⇒ fail-loud</b>。子表/列名写错时<b>不</b>静默跳过 ——
 *       静默跳过的效果等于「守卫不存在」，且症状是「突然少拦了一类」，
 *       与 AGENTS §4.5 第 49 条「{@code if (x != null)} 包住关键逻辑 = 静默失败」同型。</li>
 *   <li><b>无企业上下文时放行</b> —— 系统初始化 / 定时任务 / 种子克隆走这条路，
 *       fail-closed 会把它们全挂掉（同 {@code EnterpriseWriteGuard} 的取舍）。</li>
 *   <li><b>错误消息含子表中文名与条数，但不含库结构细节</b> ——
 *       表名/列名/约束名只进服务端日志，不回给前端。</li>
 * </ol>
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class ReferenceGuardAspect {

    private final JdbcTemplate jdbcTemplate;

    /** 子表是否含 {@code enterprise_id} 的缓存 —— 启动后不再查（结构不变）。 */
    private final Map<String, Boolean> enterpriseColumnCache = new ConcurrentHashMap<>();

    /** 已就「子表无 {@code enterprise_id}」告警过的表，避免刷屏。 */
    private final Set<String> warnedGlobalTables = ConcurrentHashMap.newKeySet();

    @Around("@annotation(com.huicai.common.security.ReferenceGuard)")
    public Object assertNoReferences(ProceedingJoinPoint pjp) throws Throwable {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        ReferenceGuard guard = signature.getMethod().getAnnotation(ReferenceGuard.class);
        if (guard == null) {
            return pjp.proceed();
        }

        Object[] args = pjp.getArgs();
        if (guard.idArgIndex() >= args.length) {
            throw BusinessException.badRequest(
                    "引用完整性守卫参数下标越界：" + pjp.getSignature().toShortString());
        }
        Object rawId = args[guard.idArgIndex()];
        if (rawId == null) {
            return pjp.proceed();
        }
        Long id = toLong(rawId);

        List<String> refs = referencedChildren(guard, id);
        if (!refs.isEmpty()) {
            throw BusinessException.badRequest(
                    "该" + guard.label() + "已被以下数据引用，无法删除：" + String.join("、", refs)
                            + "。请先处理相关单据，或改为停用。");
        }
        return pjp.proceed();
    }

    private Long toLong(Object v) {
        if (v instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.valueOf(String.valueOf(v));
        } catch (NumberFormatException e) {
            throw BusinessException.badRequest("引用完整性守卫无法解析记录标识：" + v);
        }
    }

    private List<String> referencedChildren(ReferenceGuard guard, Long id) {
        Long enterpriseId = EnterpriseContextHolder.get();
        List<String> refs = new ArrayList<>();
        for (Ref ref : guard.children()) {
            Long count = countReferences(ref, id, enterpriseId);
            if (count != null && count > 0) {
                refs.add(ref.label() + "(" + count + "条)");
            }
        }
        return refs;
    }

    private Long countReferences(Ref ref, Long id, Long enterpriseId) {
        // 🔴 2026-10-16 修正：完整性检查**默认全局**，不加 enterprise_id 过滤。
        //    加了就会「过滤掉的那部分引用被静默漏报」⇒ 守卫误判为「无引用可删」，
        //    这是 fail-open，方向与 fail-closed 相反。详见 @Ref#scopedByEnterprise。
        boolean scoped = ref.scopedByEnterprise() && hasEnterpriseColumn(ref.table());

        if (ref.scopedByEnterprise() && !hasEnterpriseColumn(ref.table())) {
            if (warnedGlobalTables.add(ref.table())) {
                log.warn("子表 {} 声明按企业隔离但实际无 enterprise_id 列，"
                        + "本次按全局口径检查 —— 该例外须在守卫测试中显式登记", ref.table());
            }
        }

        String sql = "SELECT count(*) FROM " + ref.table() + " WHERE " + ref.column() + " = ?"
                + (scoped ? " AND enterprise_id = ?" : "");
        Object[] params = scoped ? new Object[]{id, enterpriseId} : new Object[]{id};

        try {
            Long c = jdbcTemplate.queryForObject(sql, Long.class, params);
            return c == null ? 0L : c;
        } catch (Exception e) {
            // fail-loud：子表查不了就不能放行，否则等于把完整性交给运气
            log.error("引用完整性检查失败：表={} 列={} 拒绝删除", ref.table(), ref.column(), e);
            throw BusinessException.badRequest(
                    "引用完整性校验失败（" + ref.label() + "），已拒绝删除以保护数据完整性");
        }
    }

    private boolean hasEnterpriseColumn(String table) {
        return enterpriseColumnCache.computeIfAbsent(table, t -> {
            Integer c = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM information_schema.columns "
                            + "WHERE table_schema = current_schema() AND table_name = ? "
                            + "AND column_name = 'enterprise_id'",
                    Integer.class, t);
            return c != null && c > 0;
        });
    }
}