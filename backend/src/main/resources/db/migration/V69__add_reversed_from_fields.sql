-- V69: 为 t_output_invoice 和 t_receivable 添加 reversed_from 字段
-- 用途: 红冲链路溯源（指向被红冲的原始记录 ID）

-- 销售发票：被哪张蓝字发票红冲
ALTER TABLE t_output_invoice ADD COLUMN IF NOT EXISTS reversed_from BIGINT;
COMMENT ON COLUMN t_output_invoice.reversed_from IS '被哪张蓝字发票红冲(指向蓝字发票 ID)';
CREATE INDEX IF NOT EXISTS idx_output_invoice_reversed_from
    ON t_output_invoice(reversed_from);

-- 应收单：被哪张红冲应收单红冲
ALTER TABLE t_receivable ADD COLUMN IF NOT EXISTS reversed_from BIGINT;
COMMENT ON COLUMN t_receivable.reversed_from IS '被红冲应收单ID';
CREATE INDEX IF NOT EXISTS idx_receivable_reversed_from
    ON t_receivable(reversed_from);
