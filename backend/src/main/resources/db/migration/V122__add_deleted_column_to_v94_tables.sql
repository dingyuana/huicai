-- ============================================================
-- V122: 补充 V94 建表时遗漏的 deleted 列（6 张表）
--
-- 背景：V94 创建了 14 张表，但其中 6 张缺少 deleted 列。
-- 这些表的 Entity 均继承 BaseEntity（带 @TableLogic），
-- MyBatis-Plus 自动注入 AND deleted = 0 导致 500 错误。
-- ============================================================

-- 1. t_ai_anomaly_tag
ALTER TABLE t_ai_anomaly_tag ADD COLUMN IF NOT EXISTS deleted INT NOT NULL DEFAULT 0;

-- 2. t_bad_debt_provision_detail
ALTER TABLE t_bad_debt_provision_detail ADD COLUMN IF NOT EXISTS deleted INT NOT NULL DEFAULT 0;

-- 3. t_bad_debt_provision_scheme_item
ALTER TABLE t_bad_debt_provision_scheme_item ADD COLUMN IF NOT EXISTS deleted INT NOT NULL DEFAULT 0;

-- 4. t_reconciliation_dispute
ALTER TABLE t_reconciliation_dispute ADD COLUMN IF NOT EXISTS deleted INT NOT NULL DEFAULT 0;

-- 5. t_reconciliation_log
ALTER TABLE t_reconciliation_log ADD COLUMN IF NOT EXISTS deleted INT NOT NULL DEFAULT 0;

-- 6. t_budget_entry
ALTER TABLE t_budget_entry ADD COLUMN IF NOT EXISTS deleted INT NOT NULL DEFAULT 0;