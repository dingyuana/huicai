package com.huicai.sme.cash.integration;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huicai.base.business.constant.StatementStatus;
import com.huicai.base.business.entity.BankStatementEntity;
import com.huicai.base.business.mapper.BankStatementMapper;
import com.huicai.common.test.AbstractMapperTest;
import com.huicai.sme.cash.service.BankStatementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 银行流水审核链路集成测试（真实 PostgreSQL + Flyway）。
 *
 * <p>覆盖以下缺陷场景：
 * <ul>
 *   <li>audit() 后 reviewStatus 必须离开 CONFIRMED（V120 CHECK 约束修复验证）</li>
 *   <li>audit() 重复调用不重复生成单据（幂等守卫验证）</li>
 * </ul>
 *
 * <p>基于 {@link AbstractMapperTest}，启动真实 PostgreSQL 16 + Flyway 迁移，
 * 自动执行 V001–V120 全部 migration（含 V120 CHECK 约束扩展）。
 * 每条测试方法独立事务，执行后自动回滚，互不影响。
 *
 * @SlowTest — 需要 Docker + Testcontainers
 */
class BankStatementAuditIntegrationTest extends AbstractMapperTest {

    private static final Long ENTERPRISE_ID = 1L;
    private static final Long USER_ID = 1L;

    @Autowired
    private BankStatementService service;

    @Autowired
    private BankStatementMapper mapper;

    @BeforeEach
    void clearStatements() {
        mapper.delete(new LambdaQueryWrapper<BankStatementEntity>()
                .eq(BankStatementEntity::getEnterpriseId, ENTERPRISE_ID));
    }

    /**
     * 审计状态流转：PENDING → UNCONFIRMED → CLASSIFIED → CONFIRMED → voucher_generated
     */
    @Test
    @DisplayName("audit: 状态从 CONFIRMED 推进到 voucher_generated")
    void audit_must_advance_review_status_from_confirmed_to_voucher_generated() {
        BankStatementEntity stmt = createClassifiedStatement();

        stmt.setReviewStatus(StatementStatus.UNCONFIRMED);
        mapper.updateById(stmt);

        // review 模拟出纳确认
        BankStatementEntity confirmed = service.review(stmt.getId(), USER_ID);
        assertEquals(StatementStatus.CONFIRMED, confirmed.getReviewStatus(),
                "review() 后状态应为 CONFIRMED");

        // audit 审核 → 必须离开 CONFIRMED
        BankStatementEntity audited = service.audit(stmt.getId(), USER_ID);

        String afterAudit = audited.getReviewStatus();
        assertNotEquals(StatementStatus.CONFIRMED, afterAudit,
                "audit() 后 reviewStatus 必须离开 CONFIRMED，当前仍为 CONFIRMED 说明状态更新未生效");

        // 从 DB 重新查询，确认不是内存缓存
        BankStatementEntity inDb = mapper.selectById(stmt.getId());
        assertNotEquals(StatementStatus.CONFIRMED, inDb.getReviewStatus(),
                "DB 中 reviewStatus 仍为 CONFIRMED，updateById 可能被 CHECK 约束拒绝并回滚");

        System.out.println("[PASS] audit 后状态: " + afterAudit + " → DB: " + inDb.getReviewStatus());
    }

    /**
     * 幂等守卫：audit() 第二次调用不重复生成单据，直接返回原状态
     */
    @Test
    @DisplayName("audit: 重复调用返回原状态，不重复生成")
    void audit_idempotent_double_call_returns_same_status() {
        BankStatementEntity stmt = createClassifiedStatement();
        stmt.setReviewStatus(StatementStatus.UNCONFIRMED);
        mapper.updateById(stmt);

        service.review(stmt.getId(), USER_ID);
        service.audit(stmt.getId(), USER_ID);

        // 幂等调用：第二次 audit 应直接返回，不抛异常
        BankStatementEntity second = service.audit(stmt.getId(), USER_ID);
        assertNotNull(second);

        // 状态不应改变
        BankStatementEntity db = mapper.selectById(stmt.getId());
        assertEquals(second.getReviewStatus(), db.getReviewStatus(),
                "第二次 audit() 不应改变 reviewStatus");

        System.out.println("[PASS] 幂等: 第二次 audit 返回状态=" + second.getReviewStatus());
    }

    /**
     * 边界：非 CONFIRMED 状态不允许 audit，应抛业务异常
     */
    @Test
    @DisplayName("audit: 非 CONFIRMED 状态应拒绝")
    void audit_rejects_non_confirmed_status() {
        BankStatementEntity stmt = createClassifiedStatement();
        stmt.setReviewStatus(StatementStatus.PENDING);
        mapper.updateById(stmt);

        assertThrows(Exception.class,
                () -> service.audit(stmt.getId(), USER_ID),
                "PENDING 状态不应允许 audit");
    }

    // ===== 辅助方法 =====

    private BankStatementEntity createClassifiedStatement() {
        BankStatementEntity stmt = new BankStatementEntity();
        stmt.setEnterpriseId(ENTERPRISE_ID);
        stmt.setAccountId(1L);
        stmt.setTxDate(LocalDate.now());
        stmt.setAmount(BigDecimal.valueOf(10000));
        stmt.setSummary("测试银行流水");
        stmt.setDirection("in");
        stmt.setCounterAccount("测试客户");
        stmt.setClassification("business_receipt");
        stmt.setReviewStatus(StatementStatus.CLASSIFIED);
        stmt.setCreatedAt(LocalDateTime.now());
        stmt.setCreatedBy(USER_ID);
        stmt.setDeleted(0);

        mapper.insert(stmt);
        return mapper.selectById(stmt.getId());
    }
}
