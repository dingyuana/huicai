-- V43: t_arap_settlement 补充 updated_by 列
-- ArapSettlementEntity 包含 updatedBy 字段, 但建表时遗漏了该列
-- 导致 getById/generateVoucher 等操作报错 "column updated_by does not exist"

ALTER TABLE t_arap_settlement
    ADD COLUMN IF NOT EXISTS updated_by BIGINT;
