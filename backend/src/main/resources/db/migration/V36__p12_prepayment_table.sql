-- ============================================================
-- V36: P12 核销业务闭环 — 预付款/预收款表
-- 供应商预付款 + 客户预收款, 统一放在 t_prepayment
-- ============================================================

BEGIN;

CREATE TABLE IF NOT EXISTS t_prepayment (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id          BIGINT NOT NULL DEFAULT 1,

    -- 方向: PAYMENT_PREPAY(供应商预付款) / RECEIPT_PREPAY(客户预收款)
    prepay_type        VARCHAR(30) NOT NULL DEFAULT 'PAYMENT_PREPAY',

    -- 客商: vendor_id ↔ PAYMENT_PREPAY ; customer_id ↔ RECEIPT_PREPAY
    vendor_id          BIGINT,
    customer_id        BIGINT,

    doc_id             BIGINT,                   -- 关联 t_business_doc
    voucher_id         BIGINT,                   -- 关联 t_voucher
    period             VARCHAR(10),
    tx_date            DATE,

    amount             NUMERIC(18,2) NOT NULL,
    settled_amount     NUMERIC(18,2) DEFAULT 0,
    unsettled_amount   NUMERIC(18,2),

    summary            VARCHAR(500),

    -- 状态: DRAFT / CONFIRMED / SETTLED / CANCELLED
    status             VARCHAR(20) DEFAULT 'DRAFT',

    source_doc_type    VARCHAR(30),              -- bank_txn / MANUAL 等
    source_doc_id      BIGINT,

    remark             VARCHAR(500),
    created_by         VARCHAR(50),
    created_at         TIMESTAMP DEFAULT now(),
    updated_at         TIMESTAMP DEFAULT now(),
    deleted            INTEGER DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_t_prepayment_vendor  ON t_prepayment(vendor_id);
CREATE INDEX IF NOT EXISTS idx_t_prepayment_cust    ON t_prepayment(customer_id);
CREATE INDEX IF NOT EXISTS idx_t_prepayment_status  ON t_prepayment(status);
CREATE INDEX IF NOT EXISTS idx_t_prepayment_period  ON t_prepayment(period);
CREATE INDEX IF NOT EXISTS idx_t_prepayment_type    ON t_prepayment(prepay_type);

COMMIT;
