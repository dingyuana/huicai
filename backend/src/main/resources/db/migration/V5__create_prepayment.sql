-- ============================================================
-- V5: 创建预收/预付表 t_prepayment
-- ============================================================
CREATE TABLE IF NOT EXISTS t_prepayment (
    id               BIGINT          GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id        BIGINT          NOT NULL DEFAULT 1,
    deleted          INTEGER         NOT NULL DEFAULT 0,
    vendor_id        BIGINT,
    customer_id      BIGINT,
    doc_id           BIGINT,
    voucher_id       BIGINT,
    period           VARCHAR(20)     NOT NULL,
    tx_date          DATE            NOT NULL,
    amount           NUMERIC(18,2)   NOT NULL DEFAULT 0,
    settled_amount   NUMERIC(18,2)   NOT NULL DEFAULT 0,
    unsettled_amount NUMERIC(18,2)   NOT NULL DEFAULT 0,
    summary          VARCHAR(500),
    status           VARCHAR(20)     NOT NULL DEFAULT 'DRAFT',
    source_doc_type  VARCHAR(50),
    source_doc_id    BIGINT,
    remark           VARCHAR(500),
    created_by       VARCHAR(50),
    created_at       DATE,
    updated_at       DATE
);

COMMENT ON TABLE  t_prepayment               IS '预收/预付表';
COMMENT ON COLUMN t_prepayment.id            IS '主键';
COMMENT ON COLUMN t_prepayment.tenant_id     IS '租户ID';
COMMENT ON COLUMN t_prepayment.vendor_id     IS '供应商ID(预付)';
COMMENT ON COLUMN t_prepayment.customer_id   IS '客户ID(预收)';
COMMENT ON COLUMN t_prepayment.period        IS '会计期间';
COMMENT ON COLUMN t_prepayment.amount        IS '金额';
COMMENT ON COLUMN t_prepayment.settled_amount IS '已核销金额';
COMMENT ON COLUMN t_prepayment.unsettled_amount IS '未核销金额';
COMMENT ON COLUMN t_prepayment.status        IS '状态: DRAFT/SUBMITTED/AUDITED/POSTED';

CREATE INDEX IF NOT EXISTS idx_prepayment_period ON t_prepayment(period, deleted);
CREATE INDEX IF NOT EXISTS idx_prepayment_vendor ON t_prepayment(vendor_id);
CREATE INDEX IF NOT EXISTS idx_prepayment_customer ON t_prepayment(customer_id);