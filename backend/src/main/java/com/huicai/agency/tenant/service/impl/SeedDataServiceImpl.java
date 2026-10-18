package com.huicai.agency.tenant.service.impl;

import com.huicai.agency.tenant.entity.EnterpriseEntity;
import com.huicai.agency.tenant.mapper.EnterpriseMapper;
import com.huicai.agency.tenant.service.SeedDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
/**
 * 类级事务（P102/M5b，2026-10-03）：走租户表的路径必须有事务，否则
 * TenantRlsInitializer 切面不触发、app.enterprise_id 设不进去，
 * 应用以非超级用户连接时 RLS 会把本企业数据也过滤掉（读 0 行）。
 * 方法级 @Transactional 优先级更高，不受此影响。
 */
@Transactional
public class SeedDataServiceImpl implements SeedDataService {

    private final JdbcTemplate jdbcTemplate;
    private final EnterpriseMapper enterpriseMapper;

    private static final String[] SEED_TABLES = {
        "t_subject", "t_voucher_type", "t_summary_lib", "t_period",
        "t_bank_account", "t_customer", "t_voucher_template"
    };

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean cloneSeedData(Long enterpriseId) {
        return cloneSeedData(enterpriseId, false);
    }

    @Override
    public boolean cloneSeedData(Long enterpriseId, boolean force) {
        EnterpriseEntity enterprise = enterpriseMapper.selectById(enterpriseId);
        if (enterprise == null) {
            log.warn("SeedData: enterprise {} not found", enterpriseId);
            return false;
        }
        if (!force && Boolean.TRUE.equals(enterprise.getSeedDataDone())) {
            log.info("SeedData: enterprise {} already seeded, skip", enterpriseId);
            return false;
        }

        int totalRows = 0;
        for (String table : SEED_TABLES) {
            totalRows += cloneTable(table, enterpriseId);
        }

        // 克隆凭证模板分录行：需要将 template_id 从旧 ID 映射到新 ID
        totalRows += cloneTemplateLines(enterpriseId);

        if (totalRows == 0) {
            // SPEC-P126：此前此处只 warn，随后**仍会**执行 setSeedDataDone(true)，
            // 于是「一行没复制到」被标记成「已播种」；再加上开头的
            // `seedDataDone==true 即返回` 短路，该企业从此再也无法自愈。
            // 实测后果：代理端客户企业 seed_data_done=true 但 t_subject 为 0 条，
            // GET /api/v1/subjects/tree 返空，期初建账页科目下拉为空。
            // 此处必须**不置标记并返回 false**，保留重试可能。
            log.error("SeedData: enterprise {} cloned 0 rows (template may be empty) — "
                    + "not marking seedDataDone, keeping it retryable", enterpriseId);
            return false;
        }

        // 标记种子数据已初始化
        enterprise.setSeedDataDone(true);
        enterpriseMapper.updateById(enterprise);

        log.info("SeedData: cloned {} rows across {} tables for enterprise {}", totalRows, SEED_TABLES.length, enterpriseId);
        return true;
    }

    /**
     * 克隆凭证模板分录行：通过 template_code 关联新旧模板 ID，实现 template_id 重映射。
     * t_voucher_template_line 的 template_id 指向 t_voucher_template.id，
     * 而模板克隆后 id 会重新生成，因此需要按 template_code 匹配。
     */
    private int cloneTemplateLines(Long enterpriseId) {
        try {
            String sql = "INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, " +
                "cr_amount_template, summary_template, direction, assist_type, assist_required, line_order, " +
                "enterprise_id, created_at, updated_at, deleted) " +
                "SELECT " +
                "  (SELECT id FROM t_voucher_template WHERE template_code = tpl.template_code AND enterprise_id = ?), " +
                "  COALESCE(" +
                "    (SELECT id FROM t_subject WHERE code = sc.code AND enterprise_id = ?), " +
                "    ref.subject_id" +
                "  ), " +
                "  ref.dr_amount_template, ref.cr_amount_template, ref.summary_template, " +
                "  ref.direction, ref.assist_type, ref.assist_required, ref.line_order, " +
                "  ?, NOW(), NOW(), 0 " +
                "FROM t_voucher_template_line ref " +
                "JOIN t_voucher_template tpl ON tpl.id = ref.template_id " +
                "LEFT JOIN t_subject sc ON sc.id = ref.subject_id " +
                "WHERE tpl.enterprise_id = 0 AND ref.deleted = 0 " +
                "AND tpl.deleted = 0 " +
                "AND EXISTS (SELECT 1 FROM t_voucher_template WHERE template_code = tpl.template_code AND enterprise_id = ?) " +
                "ON CONFLICT DO NOTHING";

            int rows = jdbcTemplate.update(sql, enterpriseId, enterpriseId, enterpriseId, enterpriseId);
            log.info("SeedData: cloned {} template lines for enterprise {}", rows, enterpriseId);
            return rows;
        } catch (Exception e) {
            log.error("SeedData: clone template lines failed for enterprise {}: {}", enterpriseId, e.getMessage(), e);
            return 0;
        }
    }

    /**
     * 动态克隆：从模板企业(enterprise_id=0)复制数据到目标企业。
     * 通过 information_schema 动态获取表结构，避免硬编码列名。
     */
    private int cloneTable(String table, Long enterpriseId) {
        try {
            // 查询表的所有列（排除 id, version，这些由数据库自动生成）
            String colSql = "SELECT column_name FROM information_schema.columns " +
                "WHERE table_name = ? AND column_name NOT IN ('id', 'version') " +
                "ORDER BY ordinal_position";
            List<String> columns = jdbcTemplate.queryForList(colSql, String.class, table);

            if (columns.isEmpty()) {
                log.warn("SeedData: no columns found for table {}", table);
                return 0;
            }

            // 构建 INSERT 列列表和 SELECT 表达式
            List<String> colList = new ArrayList<>();
            List<String> selList = new ArrayList<>();
            for (String col : columns) {
                colList.add(col);
                if ("enterprise_id".equals(col)) {
                    selList.add(enterpriseId.toString());
                } else if ("created_at".equals(col) || "updated_at".equals(col)) {
                    selList.add("NOW()");
                } else if ("deleted".equals(col)) {
                    selList.add("0");
                } else {
                    selList.add(col);
                }
            }

            String sql = String.format(
                "INSERT INTO %s (%s) SELECT %s FROM %s WHERE enterprise_id = 0 AND deleted = 0 ON CONFLICT DO NOTHING",
                table, String.join(", ", colList), String.join(", ", selList), table
            );

            int rows = jdbcTemplate.update(sql);
            log.info("SeedData: cloned {} rows into {} for enterprise {}", rows, table, enterpriseId);
            return rows;
        } catch (Exception e) {
            log.error("SeedData: clone {} failed for enterprise {}: {}", table, enterpriseId, e.getMessage(), e);
            return 0;
        }
    }
}
