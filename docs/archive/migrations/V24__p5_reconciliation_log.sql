-- ============================================================
-- V24: P5 智能核销 — 核销记录日志表
-- ============================================================

BEGIN;

CREATE TABLE t_reconciliation_log (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id          BIGINT NOT NULL DEFAULT 1,
    source_doc_type    VARCHAR(32) NOT NULL,    -- receipt/payment/bank_txn
    source_doc_id      BIGINT NOT NULL,
    target_doc_type    VARCHAR(32) NOT NULL,    -- INVOICE_OUT/INVOICE_IN
    target_doc_id      BIGINT NOT NULL,
    allocated_amount   NUMERIC(18,2) NOT NULL,
    discount_amount    NUMERIC(18,2) DEFAULT 0,
    match_score        NUMERIC(5,2),
    match_method       VARCHAR(20) DEFAULT 'MANUAL',  -- AUTO / MANUAL
    status             VARCHAR(20) DEFAULT 'CONFIRMED', -- CONFIRMED / CANCELLED
    remark             VARCHAR(500),
    created_by         BIGINT,
    created_at         TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_recon_log_source ON t_reconciliation_log(source_doc_type, source_doc_id);
CREATE INDEX idx_recon_log_target ON t_reconciliation_log(target_doc_type, target_doc_id);
CREATE INDEX idx_recon_log_tenant  ON t_reconciliation_log(tenant_id);

COMMIT;