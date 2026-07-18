-- V71: 为 t_business_doc 和 t_voucher 添加外键关联字段
-- 用途: 三层单据间通过外键直接关联，替代字符串编号查询

-- 1. 业务单据增加 invoice_id（指向销售发票）
ALTER TABLE t_business_doc ADD COLUMN IF NOT EXISTS invoice_id BIGINT REFERENCES t_output_invoice(id);
COMMENT ON COLUMN t_business_doc.invoice_id IS '关联销售发票ID（P1 新增）';
CREATE INDEX IF NOT EXISTS idx_business_doc_invoice_id
    ON t_business_doc(invoice_id);

-- 2. 凭证增加 business_doc_id（指向业务单据）
ALTER TABLE t_voucher ADD COLUMN IF NOT EXISTS business_doc_id BIGINT REFERENCES t_business_doc(id);
COMMENT ON COLUMN t_voucher.business_doc_id IS '关联业务单据ID（P1 新增）';
CREATE INDEX IF NOT EXISTS idx_voucher_business_doc_id
    ON t_voucher(business_doc_id);
