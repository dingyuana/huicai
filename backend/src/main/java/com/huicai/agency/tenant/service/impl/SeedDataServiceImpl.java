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
public class SeedDataServiceImpl implements SeedDataService {

    private final JdbcTemplate jdbcTemplate;
    private final EnterpriseMapper enterpriseMapper;

    private static final String[] SEED_TABLES = {
        "t_subject", "t_voucher_type", "t_summary_lib", "t_period",
        "t_bank_account", "t_customer"
    };

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean cloneSeedData(Long enterpriseId) {
        EnterpriseEntity enterprise = enterpriseMapper.selectById(enterpriseId);
        if (enterprise == null) {
            log.warn("SeedData: enterprise {} not found", enterpriseId);
            return false;
        }
        if (Boolean.TRUE.equals(enterprise.getSeedDataDone())) {
            log.info("SeedData: enterprise {} already seeded, skip", enterpriseId);
            return false;
        }

        int totalRows = 0;
        for (String table : SEED_TABLES) {
            totalRows += cloneTable(table, enterpriseId);
        }

        if (totalRows == 0) {
            log.warn("SeedData: no template data cloned for enterprise {} — template may be empty", enterpriseId);
        }

        // 标记种子数据已初始化
        enterprise.setSeedDataDone(true);
        enterpriseMapper.updateById(enterprise);

        log.info("SeedData: cloned {} rows across {} tables for enterprise {}", totalRows, SEED_TABLES.length, enterpriseId);
        return true;
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
