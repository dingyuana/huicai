-- ============================================================
-- V143: 修复 t_reconciliation_log.status CHECK 约束缺失 SUBMITTED
--
-- 背景：
--   核销执行改为「提报(SUBMITTED) → 审批(EXECUTED/CONFIRMED/REJECTED)」
--   流程后，代码在 ReconciliationServiceImpl.execute() 写入
--   ArapStatus.SUBMITTED，但 V94 建表时的 CHECK 约束只允许
--   CONFIRMED/EXECUTED/REJECTED/CANCELLED，导致 batch-execute / execute
--   插入时 500（DataIntegrityViolationException）。
--
-- 变更：
--   重建约束，合法状态集合加入 SUBMITTED：
--     SUBMITTED / CONFIRMED / EXECUTED / REJECTED / CANCELLED
--
-- 幂等：DROP CONSTRAINT IF EXISTS + ADD CONSTRAINT，可重复执行。
-- ============================================================

ALTER TABLE t_reconciliation_log
    DROP CONSTRAINT IF EXISTS chk_reconciliation_status;

ALTER TABLE t_reconciliation_log
    ADD CONSTRAINT chk_reconciliation_status
    CHECK (status IN ('SUBMITTED', 'CONFIRMED', 'EXECUTED', 'REJECTED', 'CANCELLED'));