-- V51: 修复 t_output_invoice 缺少 reversed_by_invoice_id 和 original_invoice_no 列
-- 2026-06-25
-- 背景: OutputInvoiceEntity.reversedByInvoiceId 字段用于红冲关联(指向红字发票 ID),
-- OutputInvoiceEntity.originalInvoiceNo 字段用于红字发票记录原蓝字号码，
-- 但 V8 建表时未创建这些列, V46 状态迁移也未补建.
-- 后果: 所有 SELECT t_output_invoice 的查询报 "column does not exist", 导致
-- 销售发票导入/红冲关联/批量关联等所有接口 500.
-- 修复: 添加 BIGINT/VARCHAR 列 + 索引.

ALTER TABLE t_output_invoice
    ADD COLUMN IF NOT EXISTS reversed_by_invoice_id BIGINT,
    ADD COLUMN IF NOT EXISTS original_invoice_no VARCHAR(64);

COMMENT ON COLUMN t_output_invoice.reversed_by_invoice_id IS '被哪张红字发票红冲(指向红字发票 ID)';
COMMENT ON COLUMN t_output_invoice.original_invoice_no IS '原蓝字发票号码（红字发票专用）';

CREATE INDEX IF NOT EXISTS idx_output_invoice_reversed_by
    ON t_output_invoice(reversed_by_invoice_id);