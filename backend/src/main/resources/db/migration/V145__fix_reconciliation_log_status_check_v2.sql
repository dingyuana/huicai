-- ============================================================
-- V145: t_reconciliation_log.status CHECK 约束补 VOUCHERED/REVERSED
--
-- 背景：
--   V143 只修复了 SUBMITTED 缺失，但 ArapSettlementServiceImpl
--   logReconciliationLog() 写入 settlement.status（操作后状态）：
--     GENERATE_VOUCHER → VOUCHERED
--     REVERSE         → REVERSED
--   这两个状态不在 V143 建立的合法集合（SUBMITTED/CONFIRMED/
--   EXECUTED/REJECTED/CANCELLED）内，导致制证/红冲时日志 INSERT
--   违反 chk_reconciliation_status → 事务 aborted → 整单回滚 500。
--   （2026-09-10 实测: generate-voucher 核销单7 报
--    "new row ... violates check constraint chk_reconciliation_status"，
--    Failing row status=VOUCHERED）
--
-- 变更：
--   重建约束，合法状态集合加入 VOUCHERED / REVERSED：
--     SUBMITTED / CONFIRMED / EXECUTED / REJECTED / CANCELLED
--     / VOUCHERED / REVERSED
--
-- 幂等：DROP CONSTRAINT IF EXISTS + ADD CONSTRAINT，可重复执行。
-- ============================================================

ALTER TABLE t_reconciliation_log
    DROP CONSTRAINT IF EXISTS chk_reconciliation_status;

ALTER TABLE t_reconciliation_log
    ADD CONSTRAINT chk_reconciliation_status
    CHECK (status IN ('SUBMITTED', 'CONFIRMED', 'EXECUTED', 'REJECTED',
                      'CANCELLED', 'VOUCHERED', 'REVERSED'));