-- V140: 核销单来源单据字段 (P1 统一核销写路径)
-- execute() 改为只创建 SUBMITTED 待审批核销单，approve() 审批时统一扣款。
-- 来源单据（RECEIPT/PAYMENT）信息需落库，供审批时同步扣减来源单据余额。

ALTER TABLE t_arap_settlement
    ADD COLUMN IF NOT EXISTS source_doc_type VARCHAR(32),
    ADD COLUMN IF NOT EXISTS source_doc_id BIGINT;

COMMENT ON COLUMN t_arap_settlement.source_doc_type IS '来源单据类型: RECEIPT/PAYMENT/bank_txn 等';
COMMENT ON COLUMN t_arap_settlement.source_doc_id IS '来源单据ID（收付款业务单/银行流水）';