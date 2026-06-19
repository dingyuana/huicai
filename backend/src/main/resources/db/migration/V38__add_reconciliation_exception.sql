-- P0-异常池: 核销异常记录表 — 存储自动核销失败/需要人工介入的异常记录
CREATE TABLE t_reconciliation_exception (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id         BIGINT        NOT NULL DEFAULT 1,
    source_doc_type   VARCHAR(32)   NOT NULL,        -- receipt/payment/bank_txn
    source_doc_id     BIGINT        NOT NULL,
    target_doc_type   VARCHAR(32),                    -- INVOICE_OUT/INVOICE_IN (可能为空)
    target_doc_id     BIGINT,
    party_id          BIGINT,                         -- 客户/供应商 ID
    party_type        VARCHAR(20),                    -- CUSTOMER / VENDOR
    amount            NUMERIC(18,2) NOT NULL,
    unsettled_amount  NUMERIC(18,2),
    exception_type    VARCHAR(32)   NOT NULL,         -- PARTY_MISMATCH / AMOUNT_MISMATCH / INVOICE_NOT_FOUND / MATCH_FAILED / APPROVAL_REQUIRED
    exception_reason  VARCHAR(1000),
    match_suggestion  VARCHAR(1000),                  -- AI/规则推荐的匹配方案 (JSON)
    status            VARCHAR(20)   NOT NULL DEFAULT 'OPEN',  -- OPEN / RESOLVED / IGNORED
    retry_count       INTEGER       NOT NULL DEFAULT 0,
    assigned_to       BIGINT,                         -- 处理人
    resolved_by       BIGINT,
    resolved_at       TIMESTAMP,
    remark            VARCHAR(500),
    created_by        BIGINT,
    created_at        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_exception_source   ON t_reconciliation_exception(source_doc_type, source_doc_id);
CREATE INDEX idx_exception_party    ON t_reconciliation_exception(party_id, party_type);
CREATE INDEX idx_exception_status   ON t_reconciliation_exception(status);
CREATE INDEX idx_exception_type     ON t_reconciliation_exception(exception_type);
