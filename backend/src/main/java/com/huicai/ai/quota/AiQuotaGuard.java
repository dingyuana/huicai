package com.huicai.ai.quota;

import com.huicai.common.context.EnterpriseContextHolder;
import com.huicai.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.ZoneId;
import java.util.Set;

/**
 * SPEC-P135 —— AI 端点日配额守卫。
 *
 * <p><b>为什么是「配额」而不是「权限码」</b>：AI 能力不是职责而是<b>成本</b>。
 * 业务权限码约束「谁能改账」，配额约束「谁能花钱、花多少」——
 * 用角色分权会让「出纳与财务主管都该能调 AI」变成「日常核销寸步难行」。
 * 竞品（用友 YonSuite / 金蝶云星空）同样按租户额度限流。</p>
 *
 * <p>🔴 <b>fail-open</b>：Redis 不可用时<b>放行并告警</b>。
 * 配额是<b>成本护栏</b>不是<b>安全边界</b>，为它阻断财务业务不划算。
 * 代价已在 SPEC §5 局限 3 登记。</p>
 *
 * <p>⚠️ <b>key 必须含 enterpriseId</b>：否则<b>一个账套用满会拖垮所有账套</b>
 * （SPEC AT-135-8 专门守这一点）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiQuotaGuard {

    private final StringRedisTemplate redis;
    private final AiQuotaProperties props;

    public record Quota(int used, int limit, LocalDateTime resetAt) {}

    /**
     * 校验并计一次。
     *
     * @param exemptPermissions 当前登录者持有的权限码（用于豁免判定）
     * @return 额度信息；豁免或关闭时 {@code used} 返回当前值但不递增
     */
    public Quota consume(Set<String> exemptPermissions) {
        int limit = props.getDaily() == null ? 0 : props.getDaily();
        Long enterpriseId = EnterpriseContextHolder.get();

        if (!props.isEnabled() || limit <= 0) {
            return new Quota(0, limit, nextReset());
        }
        if (isExempt(exemptPermissions)) {
            // 豁免者不计数，但仍返回当前用量以便前端展示
            return new Quota(currentUsed(enterpriseId), limit, nextReset());
        }
        if (enterpriseId == null) {
            // 无企业上下文的写路径属异常情况（AI 端点应始终在请求内），
            // 按「不计数但放行」处理，并在日志留痕，避免把系统任务全拦死。
            log.warn("AI 配额检查时无企业上下文，跳过计数（endpoint 调用方问题）");
            return new Quota(0, limit, nextReset());
        }

        String key = key(enterpriseId);
        int used;
        try {
            Long v = redis.opsForValue().increment(key);
            used = v == null ? 1 : v.intValue();
            if (used == 1) {
                // 首次计数时设过期：到次日 00:00 之后不久自动清理
                redis.expire(key, Duration.between(LocalDateTime.now(), nextReset()).plusHours(1));
            }
        } catch (Exception e) {
            // 🔴 fail-open：护栏失效不能阻断业务，但必须留痕
            log.error("AI 配额计数失败（Redis 不可用），按 fail-open 放行 —— "
                    + "**成本护栏当前失效**，enterpriseId={}", enterpriseId, e);
            return new Quota(-1, limit, nextReset());
        }

        if (used > limit) {
            throw BusinessException.tooManyRequests(
                    "今日 AI 调用额度已用尽（" + used + "/" + limit + "），明日 0 点重置");
        }
        return new Quota(used, limit, nextReset());
    }

    /** 只读查询，不消耗额度（SPEC AT-135-4）。 */
    public Quota peek() {
        int limit = props.getDaily() == null ? 0 : props.getDaily();
        if (!props.isEnabled()) {
            return new Quota(0, limit, nextReset());
        }
        return new Quota(currentUsed(EnterpriseContextHolder.get()), limit, nextReset());
    }

    private boolean isExempt(Set<String> authorities) {
        if (authorities == null || authorities.isEmpty()) {
            return false;
        }
        return props.getExemptPermissions().stream().anyMatch(authorities::contains);
    }

    private int currentUsed(Long enterpriseId) {
        if (enterpriseId == null) {
            return 0;
        }
        try {
            String v = redis.opsForValue().get(key(enterpriseId));
            return v == null ? 0 : Integer.parseInt(v);
        } catch (Exception e) {
            log.error("AI 配额读取失败，peek 返回 -1 表示未知", e);
            return -1;
        }
    }

    /** key 格式 {@code ai:quota:{enterpriseId}:{yyyyMMdd}}（SPEC §2.1）。 */
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * key 含日期 ⇒ 跨自然日自然是新 key（SPEC AT-135-5）。
     *
     * <p>⚠️ 日期格式<b>必须与 SPEC 逐字一致</b>（yyyyMMdd，非 ISO 带横线）：
     * 两者都能实现「跨日重置」，但不一致会让「按 SPEC 写的运维排查命令」
     * （如 {@code redis-cli get ai:quota:1:20261017}）查不到数据。</p>
     */
    String key(Long enterpriseId) {
        return "ai:quota:" + enterpriseId + ":"
                + LocalDate.now(ZoneId.systemDefault()).format(DAY);
    }

    private LocalDateTime nextReset() {
        return LocalDate.now(ZoneId.systemDefault()).plusDays(1).atStartOfDay();
    }
}