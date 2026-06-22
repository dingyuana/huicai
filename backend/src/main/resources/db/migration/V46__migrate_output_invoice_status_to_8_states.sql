-- V46__migrate_output_invoice_status_to_8_states.sql
-- 2026-06-22 P21-a-1 修复版
-- 依据: docs/specs/P21-sales-invoice-state-machine.md §3
-- 实施者: Hermes (按老丁 1C2B3A 显式授权, 2026-06-22)
--
-- 修复历史:
--   V45 (fdff38b) 顺序 bug: 先 UPDATE 旧数据再 DROP 旧 CHECK 约束
--   UPDATE 4 旧状态 (DRAFT/ISSUED/VOID/RED_INK → PENDING_CONFIRM/CONFIRMED/VOIDED/REVERSED)
--   中 ISSUED → CONFIRMED 违反 V8 旧 CHECK 约束 (DRAFT/ISSUED/VOID/RED_INK, 无 CONFIRMED)
--   V45 跑时 PG 报 check_violation: 23514 整体回滚
-- V46 正确顺序: 先 DROP 旧 CHECK → UPDATE 旧数据 → ADD 新 CHECK
-- V45 删除 (commit 在同 PR), 保留 V45 含 6 状态无 REVERSED 的旧 SPEC 引用作为 V45 文档不实施

-- ============================================================
-- Step 1: DROP V8 旧 CHECK 约束 (chk_output_invoice_status 4 状态)
-- ============================================================

ALTER TABLE t_output_invoice
    DROP CONSTRAINT IF EXISTS chk_output_invoice_status;

-- ============================================================
-- Step 2: 数据迁移 (4 旧状态 → 8 新状态)
-- ============================================================

-- 2.1 NULL → PENDING_CONFIRM (未设置状态视为待确认)
UPDATE t_output_invoice
SET status = 'PENDING_CONFIRM'
WHERE status IS NULL;

-- 2.2 DRAFT → PENDING_CONFIRM (草稿 = 待确认)
UPDATE t_output_invoice
SET status = 'PENDING_CONFIRM'
WHERE status = 'DRAFT';

-- 2.3 ISSUED → CONFIRMED (已开票 = 已确认)
-- 注意: 旧 ISSUED 记录中部分 voucher_id 为空,
--       业务上 voucher_id 为空的 CONFIRMED 视为 "待生成凭证"
UPDATE t_output_invoice
SET status = 'CONFIRMED'
WHERE status = 'ISSUED';

-- 2.4 VOID → VOIDED (已作废)
UPDATE t_output_invoice
SET status = 'VOIDED'
WHERE status = 'VOID';

-- 2.5 RED_INK → REVERSED (红字冲销)
UPDATE t_output_invoice
SET status = 'REVERSED'
WHERE status = 'RED_INK';

-- ============================================================
-- Step 3: ADD 新 CHECK 约束 (8 状态)
-- ============================================================

ALTER TABLE t_output_invoice
    ADD CONSTRAINT chk_output_invoice_status
    CHECK (status IN (
        'PENDING_CONFIRM', 'PENDING_REVIEW', 'CONFIRMED',
        'VOUCHERED', 'FULLY_RECONCILED', 'PARTIALLY_RECONCILED', 'VOIDED',
        'REVERSED'
    ));

-- ============================================================
-- Step 4: status 字段索引 (前端按状态过滤, 幂等)
-- ============================================================

CREATE INDEX IF NOT EXISTS idx_t_output_invoice_status
    ON t_output_invoice(status);

-- ============================================================
-- Step 5: COMMENT 更新
-- ============================================================

COMMENT ON COLUMN t_output_invoice.status IS
    '状态: PENDING_CONFIRM/PENDING_REVIEW/CONFIRMED/VOUCHERED/FULLY_RECONCILED/PARTIALLY_RECONCILED/VOIDED/REVERSED (2026-06-22 由 V8 旧 4 状态经 V46 迁移)';

-- ============================================================
-- Step 6: 迁移结果审计 (输出统计, 供人工核对)
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
        RAISE NOTICE 'V46 迁移结果: status=%, count=%', rec.status, rec.cnt;
    END LOOP;
END $$;
