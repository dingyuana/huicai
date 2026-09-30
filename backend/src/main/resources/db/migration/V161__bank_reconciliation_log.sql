-- ============================================================
-- V161: 银行对账人工确认/驳回日志（REQ-2026-134 / P107 D4）
--
-- 缺陷：BankReconciliationServiceImpl.confirmMatch/rejectMatch 只
--       log.info 并返回 ConfirmResult，既不更新 t_bank_statement.match_status
--       也不动 t_bank_journal.is_reconciled —— 人工确认/驳回完全丢失，
--       流水状态永远停在 PENDING_CONFIRM/UNMATCHED。
--
-- 为什么需要新表：银行侧原本只有 t_bank_account / t_bank_journal /
--   t_bank_statement 三张表，**不存在任何对账日志表**
--   （t_reconciliation_log 属应收应付核销域，不可混用）。
--   人工确认是铁律 #1/#5 的关键动作，必须可审计（谁、何时、
--   从什么状态改到什么状态、关联哪张日记账）。
--
-- match_status 取值须落在既有 CHECK chk_stmt_match_status 内：
--   UNMATCHED / MATCHED / MANUAL_MATCHED / IGNORED
--   本迁移记录人工确认后的状态 MANUAL_MATCHED（与自动匹配的 MATCHED 区分，
--   呼应 Iron rule：人工与自动必须可分辨）。
-- ============================================================

CREATE TABLE IF NOT EXISTS t_bank_reconciliation_log (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    statement_id        BIGINT       NOT NULL,
    journal_id          BIGINT,
    action              VARCHAR(20)  NOT NULL,   -- CONFIRM / REJECT
    status_before       VARCHAR(20),
    status_after        VARCHAR(20)  NOT NULL,
    operator            VARCHAR(64)  NOT NULL,
    remark              VARCHAR(500),
    enterprise_id       BIGINT,
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP,
    deleted             INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT chk_bank_recon_log_action
        CHECK (action IN ('CONFIRM', 'REJECT')),
    CONSTRAINT chk_bank_recon_log_status_after
        CHECK (status_after IN ('UNMATCHED', 'MATCHED', 'MANUAL_MATCHED', 'IGNORED', 'PENDING_CONFIRM'))
);

CREATE INDEX IF NOT EXISTS idx_bank_recon_log_statement
    ON t_bank_reconciliation_log (statement_id);

CREATE INDEX IF NOT EXISTS idx_bank_recon_log_enterprise
    ON t_bank_reconciliation_log (enterprise_id);

COMMENT ON TABLE t_bank_reconciliation_log IS '银行对账人工确认/驳回日志（铁律 #5 审计追踪）';
COMMENT ON COLUMN t_bank_reconciliation_log.action IS 'CONFIRM=人工确认匹配 / REJECT=人工驳回';
