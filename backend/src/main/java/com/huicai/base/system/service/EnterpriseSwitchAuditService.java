package com.huicai.base.system.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 企业切换留痕（REQ-2026-129 P102 / SPEC §1.1「SUPER_ADMIN 切换须审计留痕」）
 *
 * <p>用 {@link Propagation#REQUIRES_NEW} 独立事务写入：留痕失败必须可见，
 * 不能被切换动作的成败掩盖。
 *
 * <p><b>为何不用 AuditLogMapper</b>：{@code AuditLogEntity} 只有
 * {@code operation/module/userId/username/ipAddress} 等少数字段，
 * <b>并不存在</b> {@code before_data} / {@code after_data} / {@code entity_type}
 * 对应的属性（真实列存在但实体未声明，且 requestParams/oldSnapshot/newSnapshot
 * 被标为 {@code exist=false} 幽灵字段）—— 这正是 P103 待修的审计失效本身。
 * 此处显式按列名写入快照，P103 修好实体后可改回 Mapper。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EnterpriseSwitchAuditService {

    private final JdbcTemplate jdbcTemplate;

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRES_NEW)
    public void recordEnterpriseSwitch(Long userId, Long fromEnterpriseId, Long toEnterpriseId) {
        try {
            jdbcTemplate.update(
                    "INSERT INTO t_audit_log (module, operation, entity_type, entity_no, "
                            + "before_data, after_data, operator_id) "
                            + "VALUES (?, ?, ?, ?, ?::jsonb, ?::jsonb, ?)",
                    "ENTERPRISE", "SWITCH", "ENTERPRISE", String.valueOf(toEnterpriseId),
                    "{\"enterpriseId\":" + nullSafe(fromEnterpriseId) + "}",
                    "{\"enterpriseId\":" + nullSafe(toEnterpriseId) + "}",
                    userId);
        } catch (Exception e) {
            log.error("企业切换留痕失败: userId={}, {} -> {}", userId, fromEnterpriseId, toEnterpriseId, e);
            throw e;
        }
    }

    private String nullSafe(Long v) {
        return v == null ? "null" : String.valueOf(v);
    }
}
