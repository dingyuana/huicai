-- V138: 红冲发票账务处理字段扩展 (P36.1)
-- 为 t_input_invoice 增加红冲账务所需字段；t_output_invoice 补齐 reverse_reason 等缺失列

ALTER TABLE t_input_invoice
    ADD COLUMN IF NOT EXISTS reverse_reason VARCHAR(32),
    ADD COLUMN IF NOT EXISTS original_voucher_id BIGINT,
    ADD COLUMN IF NOT EXISTS original_certification_status VARCHAR(20),
    ADD COLUMN IF NOT EXISTS reversed_from BIGINT,
    ADD COLUMN IF NOT EXISTS original_invoice_no VARCHAR(64);

COMMENT ON COLUMN t_input_invoice.reverse_reason IS '红冲原因: INVOICE_ERROR-开票有误, RETURN-退货, DISCOUNT-折让, OTHER-其他';
COMMENT ON COLUMN t_input_invoice.original_voucher_id IS '原蓝字发票对应凭证ID（红冲时快照）';
COMMENT ON COLUMN t_input_invoice.original_certification_status IS '原发票抵扣状态快照: CERTIFIED/UNCERTIFIED';
COMMENT ON COLUMN t_input_invoice.reversed_from IS '被红冲的发票ID（红字发票指向原蓝字）';

ALTER TABLE t_output_invoice
    ADD COLUMN IF NOT EXISTS reverse_reason VARCHAR(32),
    ADD COLUMN IF NOT EXISTS original_voucher_id BIGINT,
    ADD COLUMN IF NOT EXISTS original_certification_status VARCHAR(20);

COMMENT ON COLUMN t_output_invoice.reverse_reason IS '红冲原因: INVOICE_ERROR-开票有误, RETURN-退货, DISCOUNT-折让, OTHER-其他';
COMMENT ON COLUMN t_output_invoice.original_voucher_id IS '原蓝字发票对应凭证ID（红冲时快照）';
COMMENT ON COLUMN t_output_invoice.original_certification_status IS '原发票状态快照';

CREATE INDEX IF NOT EXISTS idx_input_invoice_reversed_from ON t_input_invoice(reversed_from);