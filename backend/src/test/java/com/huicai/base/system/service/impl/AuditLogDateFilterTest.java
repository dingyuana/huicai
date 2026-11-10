package com.huicai.base.system.service.impl;

import com.huicai.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SPEC-P152：审计日志日期过滤的类型守卫。
 *
 * <p>2026-11-04 实机坐实的缺陷：{@code operation_time} 是 timestamp 列，
 * 原实现把 "2026-11-01" 字符串直接塞进 wrapper，Postgres 报
 * {@code operator does not exist: timestamp >= character varying}，
 * 操作日志页查询整条 500。H2 会隐式转换 ⇒ L1 拦不住，必须显式守卫
 * 「进 wrapper 的日期必须是 LocalDateTime 形态」这一口径。</p>
 */
class AuditLogDateFilterTest {

    @Test
    void 合法日期_解析为当日闭区间端点() {
        assertEquals(LocalDateTime.of(2026, 11, 1, 0, 0, 0),
                AuditLogServiceImpl.parseDayStart("2026-11-01"));
        assertEquals(LocalDateTime.of(2026, 11, 30, 23, 59, 59),
                AuditLogServiceImpl.parseDayEnd("2026-11-30"));
    }

    @Test
    void 前后空白容忍() {
        assertEquals(LocalDateTime.of(2026, 11, 1, 0, 0, 0),
                AuditLogServiceImpl.parseDayStart("  2026-11-01 "));
    }

    @Test
    void 非法格式_400而非静默全量扫描() {
        // 沉默吞掉非法日期 = 过滤条件消失 = 全表扫 + 用户以为过滤生效（名实不符）
        assertThrows(BusinessException.class, () -> AuditLogServiceImpl.parseDayStart("01/11/2026"));
        assertThrows(BusinessException.class, () -> AuditLogServiceImpl.parseDayEnd("202611"));
        assertThrows(BusinessException.class, () -> AuditLogServiceImpl.parseDayEnd("abc"));
    }
}
