-- ============================================================
-- V103: S-26 Agency 分支 — 业务表加 enterprise_id（第二批：应收应付/资金/发票类）
-- ============================================================

-- 业务单据
ALTER TABLE t_business_doc       ADD COLUMN IF NOT EXISTS enterprise_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE t_business_doc_entry ADD COLUMN IF NOT EXISTS enterprise_id BIGINT NOT NULL DEFAULT 1;

-- 应收应付
ALTER TABLE t_arap_settlement       ADD COLUMN IF NOT EXISTS enterprise_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE t_arap_settlement_entry ADD COLUMN IF NOT EXISTS enterprise_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE t_customer_statement    ADD COLUMN IF NOT EXISTS enterprise_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE t_bad_debt_provision    ADD COLUMN IF NOT EXISTS enterprise_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE t_bad_debt_scheme       ADD COLUMN IF NOT EXISTS enterprise_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE t_bad_debt_detail       ADD COLUMN IF NOT EXISTS enterprise_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE t_aging_alert           ADD COLUMN IF NOT EXISTS enterprise_id BIGINT NOT NULL DEFAULT 1;

-- 客户/供应商/员工
ALTER TABLE t_customer ADD COLUMN IF NOT EXISTS enterprise_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE t_vendor   ADD COLUMN IF NOT EXISTS enterprise_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE t_employee ADD COLUMN IF NOT EXISTS enterprise_id BIGINT NOT NULL DEFAULT 1;

-- 银行/资金
ALTER TABLE t_bank_account    ADD COLUMN IF NOT EXISTS enterprise_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE t_bank_statement  ADD COLUMN IF NOT EXISTS enterprise_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE t_bank_journal    ADD COLUMN IF NOT EXISTS enterprise_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE t_cash_journal    ADD COLUMN IF NOT EXISTS enterprise_id BIGINT NOT NULL DEFAULT 1;

-- 发票税务
ALTER TABLE t_output_invoice      ADD COLUMN IF NOT EXISTS enterprise_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE t_input_invoice       ADD COLUMN IF NOT EXISTS enterprise_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE t_tax_type            ADD COLUMN IF NOT EXISTS enterprise_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE t_tax_declaration     ADD COLUMN IF NOT EXISTS enterprise_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE t_tax_carry_over      ADD COLUMN IF NOT EXISTS enterprise_id BIGINT NOT NULL DEFAULT 1;

-- 应收票据
ALTER TABLE t_note_receivable ADD COLUMN IF NOT EXISTS enterprise_id BIGINT NOT NULL DEFAULT 1;

-- 费用报销
ALTER TABLE t_expense_reimbursement ADD COLUMN IF NOT EXISTS enterprise_id BIGINT NOT NULL DEFAULT 1;

-- 对账
ALTER TABLE t_reconciliation_exception  ADD COLUMN IF NOT EXISTS enterprise_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE t_reconciliation_suggestion ADD COLUMN IF NOT EXISTS enterprise_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE t_reconciliation_tolerance  ADD COLUMN IF NOT EXISTS enterprise_id BIGINT NOT NULL DEFAULT 1;

-- 索引
CREATE INDEX IF NOT EXISTS idx_t_business_doc_enterprise       ON t_business_doc(enterprise_id);
CREATE INDEX IF NOT EXISTS idx_t_arap_settlement_enterprise    ON t_arap_settlement(enterprise_id);
CREATE INDEX IF NOT EXISTS idx_t_customer_enterprise           ON t_customer(enterprise_id);
CREATE INDEX IF NOT EXISTS idx_t_vendor_enterprise             ON t_vendor(enterprise_id);
CREATE INDEX IF NOT EXISTS idx_t_employee_enterprise           ON t_employee(enterprise_id);
CREATE INDEX IF NOT EXISTS idx_t_bank_account_enterprise       ON t_bank_account(enterprise_id);
CREATE INDEX IF NOT EXISTS idx_t_bank_statement_enterprise     ON t_bank_statement(enterprise_id);
CREATE INDEX IF NOT EXISTS idx_t_bank_journal_enterprise       ON t_bank_journal(enterprise_id);
CREATE INDEX IF NOT EXISTS idx_t_cash_journal_enterprise       ON t_cash_journal(enterprise_id);
CREATE INDEX IF NOT EXISTS idx_t_output_invoice_enterprise     ON t_output_invoice(enterprise_id);
CREATE INDEX IF NOT EXISTS idx_t_input_invoice_enterprise      ON t_input_invoice(enterprise_id);
CREATE INDEX IF NOT EXISTS idx_t_expense_reimbursement_enterprise ON t_expense_reimbursement(enterprise_id);
