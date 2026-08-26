-- V139: 进项发票申报抵扣状态拆分 (P57)
-- 将"认证"与"申报抵扣"拆分为两个独立状态，增值税计算以"已申报抵扣"为准

ALTER TABLE t_input_invoice
    ADD COLUMN IF NOT EXISTS declared_status VARCHAR(20) DEFAULT 'UNDECLARED',
    ADD COLUMN IF NOT EXISTS declared_period VARCHAR(8),
    ADD COLUMN IF NOT EXISTS declared_date DATE;

COMMENT ON COLUMN t_input_invoice.declared_status IS '申报抵扣状态: UNDECLARED-已认证未申报, DECLARED-已申报抵扣';
COMMENT ON COLUMN t_input_invoice.declared_period IS '申报所属期 yyyyMM';
COMMENT ON COLUMN t_input_invoice.declared_date IS '申报日期';