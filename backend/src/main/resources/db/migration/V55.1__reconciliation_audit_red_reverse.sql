-- V55.1: 核销单审核流程 + 红冲对冲单据支持
-- 1. 核销单增加 reversed_from_settlement_id（红冲回链）
ALTER TABLE t_arap_settlement ADD COLUMN IF NOT EXISTS reversed_from_settlement_id BIGINT DEFAULT NULL;
COMMENT ON COLUMN t_arap_settlement.reversed_from_settlement_id IS '被对冲的原核销单ID';
CREATE INDEX IF NOT EXISTS idx_reversed_from ON t_arap_settlement(reversed_from_settlement_id);