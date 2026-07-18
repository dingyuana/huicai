-- V66: t_receivable 添加应收单编号字段
-- 日期: 2025-12-25
-- 说明: 应收单独立编号字段，用于发票关联展示

ALTER TABLE t_receivable
ADD COLUMN receivable_no VARCHAR(64);

COMMENT ON COLUMN t_receivable.receivable_no IS '应收单编号';

CREATE INDEX idx_receivable_no ON t_receivable(receivable_no);
