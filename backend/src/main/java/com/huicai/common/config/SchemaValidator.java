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
 * </ul>
 *
 * <p>误报处理：如果某张表确实不需要 IDENTITY，在 {@link #SKIP_TABLES} 中添加表名。
 */
@Slf4j
@Component
public class SchemaValidator implements CommandLineRunner {

    /**
     * 白名单：明确不需要 IDENTITY 的表（如有）。
     * 当前为空，59 张实体表全部使用 IdType.AUTO。
     */
    private static final List<String> SKIP_TABLES = List.of();

    private final JdbcTemplate jdbcTemplate;

    public SchemaValidator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT table_name, is_identity FROM information_schema.columns " +
            "WHERE table_schema = 'public' AND column_name = 'id' " +
            "  AND table_name LIKE 't_%' " +
            "ORDER BY table_name"
        );

        boolean hasError = false;
        StringBuilder report = new StringBuilder("\n====== Schema Validation ======\n");

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

        if (hasError) {
            report.append("==============================\n");
            report.append("  ACTION REQUIRED: 以上表的 id 列缺少 IDENTITY,\n");
            report.append("  创建新 migration 执行修复后重启应用。\n");
            report.append("==============================\n");
            log.error(report.toString());
            throw new IllegalStateException(
                "Schema validation failed: t_ 表的 id 列缺少 IDENTITY 属性。\n" +
                report.toString() +
                "\n" +
                "Entity 使用 @TableId(type = IdType.AUTO) 要求数据库自增，\n" +
                "缺失 IDENTITY 会导致 INSERT 时 'null value in column \"id\" violates not-null constraint'。\n" +
                "请创建 Flyway migration 执行修复后重启应用。"
            );
        } else {
            report.append("  ✅ All ").append(rows.size()).append(" t_ tables have id IDENTITY\n");
            report.append("==============================\n");
            log.info(report.toString());
        }
    }
}
