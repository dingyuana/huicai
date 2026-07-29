-- ============================================================
-- V125: 修复 t_output_invoice.status CHECK 约束缺少核销状态
--
-- 背景：InvoiceStatus 新增了 FULLY_RECONCILED / PARTIALLY_RECONCILED
-- 状态常量（用于核销同步），但 DB CHECK 约束未同步更新，
-- 执行核销时导致 "violates check constraint" 错误。
--
-- 同时修复旧约束中的 VOID → VOIDED（与 InvoiceStatus 常量对齐）。
-- 旧值 ISSUED/RED_INK 已在 InvoiceStatus 中移除，实际数据中无人使用。
-- ============================================================

-- 先删除旧约束
ALTER TABLE t_output_invoice DROP CONSTRAINT IF EXISTS chk_output_invoice_status;

-- 添加新约束，匹配 InvoiceStatus 常量
ALTER TABLE t_output_invoice ADD CONSTRAINT chk_output_invoice_status
    CHECK (status IN (
        'PENDING_CONFIRM',
        'PENDING_REVIEW',
        'CONFIRMED',
        'VOUCHERED',
        'FULLY_RECONCILED',
        'PARTIALLY_RECONCILED',
        'VOIDED',
        'REVERSED'
    ));