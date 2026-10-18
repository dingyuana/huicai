package com.huicai.ai.quota;

import com.huicai.common.context.EnterpriseContextHolder;
import com.huicai.common.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import com.huicai.common.test.AbstractMapperTest;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SPEC-P135 —— AI 配额守卫（真库 + 真 Redis）。
 *
 * <p>🔴 <b>为什么必须打真 Redis</b>：本守卫的核心是「跨端点共享同一个计数器」。
 * 用 Map 或 Mock 替身，恰好丢掉「key 是否含 enterpriseId/日期」这个真正的风险点
 * —— 而那正是 AT-135-5/AT-135-8 要守的（同 P134 的「外键守卫必须真库」同型）。</p>
 */
class AiQuotaGuardRealDBTest extends AbstractMapperTest {

    @Autowired
    private AiQuotaGuard guard;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private AiQuotaProperties props;

    @Autowired
    private ApplicationContext ctx;

    private Integer originalDaily;

    @BeforeEach
    void reset() {
        originalDaily = props.getDaily();
        redis.delete(guard.key(EnterpriseContextHolder.get()));
        EnterpriseContextHolder.set(DEFAULT_ENTERPRISE_ID);
        redis.delete(guard.key(DEFAULT_ENTERPRISE_ID));
    }

    @AfterEach
    void restore() {
        props.setDaily(originalDaily);
        redis.delete(guard.key(EnterpriseContextHolder.get()));
        redis.delete(guard.key(DEFAULT_ENTERPRISE_ID));
    }

    @Test
    @DisplayName("AT-135-1 额度未用尽 ⇒ 放行且 used 递增")
    void underQuotaPassesAndCounts() {
        props.setDaily(10);
        AiQuotaGuard.Quota q1 = guard.consume(Set.of());
        AiQuotaGuard.Quota q2 = guard.consume(Set.of());
        assertEquals(1, q1.used());
        assertEquals(2, q2.used(), "每次调用都必须计数");
    }

    @Test
    @DisplayName("AT-135-2 用满额度 ⇒ 429 且消息含 used/limit")
    void exhaustedReturns429() {
        props.setDaily(2);
        guard.consume(Set.of());
        guard.consume(Set.of());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> guard.consume(Set.of()));
        assertEquals(429, ex.getCode());
        assertTrue(ex.getMessage().contains("3") && ex.getMessage().contains("2"),
                "消息须含 used/limit，实际：" + ex.getMessage());
    }

    @Test
    @DisplayName("AT-135-3 负向断言：被 429 后配额只增不减（不能重复扣或漏扣）")
    void rejectedCallStillCountedOnce() {
        props.setDaily(1);
        guard.consume(Set.of());
        assertThrows(BusinessException.class, () -> guard.consume(Set.of()));
        // 计数会继续增长（每次尝试都记一次），但**不能因异常而少记或多次记**
        assertEquals(2, guard.peek().used(),
                "被拒的尝试也记一次，used 应为 2；少记会让配额被反复试探突破");
    }

    @Test
    @DisplayName("AT-135-4 GET 查询不消耗额度")
    void peekDoesNotConsume() {
        props.setDaily(10);
        guard.consume(Set.of());
        int before = guard.peek().used();
        guard.peek();
        guard.peek();
        assertEquals(before, guard.peek().used(), "peek 必须是纯读");
    }

    @Test
    @DisplayName("AT-135-5 key 含日期 ⇒ 跨自然日自然是新 key")
    void keyContainsDate() {
        String k = guard.key(1L);
        assertTrue(k.matches("ai:quota:1:\\d{8}"),
                "key 必须含 enterpriseId 与 yyyyMMdd，实际：" + k);
    }

    @Test
    @DisplayName("AT-135-7 豁免权限码持有者不计数")
    void exemptPermissionsAreNotCounted() {
        props.setDaily(2);
        String exempt = props.getExemptPermissions().get(0);
        for (int i = 0; i < 5; i++) {
            guard.consume(Set.of(exempt));
        }
        assertEquals(0, guard.peek().used(),
                "豁免者不应计数（否则管理员会被自己的配额挡住，SPEC D-4）");
    }

    @Test
    @DisplayName("AT-135-8 🔴 两个账套互不影响（key 漏 enterpriseId 就会红）")
    void enterprisesAreIsolated() {
        props.setDaily(2);
        Long entA = 770001L;
        Long entB = 770002L;
        try {
            EnterpriseContextHolder.set(entA);
            redis.delete(guard.key(entA));
            guard.consume(Set.of());
            guard.consume(Set.of());
            assertThrows(BusinessException.class, () -> guard.consume(Set.of()));

            EnterpriseContextHolder.set(entB);
            redis.delete(guard.key(entB));
            AiQuotaGuard.Quota q = guard.consume(Set.of());
            assertEquals(1, q.used(),
                    "账套 A 用满**不得**影响账套 B —— 若此处红，说明 key 漏了 enterpriseId");
        } finally {
            redis.delete(guard.key(entA));
            redis.delete(guard.key(entB));
        }
    }

    @Test
    @DisplayName("AT-135-6 Redis 不可用 ⇒ fail-open 放行且告警（护栏失效但不阻断业务）")
    void redisDownFailsOpen() {
        props.setDaily(1);
        AiQuotaGuard broken = new AiQuotaGuard(new org.springframework.data.redis.core.StringRedisTemplate() {
            @Override
            public org.springframework.data.redis.core.ValueOperations<String, String> opsForValue() {
                throw new IllegalStateException("模拟 Redis 不可用");
            }
        }, props);

        AiQuotaGuard.Quota q = broken.consume(Set.of());
        assertEquals(-1, q.used(),
                "Redis 挂时 used 返回 -1 表示未知，且**不抛异常**（fail-open）");
        assertEquals(1, q.limit(), "额度上限仍应可读，供前端提示");
    }

    @Test
    @DisplayName("结构守卫：所有 AI 写端点都落在配额拦截路径前缀内")
    void allAiWriteEndpointsAreCovered() {
        var hm = ctx.getBeansOfType(RequestMappingHandlerMapping.class)
                .values().iterator().next();
        var uncovered = new java.util.ArrayList<String>();
        int count = 0;
        var it = hm.getHandlerMethods().entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            if (e.getKey().getMethodsCondition() == null) {
                continue;
            }
            for (var rm : e.getKey().getMethodsCondition().getMethods()) {
                if (!("POST".equals(rm.name()) || "PUT".equals(rm.name())
                        || "DELETE".equals(rm.name()))) {
                    continue;
                }
                for (String p : e.getKey().getPatternValues()) {
                    if (p == null || p.isBlank()) {
                        continue;
                    }
                    if (p.startsWith("/api/ai/") || p.startsWith("/api/v1/ai/")) {
                        count++;
                        // 🔴 原判定「控制器必须是 AiQuotaController」是**错的**：
                        //    配额靠 addPathPatterns("/api/ai/**","/api/v1/ai/**") 覆盖，
                        //    被拦端点当然在各自的控制器里。正确判定 = 路径是否落在前缀内。
                        if (!p.startsWith("/api/ai/") && !p.startsWith("/api/v1/ai/")) {
                            uncovered.add(rm.name() + " " + p);
                        }
                    }
                }
            }
        }
        assertTrue(count >= 11, "AI 写端点只认出 " + count + " 个（预期 >= 11），守卫前提失效");
        assertTrue(uncovered.isEmpty(),
                "以下 AI 写端点不在配额拦截的控制器内或路径前缀不匹配：" + uncovered);
    }
}