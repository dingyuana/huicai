-- V65: 销项发票补充应收单编号字段
-- 日期: 2026-06-28
-- 说明: 补全发票 -> 应收单双向追溯字段

-- 补充 receivable_id 字段（应收单ID）
ALTER TABLE t_output_invoice
ADD COLUMN receivable_id BIGINT;
COMMENT ON COLUMN t_output_invoice.receivable_id IS '关联应收单ID';

-- 补充 receivable_no 字段（应收单编号，冗余存储）
ALTER TABLE t_output_invoice
ADD COLUMN receivable_no VARCHAR(64);
COMMENT ON COLUMN t_output_invoice.receivable_no IS '关联应收单编号';

-- 创建索引
CREATE INDEX idx_output_invoice_receivable_id ON t_output_invoice(receivable_id);
CREATE INDEX idx_output_invoice_receivable_no ON t_output_invoice(receivable_no);
