-- V72: 核销结算表增加 business_doc_id 关联字段
-- 用途: 核销结算系统改为直接操作 BusinessDocEntity（P34 M3）

-- 1. t_arap_settlement_entry 增加 business_doc_id（Entity 已有字段，补迁移）
ALTER TABLE t_arap_settlement_entry ADD COLUMN IF NOT EXISTS business_doc_id BIGINT REFERENCES t_business_doc(id);
COMMENT ON COLUMN t_arap_settlement_entry.business_doc_id IS '关联业务单据ID（P34 替代 receivableId/payableId）';
CREATE INDEX IF NOT EXISTS idx_arap_settlement_entry_business_doc_id
    ON t_arap_settlement_entry(business_doc_id);

-- 2. t_reconciliation_log 增加 target_business_doc_id
ALTER TABLE t_reconciliation_log ADD COLUMN IF NOT EXISTS target_business_doc_id BIGINT REFERENCES t_business_doc(id);
COMMENT ON COLUMN t_reconciliation_log.target_business_doc_id IS '目标业务单据ID（P34 替代 target_doc_id 指向 receivable/payable）';
CREATE INDEX IF NOT EXISTS idx_reconciliation_log_target_business_doc_id
    ON t_reconciliation_log(target_business_doc_id);