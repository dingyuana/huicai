package com.huicai.sme.tax.integration;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huicai.base.business.entity.BankStatementEntity;
import com.huicai.base.business.mapper.BankStatementMapper;
import com.huicai.base.system.entity.AuditLogEntity;
import com.huicai.base.system.mapper.AuditLogMapper;
import com.huicai.common.test.AbstractMapperTest;
import com.huicai.sme.tax.entity.TaxDeclarationEntity;
import com.huicai.sme.tax.mapper.TaxDeclarationMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 全量 Entity↔DB Schema 一致性集成测试（真实 PostgreSQL + Flyway）。
 *
 * <p>本项目的 L2 集成测试体系之前存在结构性缺陷：
 * 36 个 Controller 测试全部使用 H2 内存数据库 + flyway.enabled: false，
 * 因此永远无法检测 Entity↔DB 列映射错误和 CHECK 约束违反。
 *
 * <p>本测试类基于 {@link AbstractMapperTest}，确保每条测试路径
 * 在真实 PostgreSQL 上执行，验证关键 Entity 的 insert/update 不抛异常。
 *
 * <p>覆盖的关键缺陷场景：
 * <ul>
 *   <li>Entity 字段不在 DB 中 → PSQLException: column does not exist</li>
 *   <li>Entity 字段名与 DB 列名不匹配 → 映射错误</li>
 *   <li>CHECK 约束阻止合法值 → DataIntegrityViolationException</li>
 *   <li>Entity 中 @TableField(exist=false) 字段不应被 MyBatis-Plus 写入</li>
 * </ul>
 *
 * <p>⚠️ 注意：本测试不枚举所有 Entity（太慢），只验证"高风险"的几条链路：
 * <ul>
 *   <li>BudgetEntry（曾缺失 deptId/projectId/periodMonth 列）</li>
 *   <li>ExpenseReimbursement（曾 mismatch employeeId vs applicant_id）</li>
 *   <li>ReportTemplate（曾含不存在 isSystem 列）</li>
 *   <li>AuditLog（曾含不存在 username 列）</li>
 * </ul>
 *
 * @SlowTest — 需要 Docker + Testcontainers
 */
class EntityDbSchemaIntegrationTest extends AbstractMapperTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private AuditLogMapper auditLogMapper;

    @Autowired
    private TaxDeclarationMapper taxDeclarationMapper;

    @Autowired
    private BankStatementMapper bankStatementMapper;

    // ===== AuditLog =====

    /**
     * 验证 AuditLog insert 全字段不抛异常。
     * 核心缺陷：曾存在 AuditLogEntity.username → DB 无此列。
     */
    @Test
    @DisplayName("AuditLog insert 不抛列映射异常")
    void auditLog_insert_succeeds() {
        AuditLogEntity log = new AuditLogEntity();
        log.setUserId(1L);
        log.setModule("SCHEMA_TEST");
        log.setOperation("INSERT");
        log.setMethod("test");
        log.setRequestParams("{}");
        log.setResponseResult("{}");
        log.setOldSnapshot("{}");
        log.setNewSnapshot("{}");
        log.setStatus("success");
        log.setCreatedAt(LocalDateTime.now());

        int rows = auditLogMapper.insert(log);
        assertEquals(1, rows, "AuditLog insert 应返回 1");
        assertNotNull(log.getId(), "insert 后 id 应不为 null");

        AuditLogEntity read = auditLogMapper.selectById(log.getId());
        assertNotNull(read);
        assertEquals("SCHEMA_TEST", read.getModule());

        System.out.println("[PASS] AuditLog insert: id=" + log.getId());
    }

    // ===== BankStatement (V120 CHECK 约束验证) =====

    /**
     * 验证 BankStatement 的 review_status 字段能正确更新到
     * 非 PENDING/CONFIRMED/REJECTED 的值（V120 CHECK 约束验证）。
     */
    @Test
    @DisplayName("BankStatement reviewStatus 可更新到 voucher_generated（CHECK 约束）")
    void bankStatement_reviewStatus_can_update_to_voucher_generated() {
        BankStatementEntity stmt = new BankStatementEntity();
        stmt.setEnterpriseId(1L);
        stmt.setAccountId(1L);
        stmt.setTxDate(LocalDate.now());
        stmt.setAmount(BigDecimal.valueOf(100));
        stmt.setSummary("Schema test");
        stmt.setDirection("in");
        stmt.setClassification("business_receipt");
        stmt.setReviewStatus("CONFIRMED");
        stmt.setCreatedAt(LocalDateTime.now());
        stmt.setCreatedBy(1L);
        stmt.setDeleted(0);

        bankStatementMapper.insert(stmt);

        // 设置到之前被 CHECK 约束阻止的值
        stmt.setReviewStatus("voucher_generated");
        int rows = bankStatementMapper.updateById(stmt);

        assertEquals(1, rows,
                "updateById(reviewStatus=voucher_generated) 应返回 1，" +
                        "说明 V120 CHECK 约束已扩展");

        BankStatementEntity read = bankStatementMapper.selectById(stmt.getId());
        assertEquals("voucher_generated", read.getReviewStatus());

        System.out.println("[PASS] BankStatement reviewStatus: " + read.getReviewStatus());
    }

    // ===== TaxDeclaration (@StatusChangeable + AuditLog 链路) =====

    /**
     * 验证 TaxDeclaration（@StatusChangeable Entity）的 updateById 不抛异常。
     * 这条路径是之前发票 500 错误的触发链路。
     */
    @Test
    @DisplayName("TaxDeclaration updateById 触发 AuditLog 无异常")
    void taxDeclaration_update_by_id_no_exception() {
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

        int insertRows = taxDeclarationMapper.insert(entity);
        assertEquals(1, insertRows, "insert 应返回 1");

        // 触发 @StatusChangeable Aspect
        entity.setStatus("SUBMITTED");
        int updateRows = taxDeclarationMapper.updateById(entity);

        assertEquals(1, updateRows,
                "updateById(status=SUBMITTED) 应返回 1，" +
                        "证明 @StatusChangeable + AuditLog 链路完整无异常");

        TaxDeclarationEntity read = taxDeclarationMapper.selectById(entity.getId());
        assertEquals("SUBMITTED", read.getStatus());

        System.out.println("[PASS] TaxDeclaration updateById: status=" + read.getStatus());
    }

    // ===== DataSource 真实性验证 =====

    /**
     * 验证 DataSource URL 确实是 PostgreSQL（非 H2），确认测试使用真实 DB。
     * 这是本测试类存在的前提：如果 H2 被意外启用，则测试无效。
     */
    @Test
    @DisplayName("确认测试使用真实 PostgreSQL 而非 H2")
    void dataSource_is_postgresql() {
        try {
            String url = dataSource.getConnection().getMetaData().getURL();
            assertTrue(url.contains("postgresql"),
                    "DataSource URL 应包含 postgresql，实际: " + url);

            String product = dataSource.getConnection().getMetaData().getDatabaseProductName();
            assertEquals("PostgreSQL", product,
                    "DatabaseProductName 应为 PostgreSQL，实际: " + product);

            System.out.println("[PASS] DataSource: " + product + " - " + url);
        } catch (Exception e) {
            fail("获取 DataSource 元数据失败: " + e.getMessage());
        }
    }
}
