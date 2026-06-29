-- V67__add_receivable_invoice_id.sql
-- P33 销售发票流程简化：应收单直接关联发票
-- 日期: 2026-06-29
-- 说明: 给 t_receivable 增加 invoice_id 列，实现发票→应收单直接关联，不再经过业务单

-- 1. 添加 invoice_id 列（直接关联发票ID）
ALTER TABLE t_receivable
ADD COLUMN invoice_id BIGINT;

COMMENT ON COLUMN t_receivable.invoice_id IS '关联销售发票ID（P33 简化：直接关联，不经过业务单）';

-- 2. 创建索引
CREATE INDEX idx_receivable_invoice_id ON t_receivable(invoice_id);
