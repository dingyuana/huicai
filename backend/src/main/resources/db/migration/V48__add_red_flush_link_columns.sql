-- ============================================================
-- V48: 添加红冲关联字段
-- 在销项发票表中添加 reversed_by_invoice_id 字段，用于记录红字发票与蓝字发票的关联关系
-- ============================================================

ALTER TABLE t_output_invoice ADD COLUMN IF NOT EXISTS reversed_by_invoice_id BIGINT;

ALTER TABLE t_output_invoice ADD CONSTRAINT fk_output_invoice_reversed_by 
    FOREIGN KEY (reversed_by_invoice_id) REFERENCES t_output_invoice(id);

COMMENT ON COLUMN t_output_invoice.reversed_by_invoice_id IS '被哪张红字发票红冲（指向红字发票ID）';

-- 同时添加 original_invoice_no 字段，用于红字发票记录原蓝字发票号码
ALTER TABLE t_output_invoice ADD COLUMN IF NOT EXISTS original_invoice_no VARCHAR(64);

COMMENT ON COLUMN t_output_invoice.original_invoice_no IS '原蓝字发票号码（红字发票专用）';
