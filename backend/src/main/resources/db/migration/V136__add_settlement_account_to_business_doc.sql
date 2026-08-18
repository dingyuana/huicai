-- V136: 业务单据增加结算账户ID（银行/现金账户），用于记录付款/收款通过哪个账户结算
ALTER TABLE t_business_doc ADD COLUMN IF NOT EXISTS settlement_account_id BIGINT;
COMMENT ON COLUMN t_business_doc.settlement_account_id IS '结算账户ID（银行/现金账户）';
CREATE INDEX IF NOT EXISTS idx_business_doc_settlement_account_id ON t_business_doc(settlement_account_id);