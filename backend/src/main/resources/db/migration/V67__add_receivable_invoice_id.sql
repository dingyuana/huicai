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

-- 3. 历史数据补全：通过 invoice_no 关联补全 invoice_id
-- 注意：只补全 invoice_no 非空且 invoice_id 为空的记录
UPDATE t_receivable r
SET invoice_id = (
    SELECT i.id 
    FROM t_output_invoice i 
    WHERE i.invoice_no = r.invoice_no 
    LIMIT 1
)
WHERE r.invoice_id IS NULL 
  AND r.invoice_no IS NOT NULL;
