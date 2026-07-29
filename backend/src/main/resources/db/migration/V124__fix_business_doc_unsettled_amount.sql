-- ============================================================
-- V124: 修复历史业务单据中 unsettled_amount 为 0 的数据错误
--
-- 背景：AutoGenerationService.generateDocThenVoucher() 在创建
-- BusinessDocEntity 时未设置 unsettledAmount，导致数据库默认值为 0。
-- 该 Bug 已在 2026-07-29 修复，但已有历史数据需要修正。
--
-- 修复逻辑：对于已全额核销（settled_amount = amount）的记录保持不变，
-- 对于未核销（settled_amount = 0）且 unsettled_amount = 0 的记录，
-- 将 unsettled_amount 设为 amount。
-- 对于部分核销的异常记录，将 unsettled_amount 设为 amount - settled_amount。
-- ============================================================

-- 修复未核销记录：unsettled_amount = 0 但金额 > 0 且 settled_amount = 0
UPDATE t_business_doc
SET unsettled_amount = amount
WHERE unsettled_amount = 0
  AND settled_amount = 0
  AND amount > 0;

-- 修复部分核销但 unsettled_amount 不正确的记录
UPDATE t_business_doc
SET unsettled_amount = amount - settled_amount
WHERE unsettled_amount != amount - settled_amount
  AND settled_amount > 0
  AND amount > settled_amount;