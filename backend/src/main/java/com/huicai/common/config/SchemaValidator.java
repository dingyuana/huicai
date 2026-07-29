package com.huicai.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 启动时自动检查 DB schema 与 Entity 注解一致性。
 *
 * <p>当前检查项：
 * <ul>
 *   <li>所有 t_ 前缀表的 id 列必须为 GENERATED ALWAYS AS IDENTITY
 *       （与 @TableId(type = IdType.AUTO) 对齐，否则 INSERT 时抛 not-null 异常）
 *   <li>所有存在 enterprise_id 列的业务表必须也有 deleted 列
 *       （与 BaseEntity 的 @TableLogic 对齐，否则 SELECT 时抛 column not found 异常）
 * </ul>
 *
 * <p>误报处理：如果某张表确实不需要 IDENTITY 或 deleted，在对应的 SKIP_* 列表中添加表名。
 */
@Slf4j
@Component
public class SchemaValidator implements CommandLineRunner {

    /**
     * 白名单：明确不需要 IDENTITY 的表（如有）。
     */
    private static final List<String> SKIP_TABLES = List.of();

    /**
     * 白名单：明确不需要 deleted 列的表（如有，如中间表、纯配置表）。
     * 这些表有 enterprise_id（来自 V103 批量迁移），但无 Entity 继承 BaseEntity，
     * 因此不受 @TableLogic 影响，不需要 deleted 列。
     */
    private static final List<String> SKIP_DELETED_TABLES = List.of(
        "t_voucher_cash_flow",           // 现金流量中间表，无独立 Entity
        "t_agency_enterprise",           // 代理企业关联表，无独立 Entity 继承 BaseEntity
        "t_bad_debt_detail",             // 坏账明细表，无 Entity 继承 BaseEntity
        "t_reconciliation_suggestion",   // 核销建议表，无 Entity 继承 BaseEntity
        "t_tax_carry_over"               // 税金结转记录表，无 Entity 继承 BaseEntity
    );

    private final JdbcTemplate jdbcTemplate;

    public SchemaValidator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        boolean hasError = false;
        StringBuilder report = new StringBuilder("\n====== Schema Validation ======\n");

        report.append("--- Check 1: id 列 IDENTITY ---\n");
        hasError |= checkIdIdentity(report);

        report.append("--- Check 2: deleted 列存在性 ---\n");
        hasError |= checkDeletedColumn(report);

        if (hasError) {
            report.append("==============================\n");
            report.append("  ACTION REQUIRED: 以上问题需要创建 Flyway migration 修复后重启应用。\n");
            report.append("==============================\n");
            log.error(report.toString());
            throw new IllegalStateException(
                "Schema validation failed. 请查看上方日志了解具体问题。\n" +
                "在 AGENTS.md 中查看 §4.2 Entity-DB 不一致陷阱，\n" +
                "创建 Flyway migration 执行修复后重启应用。"
            );
        } else {
            report.append("==============================\n");
            log.info(report.toString());
        }
    }

    /**
     * 检查所有 t_ 表的 id 列是否均为 GENERATED ALWAYS AS IDENTITY。
     */
    private boolean checkIdIdentity(StringBuilder report) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT table_name, is_identity FROM information_schema.columns " +
            "WHERE table_schema = 'public' AND column_name = 'id' " +
            "  AND table_name LIKE 't_%' " +
            "ORDER BY table_name"
        );

        boolean hasError = false;
        for (Map<String, Object> row : rows) {
            String table = (String) row.get("table_name");
            String identity = (String) row.get("is_identity");

            if (SKIP_TABLES.contains(table)) {
                continue;
            }

            if (!"YES".equals(identity)) {
                hasError = true;
                report.append("  ❌ ").append(table)
                      .append(".id → is_identity=").append(identity)
                      .append("\n      Fix SQL: ALTER TABLE ")
                      .append(table)
                      .append(" ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY;")
                      .append("\n");
            }
        }

        if (!hasError) {
            report.append("  ✅ All ").append(rows.size()).append(" t_ tables have id IDENTITY\n");
        }
        return hasError;
    }

    /**
     * 检查所有存在 enterprise_id 列的业务表是否也有 deleted 列。
     * <p>
     * 判断依据：BaseEntity 同时定义了 enterpriseId（多租户）和 deleted（逻辑删除），
     * 因此继承 BaseEntity 的业务表应当同时拥有 enterprise_id 和 deleted 两列。
     * enterprise_id 作为"该表继承 BaseEntity"的代理标识。
     * </p>
     */
    private boolean checkDeletedColumn(StringBuilder report) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT DISTINCT c.table_name FROM information_schema.columns c " +
            "WHERE c.table_schema = 'public' " +
            "  AND c.table_name LIKE 't_%' " +
            "  AND c.column_name = 'enterprise_id' " +
            "  AND NOT EXISTS (" +
            "    SELECT 1 FROM information_schema.columns c2 " +
            "    WHERE c2.table_schema = 'public' " +
            "      AND c2.table_name = c.table_name " +
            "      AND c2.column_name = 'deleted'" +
            "  ) " +
            "ORDER BY c.table_name"
        );

        boolean hasError = false;
        int count = 0;
        for (Map<String, Object> row : rows) {
            String table = (String) row.get("table_name");
            if (SKIP_DELETED_TABLES.contains(table)) {
                continue;
            }
            hasError = true;
            count++;
            report.append("  ❌ ").append(table)
                  .append(" 有 enterprise_id 列（继承 BaseEntity）但缺少 deleted 列\n")
                  .append("      Fix SQL: ALTER TABLE ").append(table)
                  .append(" ADD COLUMN IF NOT EXISTS deleted INT NOT NULL DEFAULT 0;\n");
        }

        if (!hasError) {
            int total = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT c.table_name) FROM information_schema.columns c " +
                "WHERE c.table_schema = 'public' AND c.table_name LIKE 't_%' AND c.column_name = 'enterprise_id'",
                Integer.class
            );
            report.append("  ✅ All ").append(total).append(" enterprise-scoped tables have deleted column\n");
        }
        return hasError;
    }
}
