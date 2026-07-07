-- ============================================================
-- V79: 发票增强字段 — 价税分离 / AI 风险标签 / 处理状态
--
-- 改动:
-- 1. t_input_invoice 增加 amount_ex_tax, ai_risk_tag, process_status
-- 2. t_output_invoice 增加 amount_ex_tax, ai_risk_tag, process_status
-- ============================================================

-- 进项发票
ALTER TABLE t_input_invoice ADD COLUMN IF NOT EXISTS amount_ex_tax NUMERIC(18,2) DEFAULT 0;
COMMENT ON COLUMN t_input_invoice.amount_ex_tax IS '不含税金额（价税分离计算）';
ALTER TABLE t_input_invoice ADD COLUMN IF NOT EXISTS ai_risk_tag VARCHAR(50);
COMMENT ON COLUMN t_input_invoice.ai_risk_tag IS 'AI 风险标签';
ALTER TABLE t_input_invoice ADD COLUMN IF NOT EXISTS process_status VARCHAR(20) DEFAULT 'PENDING';
COMMENT ON COLUMN t_input_invoice.process_status IS '处理状态: PENDING/PROCESSED/FAILED';

-- 销项发票
ALTER TABLE t_output_invoice ADD COLUMN IF NOT EXISTS amount_ex_tax NUMERIC(18,2) DEFAULT 0;
COMMENT ON COLUMN t_output_invoice.amount_ex_tax IS '不含税金额（价税分离计算）';
ALTER TABLE t_output_invoice ADD COLUMN IF NOT EXISTS ai_risk_tag VARCHAR(50);
COMMENT ON COLUMN t_output_invoice.ai_risk_tag IS 'AI 风险标签';
ALTER TABLE t_output_invoice ADD COLUMN IF NOT EXISTS process_status VARCHAR(20) DEFAULT 'PENDING';
COMMENT ON COLUMN t_output_invoice.process_status IS '处理状态: PENDING/PROCESSED/FAILED';