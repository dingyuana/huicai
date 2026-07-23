package com.huicai.agency.tenant.service.impl;

import com.huicai.agency.tenant.entity.EnterpriseEntity;
import com.huicai.agency.tenant.mapper.EnterpriseMapper;
import com.huicai.agency.tenant.service.SeedDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeedDataServiceImpl implements SeedDataService {

    private final JdbcTemplate jdbcTemplate;
    private final EnterpriseMapper enterpriseMapper;

    private static final String[] SEED_TABLES = {
        "t_subject", "t_voucher_type", "t_summary_lib", "t_period"
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

        for (String table : SEED_TABLES) {
            cloneTable(table, enterpriseId);
        }

        // 标记种子数据已初始化
        enterprise.setSeedDataDone(true);
        enterpriseMapper.updateById(enterprise);

        log.info("SeedData: cloned {} tables for enterprise {}", SEED_TABLES.length, enterpriseId);
        return true;
    }

    private void cloneTable(String table, Long enterpriseId) {
        // 从模板数据（enterprise_id=0）克隆到目标企业
        String sql = String.format(
            "INSERT INTO %s (enterprise_id, code, name, level, parent_id, status, " +
            "created_by, created_at, updated_by, updated_at, deleted, version) " +
            "SELECT %d, code, name, level, parent_id, status, " +
            "created_by, NOW(), updated_by, NOW(), 0, 1 " +
            "FROM %s WHERE enterprise_id = 0 AND deleted = 0 " +
            "ON CONFLICT DO NOTHING",
            table, enterpriseId, table
        );

        try {
            int rows = jdbcTemplate.update(sql);
            log.debug("SeedData: cloned {} rows into {} for enterprise {}", rows, table, enterpriseId);
        } catch (Exception e) {
            log.warn("SeedData: clone {} failed for enterprise {}: {}", table, enterpriseId, e.getMessage());
        }
    }
}
