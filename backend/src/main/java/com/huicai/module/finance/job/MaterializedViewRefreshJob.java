package com.huicai.module.finance.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 物化视图定时刷新任务.
 * <p>
 * 每天凌晨 2:00 刷新报表物化视图，确保报表数据最新。
 * 使用 CONCURRENTLY 选项避免刷新期间阻塞查询。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MaterializedViewRefreshJob {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 每天凌晨 2:00 刷新物化视图.
     * <p>
     * REFRESH MATERIALIZED VIEW CONCURRENTLY 需要物化视图有唯一索引,
     * V11 迁移已为 mv_subject_balance 和 mv_period_voucher 创建唯一索引。
     * </p>
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void refreshMaterializedViews() {
        refreshView("mv_subject_balance");
        refreshView("mv_period_voucher");
    }

    private void refreshView(String viewName) {
        try {
            String sql = "REFRESH MATERIALIZED VIEW CONCURRENTLY " + viewName;
            jdbcTemplate.execute(sql);
            log.info("物化视图刷新成功: {}", viewName);
        } catch (Exception e) {
            log.error("物化视图刷新失败: {}, error={}", viewName, e.getMessage());
            // 降级: 尝试普通刷新（不加 CONCURRENTLY，会短暂阻塞查询）
            try {
                String fallbackSql = "REFRESH MATERIALIZED VIEW " + viewName;
                jdbcTemplate.execute(fallbackSql);
                log.info("物化视图降级刷新成功: {}", viewName);
            } catch (Exception fallbackEx) {
                log.error("物化视图降级刷新也失败: {}, error={}", viewName, fallbackEx.getMessage());
            }
        }
    }
}