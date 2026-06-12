-- ============================================================
-- V7: 往来管理模块
-- 客户/供应商档案、应收应付明细、核销、账龄、坏账
-- ============================================================

-- 1. 客户档案
CREATE TABLE IF NOT EXISTS t_customer (
    id              BIGINT PRIMARY KEY,
    code            VARCHAR(32)  NOT NULL,
    name            VARCHAR(200) NOT NULL,
    contact_person  VARCHAR(64),
    phone           VARCHAR(32),
    email           VARCHAR(128),
    address         VARCHAR(500),
    tax_no          VARCHAR(64),
    bank_name       VARCHAR(128),
    bank_account    VARCHAR(64),
    credit_limit    NUMERIC(18,2) DEFAULT 0,
    credit_days     INTEGER      DEFAULT 30,
    subject_id      BIGINT,
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    remark          VARCHAR(500),
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT uq_customer_code UNIQUE (code),
    CONSTRAINT fk_customer_subject FOREIGN KEY (subject_id) REFERENCES t_subject(id)
);

COMMENT ON TABLE  t_customer IS '客户档案';
COMMENT ON COLUMN t_customer.credit_limit IS '信用额度';
COMMENT ON COLUMN t_customer.credit_days IS '账期(天)';
COMMENT ON COLUMN t_customer.subject_id IS '默认应收科目ID';

-- 2. 供应商档案
CREATE TABLE IF NOT EXISTS t_vendor (
    id              BIGINT PRIMARY KEY,
    code            VARCHAR(32)  NOT NULL,
    name            VARCHAR(200) NOT NULL,
    contact_person  VARCHAR(64),
    phone           VARCHAR(32),
    email           VARCHAR(128),
    address         VARCHAR(500),
    tax_no          VARCHAR(64),
    bank_name       VARCHAR(128),
    bank_account    VARCHAR(64),
    credit_limit    NUMERIC(18,2) DEFAULT 0,
    credit_days     INTEGER      DEFAULT 30,
    subject_id      BIGINT,
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    remark          VARCHAR(500),
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT uq_vendor_code UNIQUE (code),
    CONSTRAINT fk_vendor_subject FOREIGN KEY (subject_id) REFERENCES t_subject(id)
);

COMMENT ON TABLE  t_vendor IS '供应商档案';

-- 3. 应收明细(基于业务单据)
CREATE TABLE IF NOT EXISTS t_receivable (
    id              BIGINT PRIMARY KEY,
    customer_id     BIGINT       NOT NULL,
    doc_id          BIGINT,
    voucher_id      BIGINT,
    period          VARCHAR(6)   NOT NULL,
    tx_date         DATE         NOT NULL,
    amount          NUMERIC(18,2) NOT NULL,
    settled_amount  NUMERIC(18,2) NOT NULL DEFAULT 0,
    unsettled_amount NUMERIC(18,2) NOT NULL,
    due_date        DATE,
    summary         VARCHAR(500),
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT fk_recv_customer FOREIGN KEY (customer_id) REFERENCES t_customer(id),
    CONSTRAINT fk_recv_doc FOREIGN KEY (doc_id) REFERENCES t_business_doc(id)
);

CREATE INDEX IF NOT EXISTS idx_recv_customer ON t_receivable(customer_id);
CREATE INDEX IF NOT EXISTS idx_recv_period ON t_receivable(period);
CREATE INDEX IF NOT EXISTS idx_recv_due_date ON t_receivable(due_date);

COMMENT ON TABLE  t_receivable IS '应收明细';

-- 4. 应付明细
CREATE TABLE IF NOT EXISTS t_payable (
    id              BIGINT PRIMARY KEY,
    vendor_id       BIGINT       NOT NULL,
    doc_id          BIGINT,
    voucher_id      BIGINT,
    period          VARCHAR(6)   NOT NULL,
    tx_date         DATE         NOT NULL,
    amount          NUMERIC(18,2) NOT NULL,
    settled_amount  NUMERIC(18,2) NOT NULL DEFAULT 0,
    unsettled_amount NUMERIC(18,2) NOT NULL,
    due_date        DATE,
    summary         VARCHAR(500),
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT fk_pay_vendor FOREIGN KEY (vendor_id) REFERENCES t_vendor(id),
    CONSTRAINT fk_pay_doc FOREIGN KEY (doc_id) REFERENCES t_business_doc(id)
);

CREATE INDEX IF NOT EXISTS idx_pay_vendor ON t_payable(vendor_id);
CREATE INDEX IF NOT EXISTS idx_pay_period ON t_payable(period);
CREATE INDEX IF NOT EXISTS idx_pay_due_date ON t_payable(due_date);

COMMENT ON TABLE  t_payable IS '应付明细';

-- 5. 核销记录
CREATE TABLE IF NOT EXISTS t_arap_settlement (
    id              BIGINT PRIMARY KEY,
    settlement_no   VARCHAR(32)  NOT NULL,
    settlement_type VARCHAR(20)  NOT NULL,
    settlement_date DATE         NOT NULL,
    period          VARCHAR(6)   NOT NULL,
    party_id        BIGINT       NOT NULL,
    party_type      VARCHAR(20)  NOT NULL,
    total_amount    NUMERIC(18,2) NOT NULL,
    discount_amount NUMERIC(18,2) DEFAULT 0,
    voucher_id      BIGINT,
    status          VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    created_by      BIGINT,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT uq_settlement_no UNIQUE (settlement_no),
    CONSTRAINT chk_settlement_type CHECK (settlement_type IN ('RECEIVE', 'PAY')),
    CONSTRAINT chk_settlement_party_type CHECK (party_type IN ('CUSTOMER', 'VENDOR')),
    CONSTRAINT chk_settlement_status CHECK (status IN ('DRAFT', 'CONFIRMED', 'VOUCHERED', 'REVERSED'))
);

COMMENT ON TABLE  t_arap_settlement IS '核销单';

-- 6. 核销明细
CREATE TABLE IF NOT EXISTS t_arap_settlement_entry (
    id              BIGINT PRIMARY KEY,
    settlement_id   BIGINT       NOT NULL,
    receivable_id   BIGINT,
    payable_id      BIGINT,
    settled_amount  NUMERIC(18,2) NOT NULL,
    discount_amount NUMERIC(18,2) DEFAULT 0,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_settle_entry_settle FOREIGN KEY (settlement_id) REFERENCES t_arap_settlement(id) ON DELETE CASCADE,
    CONSTRAINT fk_settle_entry_recv FOREIGN KEY (receivable_id) REFERENCES t_receivable(id),
    CONSTRAINT fk_settle_entry_pay FOREIGN KEY (payable_id) REFERENCES t_payable(id),
    CONSTRAINT chk_settle_entry_one CHECK ((receivable_id IS NOT NULL AND payable_id IS NULL) OR (receivable_id IS NULL AND payable_id IS NOT NULL))
);

CREATE INDEX IF NOT EXISTS idx_settle_entry_settle ON t_arap_settlement_entry(settlement_id);

COMMENT ON TABLE  t_arap_settlement_entry IS '核销单明细';

-- 7. 坏账准备
CREATE TABLE IF NOT EXISTS t_bad_debt_provision (
    id              BIGINT PRIMARY KEY,
    period          VARCHAR(6)   NOT NULL,
    method          VARCHAR(20)  NOT NULL,
    provision_date  DATE         NOT NULL,
    total_amount    NUMERIC(18,2) NOT NULL,
    voucher_id      BIGINT,
    status          VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    remark          VARCHAR(500),
    created_by      BIGINT,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT chk_bad_debt_method CHECK (method IN ('AGING_RATIO', 'INDIVIDUAL', 'PERCENTAGE')),
    CONSTRAINT chk_bad_debt_status CHECK (status IN ('DRAFT', 'CONFIRMED', 'VOUCHERED'))
);

COMMENT ON TABLE  t_bad_debt_provision IS '坏账准备计提单';
COMMENT ON COLUMN t_bad_debt_provision.method IS '计提方法: AGING_RATIO-账龄比例法, INDIVIDUAL-个别认定法, PERCENTAGE-余额百分比法';
