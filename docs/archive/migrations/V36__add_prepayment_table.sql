-- P12-3: 预付款表 — 供应商预付账款
CREATE TABLE t_prepayment (
    id               BIGINT        NOT NULL PRIMARY KEY,
    tenant_id        BIGINT        NOT NULL DEFAULT 1,
    vendor_id        BIGINT        NOT NULL,
    doc_id           BIGINT,
    voucher_id       BIGINT,
    period           VARCHAR(6)    NOT NULL,
    tx_date          DATE          NOT NULL,
    amount           NUMERIC(18,2) NOT NULL,
    settled_amount   NUMERIC(18,2) NOT NULL DEFAULT 0,
    unsettled_amount NUMERIC(18,2) NOT NULL,
    summary          VARCHAR(500),
    status           VARCHAR(20)   NOT NULL DEFAULT 'DRAFT',
    source_doc_type  VARCHAR(50),
    source_doc_id    BIGINT,
    remark           VARCHAR(500),
    created_by       VARCHAR(64),
    created_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted          INTEGER       NOT NULL DEFAULT 0
);

CREATE INDEX idx_prepay_vendor    ON t_prepayment(vendor_id);
CREATE INDEX idx_prepay_period    ON t_prepayment(period);
CREATE INDEX idx_prepay_source    ON t_prepayment(source_doc_type, source_doc_id);
CREATE INDEX idx_prepay_status    ON t_prepayment(status);
