-- ============================================================
-- V85: 客户对账与差异处理（P52）
-- ============================================================
BEGIN;

-- 1. 客户对账单
CREATE TABLE IF NOT EXISTS t_customer_statement (
    id                  BIGINT        PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    customer_id         BIGINT        NOT NULL,
    customer_name       VARCHAR(200),
    period              VARCHAR(6)    NOT NULL,
    statement_date      DATE          NOT NULL,
    total_original      NUMERIC(18,2) NOT NULL,
    total_settled       NUMERIC(18,2) NOT NULL,
    total_unsettled     NUMERIC(18,2) NOT NULL,
    status              VARCHAR(20)   NOT NULL DEFAULT 'DRAFT',
    sent_at             TIMESTAMP,
    confirmed_at        TIMESTAMP,
    created_by          BIGINT,
    created_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             INTEGER       NOT NULL DEFAULT 0,
    CONSTRAINT chk_stmt_status CHECK (status IN ('DRAFT','GENERATED','SENT','CONFIRMED','DISPUTED'))
);

CREATE INDEX idx_cust_stmt_customer ON t_customer_statement(customer_id);
CREATE INDEX idx_cust_stmt_period   ON t_customer_statement(period);
CREATE INDEX idx_cust_stmt_status   ON t_customer_statement(status);

COMMENT ON TABLE  t_customer_statement IS '客户对账单';
COMMENT ON COLUMN t_customer_statement.status IS 'DRAFT-草稿, GENERATED-已生成, SENT-已发送, CONFIRMED-已确认, DISPUTED-存在差异';

-- 2. 未达账项
CREATE TABLE IF NOT EXISTS t_reconciliation_outstanding (
    id                  BIGINT        PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    customer_id         BIGINT        NOT NULL,
    statement_id        BIGINT REFERENCES t_customer_statement(id),
    outstanding_type    VARCHAR(20)   NOT NULL,
    amount              NUMERIC(18,2) NOT NULL,
    description         VARCHAR(500),
    evidence            VARCHAR(200),
    status              VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    resolved_at         TIMESTAMP,
    created_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             INTEGER       NOT NULL DEFAULT 0
);

COMMENT ON TABLE  t_reconciliation_outstanding IS '未达账项';
COMMENT ON COLUMN t_reconciliation_outstanding.outstanding_type IS 'CUSTOMER_PAID-客户已付我方未到账, COMPANY_DEDUCTED-我方已扣客户未确认';
COMMENT ON COLUMN t_reconciliation_outstanding.status IS 'PENDING-待处理, RESOLVED-已解决, CANCELLED-已取消';

-- 3. 对账差异记录
CREATE TABLE IF NOT EXISTS t_reconciliation_dispute (
    id                  BIGINT        PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    statement_id        BIGINT        NOT NULL REFERENCES t_customer_statement(id),
    customer_id         BIGINT        NOT NULL,
    doc_no              VARCHAR(64),
    dispute_type        VARCHAR(20)   NOT NULL,
    expected_amount     NUMERIC(18,2),
    actual_amount       NUMERIC(18,2),
    diff_amount         NUMERIC(18,2),
    reason              TEXT,
    resolution          TEXT,
    resolved_by         BIGINT,
    resolved_at         TIMESTAMP,
    status              VARCHAR(20)   NOT NULL DEFAULT 'OPEN',
    created_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_dispute_statement ON t_reconciliation_dispute(statement_id);
CREATE INDEX idx_dispute_customer  ON t_reconciliation_dispute(customer_id);
CREATE INDEX idx_dispute_status    ON t_reconciliation_dispute(status);

COMMENT ON TABLE  t_reconciliation_dispute IS '对账差异记录';
COMMENT ON COLUMN t_reconciliation_dispute.dispute_type IS 'AMOUNT_MISMATCH-金额不符, MISSING_DOC-单据缺失, DISCOUNT-折扣争议, OTHER-其他';
COMMENT ON COLUMN t_reconciliation_dispute.status IS 'OPEN-待处理, RESOLVED-已解决, CLOSED-已关闭';

COMMIT;
