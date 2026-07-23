-- ============================================================
-- V104: S-26 Agency 分支 — 业务表加 enterprise_id（第三批：资产/预算/报表/AI/其他）
-- ============================================================

-- 固定资产
ALTER TABLE t_asset_card         ADD COLUMN IF NOT EXISTS enterprise_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE t_asset_category     ADD COLUMN IF NOT EXISTS enterprise_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE t_asset_change       ADD COLUMN IF NOT EXISTS enterprise_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE t_asset_depreciation ADD COLUMN IF NOT EXISTS enterprise_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE t_asset_disposal     ADD COLUMN IF NOT EXISTS enterprise_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE t_asset_inventory    ADD COLUMN IF NOT EXISTS enterprise_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE t_asset_inventory_entry ADD COLUMN IF NOT EXISTS enterprise_id BIGINT NOT NULL DEFAULT 1;

-- 预算
ALTER TABLE t_budget            ADD COLUMN IF NOT EXISTS enterprise_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE t_budget_entry      ADD COLUMN IF NOT EXISTS enterprise_id BIGINT NOT NULL DEFAULT 1;

-- 报表
ALTER TABLE t_report_template ADD COLUMN IF NOT EXISTS enterprise_id BIGINT NOT NULL DEFAULT 1;

-- AI
ALTER TABLE t_ai_task          ADD COLUMN IF NOT EXISTS enterprise_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE t_ai_feedback_log  ADD COLUMN IF NOT EXISTS enterprise_id BIGINT NOT NULL DEFAULT 1;

-- 银行流水分类规则
ALTER TABLE t_classification_rule ADD COLUMN IF NOT EXISTS enterprise_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE t_account_mapping_rule ADD COLUMN IF NOT EXISTS enterprise_id BIGINT NOT NULL DEFAULT 1;

-- 索引
CREATE INDEX IF NOT EXISTS idx_t_asset_card_enterprise         ON t_asset_card(enterprise_id);
CREATE INDEX IF NOT EXISTS idx_t_asset_category_enterprise     ON t_asset_category(enterprise_id);
CREATE INDEX IF NOT EXISTS idx_t_asset_depreciation_enterprise ON t_asset_depreciation(enterprise_id);
CREATE INDEX IF NOT EXISTS idx_t_asset_disposal_enterprise     ON t_asset_disposal(enterprise_id);
CREATE INDEX IF NOT EXISTS idx_t_asset_inventory_enterprise    ON t_asset_inventory(enterprise_id);
CREATE INDEX IF NOT EXISTS idx_t_budget_enterprise             ON t_budget(enterprise_id);
CREATE INDEX IF NOT EXISTS idx_t_report_template_enterprise    ON t_report_template(enterprise_id);
CREATE INDEX IF NOT EXISTS idx_t_ai_task_enterprise            ON t_ai_task(enterprise_id);
CREATE INDEX IF NOT EXISTS idx_t_classification_rule_enterprise ON t_classification_rule(enterprise_id);
