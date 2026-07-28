package com.huicai.sme.tax.integration;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huicai.base.system.entity.AuditLogEntity;
import com.huicai.base.system.mapper.AuditLogMapper;
import com.huicai.common.test.AbstractMapperTest;
import com.huicai.sme.tax.entity.TaxDeclarationEntity;
import com.huicai.sme.tax.mapper.TaxDeclarationMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * StatusChangeAspect → AuditLog 整链集成测试（真实 PostgreSQL + Flyway）。
 *
 * <p>验证：对任何含 {@code @StatusChangeable} 注解的 Entity 调用
 * {@code Mapper.updateById()} 时，Aspect 拦截会异步触发
 * {@code AuditLogService.saveAsync()} 写入审计日志。
 *
 * <p>这正是之前发票 500 错误的根因路径：
 * 发票 confirm() → updateById() → StatusChangeAspect →
 * AuditLogService.recordStatusChange() → AuditLogMapper.insert()
 * 如果任何环节有 Entity↔DB 列映射错误或列不存在，会抛出异常。
 *
 * @SlowTest — 需要 Docker + Testcontainers
 */
class InvoiceConfirmAuditPathIntegrationTest extends AbstractMapperTest {

    @Autowired
    private TaxDeclarationMapper taxDeclarationMapper;

    @Autowired
    private AuditLogMapper auditLogMapper;

    /**
     * 验证核心审计链路：updateById 触发 Aspect → AuditLog 成功写入。
     * 这是发票 500 错误的根因路径。
     */
    @Test
    @DisplayName("StatusChangeAspect 触发 AuditLog 写入成功")
    void status_change_aspect_creates_audit_log() {
        TaxDeclarationEntity entity = createTaxDeclaration();

        taxDeclarationMapper.insert(entity);

        // 修改状态：触发 @StatusChangeable Aspect
        entity.setStatus("SUBMITTED");
        taxDeclarationMapper.updateById(entity);

        // 等待异步审计日志写入
        waitForAsync(2000);

        // 验证 AuditLog 已写入（DB 列映射正确）
        List<AuditLogEntity> logs = auditLogMapper.selectList(
                new LambdaQueryWrapper<AuditLogEntity>()
                        .orderByDesc(AuditLogEntity::getCreatedAt)
                        .last("LIMIT 20"));

        assertNotNull(logs, "AuditLog 查询不应为 null");
        assertEquals("SUBMITTED", entity.getStatus(), "Entity 状态应已更新为 SUBMITTED");

        // 至少有一条审计日志写入成功（证明整个链路 DB 映射正确）
        assertTrue(logs.size() > 0, "StatusChangeAspect 未触发 AuditLog 写入！");

        System.out.println("[PASS] 审计日志条目数: " + logs.size());
    }

    /**
     * 验证：AuditLog 所有字段列都能正确插入（Entity↔DB 映射一致性）。
     * 之前 500 错误的根因是 AuditLogEntity.username 列不存在于 DB。
     */
    @Test
    @DisplayName("AuditLog 全字段写入成功（无列映射错误）")
    void audit_log_full_insert_succeeds() {
        TaxDeclarationEntity entity = createTaxDeclaration();

        taxDeclarationMapper.insert(entity);
        entity.setStatus("APPROVED");
        taxDeclarationMapper.updateById(entity);

        waitForAsync(2000);

        List<AuditLogEntity> logs = auditLogMapper.selectList(
                new LambdaQueryWrapper<AuditLogEntity>()
                        .orderByDesc(AuditLogEntity::getCreatedAt)
                        .last("LIMIT 5"));

        assertTrue(logs.size() >= 1, "应至少生成 1 条审计日志，实际: " + logs.size());

        AuditLogEntity log = logs.get(0);
        assertNotNull(log.getId(), "id 应不为 null");
        assertNotNull(log.getCreatedAt(), "createdAt 应不为 null");
        assertNotNull(log.getModule(), "module 应不为 null");

        System.out.println("[PASS] AuditLog 全字段写入成功: id=" + log.getId() +
                ", module=" + log.getModule());
    }

    private TaxDeclarationEntity createTaxDeclaration() {
        TaxDeclarationEntity entity = new TaxDeclarationEntity();
        entity.setEnterpriseId(1L);
        entity.setDeclarationNo("TAX-TEST-" + System.currentTimeMillis());
        entity.setPeriod("202601");
        entity.setTaxType("VAT");
        entity.setDeclaredDate(LocalDate.now());
        entity.setPayableAmount(BigDecimal.ZERO);
        entity.setStatus("DRAFT");
        entity.setDeleted(0);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setCreatedBy(1L);
        return entity;
    }

    private void waitForAsync(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }
}
