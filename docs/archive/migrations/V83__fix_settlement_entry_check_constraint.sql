-- ============================================================
-- V83: 修复核销分录 check constraint — 允许 business_doc_id
--
-- P34 将 t_arap_settlement_entry 的 receivable_id/payable_id
-- 替换为 business_doc_id, 但 chk_settle_entry_one 未同步更新,
-- 导致 INSERT 时违反约束 (receivable_id + payable_id 均为 NULL).
-- ============================================================

ALTER TABLE t_arap_settlement_entry DROP CONSTRAINT IF EXISTS chk_settle_entry_one;

ALTER TABLE t_arap_settlement_entry ADD CONSTRAINT chk_settle_entry_one
  CHECK (
    (receivable_id IS NOT NULL AND payable_id IS NULL) OR
    (receivable_id IS NULL AND payable_id IS NOT NULL) OR
    (business_doc_id IS NOT NULL)
  );