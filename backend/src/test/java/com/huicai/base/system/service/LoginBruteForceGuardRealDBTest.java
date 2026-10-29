package com.huicai.base.system.service;

import com.huicai.common.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * R2 集成测试：登录暴力破解防护（连接真实 Redis）。
 *
 * <p>使用 docker-compose 中已有的 Redis 容器（port 6379, db=1），
 * 验证守卫在真实 Redis 环境下的行为。
 * 与 {@link LoginBruteForceGuardTest}（纯 Mockito）互补——本测试验证 Redis 交互真实性。
 */
@DisplayName("R2: 登录暴力破解防护集成测试（真实 Redis）")
@SpringBootTest
class LoginBruteForceGuardRealDBTest {

    @Autowired
    private LoginBruteForceGuard guard;
    @Autowired
    private StringRedisTemplate redis;

    @BeforeEach
    void setUp() {
        redis.getConnectionFactory().getConnection().flushAll();
    }

    @AfterEach
    void tearDown() {
        try {
            redis.getConnectionFactory().getConnection().flushAll();
        } catch (Exception ignored) {
        }
    }

    @Test
    @DisplayName("R2-INT-1: 5 次失败触发锁定，返回 429")
    void fiveFailuresTriggerLockout() {
        String ip = "192.168.1.100";
        String user = "testuser";

        // 前 4 次失败应正常
        for (int i = 0; i < 4; i++) {
            guard.onLoginFailure(ip, user);
        }

        // 第 5 次失败应触发锁定并返回 429
        assertThatThrownBy(() -> guard.onLoginFailure(ip, user))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo(429));
    }

    @Test
    @DisplayName("R2-INT-2: 锁定时段内持续拒绝")
    void whileLocked_continuesRejecting() {
        String ip = "10.0.0.1";
        String user = "lockedUser";

        // 触发锁定
        triggerLockout(ip, user);

        // 后续失败应继续被拒
        assertThatThrownBy(() -> guard.onLoginFailure(ip, user))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo(429));
    }

    @Test
    @DisplayName("R2-INT-3: 成功登录后清除计数")
    void successClearsCounters() {
        String ip = "172.16.0.1";
        String user = "clearedUser";

        // 3 次失败
        for (int i = 0; i < 3; i++) {
            guard.onLoginFailure(ip, user);
        }

        // 成功登录
        guard.onLoginSuccess(ip, user);

        // 计数应被清除
        assertThat(redis.opsForValue().get("login:fail:ip:" + ip)).isNull();
        assertThat(redis.opsForValue().get("login:fail:usr:" + user)).isNull();
        assertThat(redis.opsForValue().get("login:lock:ip:" + ip)).isNull();
        assertThat(redis.opsForValue().get("login:lock:usr:" + user)).isNull();
    }

    @Test
    @DisplayName("R2-INT-4: 解锁 IP 后 IP 维度键被清除")
    void unlockIp_clearsIpLockKey() {
        String ip = "192.168.2.1";
        String user = "unlockedUser";

        // 触发锁定
        triggerLockout(ip, user);

        // 解锁 IP 维度
        guard.unlock(ip, true);

        // 验证 IP 维度的锁定键和失败计数键已被删除
        assertThat(redis.opsForValue().get("login:lock:ip:" + ip)).isNull();
        assertThat(redis.opsForValue().get("login:fail:ip:" + ip)).isNull();
    }

    @Test
    @DisplayName("R2-INT-5: 不同 IP 独立计数（各自 5 次才锁定）")
    void differentIpsIndependentCounting() {
        String ip1 = "1.1.1.1";
        String ip2 = "2.2.2.2";
        String user1 = "userOne";
        String user2 = "userTwo";

        // IP1 失败 4 次
        for (int i = 0; i < 4; i++) {
            guard.onLoginFailure(ip1, user1);
        }

        // IP2 失败 4 次
        for (int i = 0; i < 4; i++) {
            guard.onLoginFailure(ip2, user2);
        }

        // IP1 第 5 次应触发锁定
        assertThatThrownBy(() -> guard.onLoginFailure(ip1, user1))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo(429));

        // IP2 第 5 次应触发锁定
        assertThatThrownBy(() -> guard.onLoginFailure(ip2, user2))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo(429));
    }

    @Test
    @DisplayName("R2-INT-6: 获取失败次数")
    void getFailCountReturnsCurrentCount() {
        String ip = "8.8.8.8";
        String user = "countUser";

        guard.onLoginFailure(ip, user);
        guard.onLoginFailure(ip, user);

        long count = guard.getFailCount("login:fail:ip:" + ip);
        assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("R2-INT-7: 锁定键带 TTL（约 15 分钟）")
    void lockKeyHasTtl() {
        String ip = "9.9.9.9";
        String user = "ttlUser";

        // 触发锁定
        triggerLockout(ip, user);

        // 验证锁定键存在且有 TTL
        Long ttl = redis.opsForValue().getOperations().getExpire("login:lock:ip:" + ip, java.util.concurrent.TimeUnit.MINUTES);
        assertThat(ttl).isNotNull();
        assertThat(ttl).isBetween(14L, 16L);
    }

    /**
     * 辅助方法：连续失败直到触发锁定。
     */
    private void triggerLockout(String ip, String user) {
        for (int i = 0; i < 10; i++) {
            try {
                guard.onLoginFailure(ip, user);
            } catch (BusinessException e) {
                // 锁定后继续调用应持续返回 429
                assertThat(e.getCode()).isEqualTo(429);
                return;
            }
        }
        throw new AssertionError("Expected lockout to be triggered");
    }
}
