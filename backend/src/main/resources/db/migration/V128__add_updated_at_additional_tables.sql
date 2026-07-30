-- ============================================================
-- V128: 补充遗漏的 BaseEntity 继承表的审计列
--
-- 背景：check-entity-schema.mjs 检测出 t_asset_inventory_entry
-- 继承 BaseEntity 但缺少 created_at 和 updated_at 列。
-- 任何 INSERT 都会触发 "column created_at does not exist" 错误。
-- ============================================================

ALTER TABLE t_asset_inventory_entry ADD COLUMN IF NOT EXISTS created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE t_asset_inventory_entry ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP(6);