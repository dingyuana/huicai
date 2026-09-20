-- ============================================================
-- V150: t_voucher_entry 增加 quantity、unit_price 字段
--
-- 背景：
--   P2 数量金额式账簿：需要记录每条分录的数量和单价，
--   以支持原材料、库存商品等存货科目的数量金额核算。
--
-- 变更：
--   quantity NUMERIC(18,2) 数量
--   unit_price NUMERIC(18,2) 单价
--
-- 幂等：ADD COLUMN IF NOT EXISTS，可重复执行。
-- ============================================================

ALTER TABLE t_voucher_entry
    ADD COLUMN IF NOT EXISTS quantity NUMERIC(18,2);

ALTER TABLE t_voucher_entry
    ADD COLUMN IF NOT EXISTS unit_price NUMERIC(18,2);

COMMENT ON COLUMN t_voucher_entry.quantity IS
    '数量（数量金额式账簿专用）';
COMMENT ON COLUMN t_voucher_entry.unit_price IS
    '单价（数量金额式账簿专用）';
