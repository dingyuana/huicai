package com.huicai.base.system.service;

import com.huicai.common.exception.BusinessException;
import com.huicai.base.system.entity.AuditLogEntity;
import com.huicai.base.system.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 登录暴力破解防护守卫。
 *
 * <p>核心机制：Redis INCR + EXPIRE 做滑动窗口计数，IP 与用户名双维度限制——
 * 任一维度达到阈值即触发锁定，成功登录清除两个维度的计数。
 *
 * <p>设计原则：
 * <ul>
 *   <li>Redis 不可用时 fail-open（放行登录，仅 warn 日志）——比锁死所有用户好</li>
 *   <li>锁定事件写审计日志，便于事后追查</li>
 *   <li>成功登录同时清零 IP 和用户名两个维度的计数</li>
 * </ul>
 *
 * <p>阈值（可通过配置调整）：
 * <ul>
 *   <li>窗口：5 分钟</li>
 *   <li>失败次数阈值：5 次</li>
 *   <li>锁定时长：15 分钟</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginBruteForceGuard {

    private static final String KEY_PREFIX_FAIL_IP = "login:fail:ip:";
    private static final String KEY_PREFIX_FAIL_USER = "login:fail:usr:";
    private static final String KEY_PREFIX_LOCK_IP = "login:lock:ip:";
    private static final String KEY_PREFIX_LOCK_USER = "login:lock:usr:";

    private static final int FAILURE_THRESHOLD = 5;
    private static final Duration WINDOW_DURATION = Duration.ofMinutes(5);
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private final StringRedisTemplate redisTemplate;
    private final AuditLogService auditLogService;

    /**
     * 登录失败时调用——记录失败、检查阈值、触发锁定。
     *
     * @param ip       客户端 IP
     * @param username 失败的用户名
     */
    public void onLoginFailure(String ip, String username) {
        try {
            incrAndCheck(KEY_PREFIX_FAIL_IP, ip, WINDOW_DURATION, KEY_PREFIX_LOCK_IP, ip, LOCK_DURATION, "IP", ip);
            incrAndCheck(KEY_PREFIX_FAIL_USER, username, WINDOW_DURATION, KEY_PREFIX_LOCK_USER, username, LOCK_DURATION, "用户名", username);
        } catch (BusinessException e) {
            // 锁定已触发，向上抛 429
            throw e;
        } catch (Exception e) {
            // Redis 不可用时 fail-open
            log.warn("登录失败计数写入 Redis 失败，放行登录（fail-open）: {}", e.getMessage());
        }
    }

    /**
     * 登录成功时调用——清除两个维度的失败计数。
     *
     * @param ip       客户端 IP
     * @param username 成功的用户名
     */
    public void onLoginSuccess(String ip, String username) {
        try {
            redisTemplate.delete(KEY_PREFIX_FAIL_IP + ip);
            redisTemplate.delete(KEY_PREFIX_FAIL_USER + username);
            // 同时清除可能的锁定键（成功登录后解除锁定）
            redisTemplate.delete(KEY_PREFIX_LOCK_IP + ip);
            redisTemplate.delete(KEY_PREFIX_LOCK_USER + username);
        } catch (Exception e) {
            log.warn("登录成功计数清除失败: {}", e.getMessage());
        }
    }

    /**
     * 手动解除锁定（管理员操作）。
     */
    public void unlock(String identifier, boolean isIp) {
        try {
            String prefix = isIp ? KEY_PREFIX_LOCK_IP : KEY_PREFIX_LOCK_USER;
            String failPrefix = isIp ? KEY_PREFIX_FAIL_IP : KEY_PREFIX_FAIL_USER;
            redisTemplate.delete(prefix + identifier);
            redisTemplate.delete(failPrefix + identifier);
        } catch (Exception e) {
            log.warn("手动解锁失败: {}", e.getMessage());
        }
    }

    /**
     * 查询指定标识的失败次数。
     */
    public long getFailCount(String key) {
        try {
            String val = redisTemplate.opsForValue().get(key);
            return val == null ? 0 : Long.parseLong(val);
        } catch (Exception e) {
            return 0;
        }
    }

    private void incrAndCheck(String failKeyPrefix, String identifier,
                               Duration window, String lockKeyPrefix, String lockId,
                               Duration lockDuration, String dimensionName, String displayValue) {
        String failKey = failKeyPrefix + identifier;
        String lockKey = lockKeyPrefix + lockId;

        // 先检查是否已锁定
        if (redisTemplate.hasKey(lockKey)) {
            throw BusinessException.tooManyRequests(
                    "系统检测到异常登录活动，账户已被临时锁定，请 " + lockDuration.toMinutes() + " 分钟后再试");
        }

        Long count = redisTemplate.opsForValue().increment(failKey);
        if (count == 1) {
            redisTemplate.expire(failKey, window.toMinutes(), TimeUnit.MINUTES);
        }

        if (count != null && count >= FAILURE_THRESHOLD) {
            // 触发锁定
            redisTemplate.opsForValue().set(lockKey, "1", lockDuration);
            logLockEvent(dimensionName, displayValue);
            throw BusinessException.tooManyRequests(
                    "系统检测到异常登录活动，账户已被临时锁定，请 " + lockDuration.toMinutes() + " 分钟后再试");
        }
    }

    private void logLockEvent(String dimension, String value) {
        try {
            AuditLogEntity log = new AuditLogEntity();
            log.setUsername("SYSTEM");
            log.setUserId(null);
            log.setOperation("LOGIN_LOCKOUT");
            log.setMethod("LoginBruteForceGuard");
            log.setStatus("fail");
            log.setModule("AUTH");
            log.setRequestParams("{\"dimension\":\"" + dimension + "\",\"value\":\"" + value + "\"}");
            log.setResponseResult("账户因多次登录失败被临时锁定");
            auditLogService.save(log);
        } catch (Exception e) {
            log.warn("记录登录锁定审计日志失败: {}", e.getMessage());
        }
    }
}
