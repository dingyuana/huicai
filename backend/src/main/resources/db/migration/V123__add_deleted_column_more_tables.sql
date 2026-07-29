-- ============================================================
-- V123: 补充更多继承 BaseEntity 但缺少 deleted 列的表（9 张）
--
-- 背景：V122 只覆盖了 V94 中的 6 张表，但仍有多张表
-- 的 Entity 继承 BaseEntity（带 @TableLogic），表中却缺少 deleted 列。
-- MyBatis-Plus 自动注入 AND deleted = 0 导致 500 错误。
-- ============================================================

-- 1. t_ai_feedback_log
ALTER TABLE t_ai_feedback_log ADD COLUMN IF NOT EXISTS deleted INT NOT NULL DEFAULT 0;

-- 2. t_subject_balance
ALTER TABLE t_subject_balance ADD COLUMN IF NOT EXISTS deleted INT NOT NULL DEFAULT 0;

-- 3. t_arap_settlement_entry
ALTER TABLE t_arap_settlement_entry ADD COLUMN IF NOT EXISTS deleted INT NOT NULL DEFAULT 0;

-- 4. t_business_doc_entry
ALTER TABLE t_business_doc_entry ADD COLUMN IF NOT EXISTS deleted INT NOT NULL DEFAULT 0;

-- 5. t_aging_alert
ALTER TABLE t_aging_alert ADD COLUMN IF NOT EXISTS deleted INT NOT NULL DEFAULT 0;

-- 6. t_reconciliation_tolerance
ALTER TABLE t_reconciliation_tolerance ADD COLUMN IF NOT EXISTS deleted INT NOT NULL DEFAULT 0;

-- 7. t_asset_depreciation
ALTER TABLE t_asset_depreciation ADD COLUMN IF NOT EXISTS deleted INT NOT NULL DEFAULT 0;

-- 8. t_asset_inventory_entry
ALTER TABLE t_asset_inventory_entry ADD COLUMN IF NOT EXISTS deleted INT NOT NULL DEFAULT 0;

-- 9. t_ticket_transaction
ALTER TABLE t_ticket_transaction ADD COLUMN IF NOT EXISTS deleted INT NOT NULL DEFAULT 0;