ALTER TABLE t_arap_settlement_entry
ADD COLUMN before_balance NUMERIC(18,2) DEFAULT 0;

ALTER TABLE t_arap_settlement_entry
ADD COLUMN after_balance NUMERIC(18,2) DEFAULT 0;

ALTER TABLE t_reconciliation_log
ADD COLUMN operation_type VARCHAR(20) DEFAULT 'CREATE';

ALTER TABLE t_reconciliation_log
ADD COLUMN rule_id VARCHAR(50);
