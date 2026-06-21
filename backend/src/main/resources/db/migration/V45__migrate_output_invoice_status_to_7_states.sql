-- V45__migrate_output_invoice_status_to_7_states.sql
-- 2026-06-21 P21-a 实施
-- 依据: docs/specs/P21-sales-invoice-state-machine.md §3
-- 实施者: Hermes (按老丁显式授权破例直写, 2026-06-21)

-- ============================================================
-- Step 1: 数据迁移（4 旧状态 → 8 新状态）
-- ============================================================

-- 1.1 NULL → PENDING_CONFIRM（未设置状态视为待确认）
UPDATE t_output_invoice
SET status = 'PENDING_CONFIRM'
WHERE status IS NULL;

-- 1.2 DRAFT → PENDING_CONFIRM（草稿 = 待确认）
UPDATE t_output_invoice
SET status = 'PENDING_CONFIRM'
WHERE status = 'DRAFT';

-- 1.3 ISSUED → CONFIRMED（已开票 = 已确认）
-- 注意: 旧 ISSUED 记录中部分 voucher_id 为空，
--       业务上 voucher_id 为空的 CONFIRMED 视为"待生成凭证"
UPDATE t_output_invoice
SET status = 'CONFIRMED'
WHERE status = 'ISSUED';

-- 1.4 VOID → VOIDED（已作废）
UPDATE t_output_invoice
SET status = 'VOIDED'
WHERE status = 'VOID';

-- 1.5 RED_INK → REVERSED（红字冲销）
UPDATE t_output_invoice
SET status = 'REVERSED'
WHERE status = 'RED_INK';

-- ============================================================
-- Step 2: DROP 旧 CHECK 约束，加新 CHECK 约束（8 状态）
-- ============================================================

ALTER TABLE t_output_invoice
    DROP CONSTRAINT IF EXISTS chk_output_invoice_status;

ALTER TABLE t_output_invoice
    ADD CONSTRAINT chk_output_invoice_status
    CHECK (status IN (
        'PENDING_CONFIRM', 'PENDING_REVIEW', 'CONFIRMED',
        'VOUCHERED', 'FULLY_RECONCILED', 'PARTIALLY_RECONCILED', 'VOIDED',
        'REVERSED'
    ));

-- ============================================================
-- Step 3: status 字段索引（前端按状态过滤）
-- ============================================================

CREATE INDEX IF NOT EXISTS idx_t_output_invoice_status
    ON t_output_invoice(status);

-- ============================================================
-- Step 4: COMMENT 更新
-- ============================================================

COMMENT ON COLUMN t_output_invoice.status IS
    '状态: PENDING_CONFIRM/PENDING_REVIEW/CONFIRMED/VOUCHERED/FULLY_RECONCILED/PARTIALLY_RECONCILED/VOIDED/REVERSED (2026-06-21 由 V8 旧 4 状态迁移)';

-- ============================================================
-- Step 5: 迁移结果审计（输出统计，供人工核对）
-- ============================================================

DO $$
DECLARE
    rec RECORD;
BEGIN
    FOR rec IN
        SELECT status, COUNT(*) AS cnt
        FROM t_output_invoice
        GROUP BY status
        ORDER BY status
    LOOP
        RAISE NOTICE 'V45 迁移结果: status=%, count=%', rec.status, rec.cnt;
    END LOOP;
END $$;