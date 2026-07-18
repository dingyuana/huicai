-- P53: 采购退货记录表
CREATE TABLE IF NOT EXISTS t_purchase_return (
    id                  BIGINT        PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    return_no           VARCHAR(64)   NOT NULL,
    vendor_id           BIGINT        NOT NULL,
    original_doc_no     VARCHAR(64),
    original_doc_id     BIGINT,
    return_amount       NUMERIC(18,2) NOT NULL,
    tax_amount          NUMERIC(18,2) NOT NULL DEFAULT 0,
    reason              TEXT,
    status              VARCHAR(20)   NOT NULL DEFAULT 'DRAFT',
    -- DRAFT / CONFIRMED / VOUCHERED / REVERSED
    voucher_id          BIGINT,
    voucher_no          VARCHAR(64),
    created_by          BIGINT,
    created_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             INTEGER       NOT NULL DEFAULT 0
);

COMMENT ON TABLE  t_purchase_return IS '采购退货记录';
COMMENT ON COLUMN t_purchase_return.return_amount IS '退货金额（含税）';
COMMENT ON COLUMN t_purchase_return.tax_amount IS '进项税额转出金额';
COMMENT ON COLUMN t_purchase_return.return_no IS '退货单号';
COMMENT ON COLUMN t_purchase_return.vendor_id IS '供应商ID';
COMMENT ON COLUMN t_purchase_return.original_doc_no IS '原应付单号';
COMMENT ON COLUMN t_purchase_return.original_doc_id IS '原应付单ID';
COMMENT ON COLUMN t_purchase_return.reason IS '退货原因';
COMMENT ON COLUMN t_purchase_return.status IS '状态: DRAFT/CONFIRMED/VOUCHERED/REVERSED';
COMMENT ON COLUMN t_purchase_return.voucher_id IS '关联凭证ID';
COMMENT ON COLUMN t_purchase_return.voucher_no IS '关联凭证编号';