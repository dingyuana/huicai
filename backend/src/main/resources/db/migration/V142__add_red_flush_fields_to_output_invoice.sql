-- V142: 补 t_output_invoice 红冲关联列 (对齐 OutputInvoiceEntity)
-- 根因: OutputInvoiceEntity 的 reversedFrom(@TableField("reversed_from")) 和
--       originalInvoiceNo(@TableField("original_invoice_no")) 映射到不存在的列,
--       导致 SELECT 报错 "column reversed_from does not exist" (500)
-- 同类问题: t_input_invoice 已在 V138 补列

ALTER TABLE t_output_invoice
    ADD COLUMN IF NOT EXISTS reversed_from BIGINT,
    ADD COLUMN IF NOT EXISTS original_invoice_no VARCHAR(32);

COMMENT ON COLUMN t_output_invoice.reversed_from IS '被哪张蓝字发票红冲(指向蓝字发票ID, 红字发票专用)';
COMMENT ON COLUMN t_output_invoice.original_invoice_no IS '原蓝字发票号码(红字发票专用)';
