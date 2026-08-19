-- V137: t_business_doc 增加 is_reversed 列 — 父端红冲标记
-- 用途：原单据被红冲后标记 isReversed=true，支持从原单追溯红冲状态
ALTER TABLE t_business_doc ADD COLUMN IF NOT EXISTS is_reversed BOOLEAN DEFAULT FALSE NOT NULL;
COMMENT ON COLUMN t_business_doc.is_reversed IS '是否已被红冲（父端标记，由reverse()写）';