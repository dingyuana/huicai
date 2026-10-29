package com.huicai.base.system.service;

import com.huicai.base.system.entity.AuditLogEntity;
import com.huicai.base.system.service.AuditLogService;
import com.huicai.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * R2：登录暴力破解防护守卫单元测试。
 *
 * <p>覆盖场景：
 * <ul>
 *   <li>成功登录清除计数</li>
 *   <li>失败登录递增计数</li>
 *   <li>达到阈值触发锁定（429）</li>
 *   <li>锁定后继续拒绝</li>
 *   <li>解锁后恢复</li>
 *   <li>Redis 不可用 fail-open</li>
 *   <li>审计日志写入</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class LoginBruteForceGuardTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOps;
    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private LoginBruteForceGuard guard;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test
    @DisplayName("R2-1: 成功登录后清除 IP 和用户名维度计数")
    void success_clearsBothCounters() {
        guard.onLoginSuccess("1.2.3.4", "alice");

        verify(redisTemplate).delete("login:fail:ip:1.2.3.4");
        verify(redisTemplate).delete("login:fail:usr:alice");
        verify(redisTemplate).delete("login:lock:ip:1.2.3.4");
        verify(redisTemplate).delete("login:lock:usr:alice");
    }

    @Test
    @DisplayName("R2-2: 首次失败递增 IP 计数到 1")
    void firstFailure_incrementsIpTo1() {
        when(valueOps.increment("login:fail:ip:1.2.3.4")).thenReturn(1L);
        when(valueOps.increment("login:fail:usr:alice")).thenReturn(1L);

        guard.onLoginFailure("1.2.3.4", "alice");

        verify(valueOps).increment("login:fail:ip:1.2.3.4");
        verify(valueOps).increment("login:fail:usr:alice");
        verify(redisTemplate).expire("login:fail:ip:1.2.3.4", 5L, TimeUnit.MINUTES);
        verify(redisTemplate).expire("login:fail:usr:alice", 5L, TimeUnit.MINUTES);
    }

    @Test
    @DisplayName("R2-3: 第 5 次失败触发锁定并返回 429")
    void fifthFailure_triggersLockout_429() {
        when(valueOps.increment("login:fail:ip:1.2.3.4")).thenReturn(1L, 2L, 3L, 4L, 5L);
        when(valueOps.increment("login:fail:usr:alice")).thenReturn(1L);
        // 锁定写入（Duration 重载）——用 any() 匹配 Duration 参数，避免 null NPE
        doNothing().when(valueOps).set(anyString(), anyString(), (java.time.Duration) any());
        lenient().when(redisTemplate.hasKey(anyString())).thenReturn(false);

        for (int i = 0; i < 4; i++) {
            guard.onLoginFailure("1.2.3.4", "alice");
        }

        assertThatThrownBy(() -> guard.onLoginFailure("1.2.3.4", "alice"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo(429));

        // 确认写入了锁定键（IP 维度先达阈值）
        verify(valueOps).set(eq("login:lock:ip:1.2.3.4"), eq("1"), any());
    }

    @Test
    @DisplayName("R2-4: 锁定时段内继续拒绝（不重复计数）")
    void whileLocked_continuesRejecting() {
        when(redisTemplate.hasKey("login:lock:ip:1.2.3.4")).thenReturn(true);

        assertThatThrownBy(() -> guard.onLoginFailure("1.2.3.4", "alice"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo(429));

        // 不应对计数做 increment（已被锁，不用继续计数）
        verifyNoInteractions(valueOps);
    }

    @Test
    @DisplayName("R2-5: 解锁后恢复正常")
    void unlock_restoresNormalOperation() {
        guard.unlock("1.2.3.4", true);

        verify(redisTemplate).delete("login:lock:ip:1.2.3.4");
        verify(redisTemplate).delete("login:fail:ip:1.2.3.4");
    }

    @Test
    @DisplayName("R2-6: Redis 不可用时 fail-open（不抛异常）")
    void redisDown_failsOpen() {
        when(redisTemplate.hasKey(anyString()))
                .thenThrow(new RuntimeException("Redis连接失败"));

        // 不应抛出，应静默放行
        guard.onLoginFailure("1.2.3.4", "alice");

        verifyNoMoreInteractions(valueOps);
    }

    @Test
    @DisplayName("R2-7: 锁定事件写入审计日志")
    void lockout_writesAuditLog() {
        when(valueOps.increment("login:fail:ip:1.2.3.4")).thenReturn(1L, 2L, 3L, 4L, 5L);
        when(valueOps.increment("login:fail:usr:alice")).thenReturn(1L);
        doNothing().when(valueOps).set(anyString(), anyString(), (java.time.Duration) any());
        lenient().when(redisTemplate.hasKey(anyString())).thenReturn(false);

        for (int i = 0; i < 4; i++) {
            guard.onLoginFailure("1.2.3.4", "alice");
        }

        assertThatThrownBy(() -> guard.onLoginFailure("1.2.3.4", "alice"))
                .isInstanceOf(BusinessException.class);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<AuditLogEntity> captor = ArgumentCaptor.forClass(AuditLogEntity.class);
        verify(auditLogService).save(captor.capture());
        AuditLogEntity log = captor.getValue();
        assertThat(log.getOperation()).isEqualTo("LOGIN_LOCKOUT");
        assertThat(log.getModule()).isEqualTo("AUTH");
        assertThat(log.getStatus()).isEqualTo("fail");
        assertThat(log.getUsername()).isEqualTo("SYSTEM");
    }

    @Test
    @DisplayName("R2-8: getFailCount 返回当前计数")
    void getFailCount_returnsCurrentCount() {
        when(valueOps.get("login:fail:ip:1.2.3.4")).thenReturn("3");

        long count = guard.getFailCount("login:fail:ip:1.2.3.4");

        assertThat(count).isEqualTo(3);
    }

    @Test
    @DisplayName("R2-9: getFailCount Redis 异常时返回 0")
    void getFailCount_redisError_returnsZero() {
        when(valueOps.get(anyString())).thenThrow(new RuntimeException("boom"));

        long count = guard.getFailCount("login:fail:ip:1.2.3.4");

        assertThat(count).isEqualTo(0);
    }
}
