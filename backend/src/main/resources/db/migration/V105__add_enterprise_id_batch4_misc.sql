-- ============================================================
-- V105: S-26 Agency 分支 — 业务表加 enterprise_id（第四批：补充 V4/V5/V94 遗漏表）
-- ============================================================

-- V4 表
ALTER TABLE t_ticket             ADD COLUMN IF NOT EXISTS enterprise_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE t_ticket_transaction ADD COLUMN IF NOT EXISTS enterprise_id BIGINT NOT NULL DEFAULT 1;

-- V5 表
ALTER TABLE t_prepayment ADD COLUMN IF NOT EXISTS enterprise_id BIGINT NOT NULL DEFAULT 1;

-- V94 表
ALTER TABLE t_ai_anomaly_tag              ADD COLUMN IF NOT EXISTS enterprise_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE t_cash_flow_rule              ADD COLUMN IF NOT EXISTS enterprise_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE t_financial_metric            ADD COLUMN IF NOT EXISTS enterprise_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE t_attachment                  ADD COLUMN IF NOT EXISTS enterprise_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE t_bad_debt_provision_detail   ADD COLUMN IF NOT EXISTS enterprise_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE t_bad_debt_provision_scheme   ADD COLUMN IF NOT EXISTS enterprise_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE t_bad_debt_provision_scheme_item ADD COLUMN IF NOT EXISTS enterprise_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE t_reconciliation_dispute      ADD COLUMN IF NOT EXISTS enterprise_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE t_reconciliation_outstanding  ADD COLUMN IF NOT EXISTS enterprise_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE t_purchase_return             ADD COLUMN IF NOT EXISTS enterprise_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE t_reconciliation_log          ADD COLUMN IF NOT EXISTS enterprise_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE t_budget_adjustment           ADD COLUMN IF NOT EXISTS enterprise_id BIGINT NOT NULL DEFAULT 1;

-- 索引
CREATE INDEX IF NOT EXISTS idx_t_ticket_enterprise              ON t_ticket(enterprise_id);
CREATE INDEX IF NOT EXISTS idx_t_prepayment_enterprise          ON t_prepayment(enterprise_id);
CREATE INDEX IF NOT EXISTS idx_t_attachment_enterprise          ON t_attachment(enterprise_id);
CREATE INDEX IF NOT EXISTS idx_t_purchase_return_enterprise     ON t_purchase_return(enterprise_id);
CREATE INDEX IF NOT EXISTS idx_t_reconciliation_log_enterprise  ON t_reconciliation_log(enterprise_id);
CREATE INDEX IF NOT EXISTS idx_t_budget_adjustment_enterprise   ON t_budget_adjustment(enterprise_id);
