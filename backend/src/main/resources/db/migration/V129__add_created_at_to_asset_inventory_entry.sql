-- ============================================================
-- V129: 为 t_asset_inventory_entry 添加 created_at 列
--
-- 背景：AssetInventoryEntryEntity 继承 BaseEntity，
-- BaseEntity 的 createdAt 有 @TableField(fill = FieldFill.INSERT)，
-- 任何 INSERT 都会尝试写入 created_at 列。
-- 该表缺少此列，会导致 "column created_at does not exist" 错误。
-- ============================================================

ALTER TABLE t_asset_inventory_entry ADD COLUMN IF NOT EXISTS created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP;