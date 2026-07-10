-- V55: 核销单审核流程 + 红冲对冲单据支持
-- 1. 核销单增加 reversed_from_settlement_id（红冲回链）
ALTER TABLE t_arap_settlement ADD COLUMN IF NOT EXISTS reversed_from_settlement_id BIGINT DEFAULT NULL COMMENT '被对冲的原核销单ID';
ALTER TABLE t_arap_settlement ADD INDEX idx_reversed_from (reversed_from_settlement_id);