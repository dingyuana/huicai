-- ============================================================
-- V8: 税务管理模块
-- 税种、进项税、销项税、附加税、申报
-- ============================================================

-- 1. 税种定义
CREATE TABLE IF NOT EXISTS t_tax_type (
    id              BIGINT PRIMARY KEY,
    code            VARCHAR(32)  NOT NULL,
    name            VARCHAR(100) NOT NULL,
    tax_category    VARCHAR(20)  NOT NULL,
    rate            NUMERIC(8,4) NOT NULL,
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    remark          VARCHAR(500),
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT uq_tax_type_code UNIQUE (code),
    CONSTRAINT chk_tax_category CHECK (tax_category IN ('VAT_INPUT', 'VAT_OUTPUT', 'SURCHARGE', 'INCOME', 'OTHER'))
);

COMMENT ON TABLE  t_tax_type IS '税种定义';
COMMENT ON COLUMN t_tax_type.tax_category IS '税种类别: VAT_INPUT-进项税, VAT_OUTPUT-销项税, SURCHARGE-附加税, INCOME-所得税, OTHER-其他';

-- 2. 进项发票
CREATE TABLE IF NOT EXISTS t_input_invoice (
    id              BIGINT PRIMARY KEY,
    invoice_no      VARCHAR(64)  NOT NULL,
    invoice_date    DATE         NOT NULL,
    period          VARCHAR(6)   NOT NULL,
    vendor_id       BIGINT,
    vendor_name     VARCHAR(200),
    amount          NUMERIC(18,2) NOT NULL,
    tax_rate        NUMERIC(8,4) NOT NULL,
    tax_amount      NUMERIC(18,2) NOT NULL,
    total_amount    NUMERIC(18,2) NOT NULL,
    invoice_type    VARCHAR(20)  NOT NULL,
    certification_status VARCHAR(20) DEFAULT 'UNCERTIFIED',
    certified_date  DATE,
    deduction_period VARCHAR(6),
    deduction_amount NUMERIC(18,2),
    doc_id          BIGINT,
    voucher_id      BIGINT,
    remark          VARCHAR(500),
    created_by      BIGINT,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT uq_input_invoice_no UNIQUE (invoice_no),
    CONSTRAINT fk_input_invoice_vendor FOREIGN KEY (vendor_id) REFERENCES t_vendor(id),
    CONSTRAINT chk_invoice_type CHECK (invoice_type IN ('SPECIAL', 'PLAIN', 'CUSTOMS', 'TRANSPORT')),
    CONSTRAINT chk_cert_status CHECK (certification_status IN ('UNCERTIFIED', 'CERTIFIED', 'INVALID', 'CANCELLED'))
);

CREATE INDEX IF NOT EXISTS idx_input_invoice_period ON t_input_invoice(period);
CREATE INDEX IF NOT EXISTS idx_input_invoice_vendor ON t_input_invoice(vendor_id);
CREATE INDEX IF NOT EXISTS idx_input_invoice_cert ON t_input_invoice(certification_status);

COMMENT ON TABLE  t_input_invoice IS '进项发票';
COMMENT ON COLUMN t_input_invoice.invoice_type IS '发票类型: SPECIAL-增值税专用, PLAIN-普通, CUSTOMS-海关缴款, TRANSPORT-运输';
COMMENT ON COLUMN t_input_invoice.certification_status IS '认证状态: UNCERTIFIED-未认证, CERTIFIED-已认证, INVALID-无效, CANCELLED-已注销';

-- 3. 销项发票
CREATE TABLE IF NOT EXISTS t_output_invoice (
    id              BIGINT PRIMARY KEY,
    invoice_no      VARCHAR(64)  NOT NULL,
    invoice_date    DATE         NOT NULL,
    period          VARCHAR(6)   NOT NULL,
    customer_id     BIGINT,
    customer_name   VARCHAR(200),
    amount          NUMERIC(18,2) NOT NULL,
    tax_rate        NUMERIC(8,4) NOT NULL,
    tax_amount      NUMERIC(18,2) NOT NULL,
    total_amount    NUMERIC(18,2) NOT NULL,
    invoice_type    VARCHAR(20)  NOT NULL,
    status          VARCHAR(20)  DEFAULT 'ISSUED',
    doc_id          BIGINT,
    voucher_id      BIGINT,
    remark          VARCHAR(500),
    created_by      BIGINT,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT uq_output_invoice_no UNIQUE (invoice_no),
    CONSTRAINT fk_output_invoice_customer FOREIGN KEY (customer_id) REFERENCES t_customer(id),
    CONSTRAINT chk_output_invoice_type CHECK (invoice_type IN ('SPECIAL', 'PLAIN', 'CUSTOMS')),
    CONSTRAINT chk_output_invoice_status CHECK (status IN ('DRAFT', 'ISSUED', 'VOID', 'RED_INK'))
);

CREATE INDEX IF NOT EXISTS idx_output_invoice_period ON t_output_invoice(period);
CREATE INDEX IF NOT EXISTS idx_output_invoice_customer ON t_output_invoice(customer_id);

COMMENT ON TABLE  t_output_invoice IS '销项发票';

-- 4. 税金结转记录
CREATE TABLE IF NOT EXISTS t_tax_carry_over (
    id              BIGINT PRIMARY KEY,
    period          VARCHAR(6)   NOT NULL,
    tax_type        VARCHAR(20)  NOT NULL,
    output_tax      NUMERIC(18,2) DEFAULT 0,
    input_tax       NUMERIC(18,2) DEFAULT 0,
    payable_tax     NUMERIC(18,2) DEFAULT 0,
    surcharge_total NUMERIC(18,2) DEFAULT 0,
    voucher_id      BIGINT,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_carry_tax_type CHECK (tax_type IN ('VAT', 'SURCHARGE')),
    CONSTRAINT uq_carry_period_type UNIQUE (period, tax_type)
);

COMMENT ON TABLE  t_tax_carry_over IS '税金结转记录';

-- 5. 纳税申报
CREATE TABLE IF NOT EXISTS t_tax_declaration (
    id              BIGINT PRIMARY KEY,
    declaration_no  VARCHAR(32)  NOT NULL,
    period          VARCHAR(6)   NOT NULL,
    tax_type        VARCHAR(20)  NOT NULL,
    declared_date   DATE         NOT NULL,
    payable_amount  NUMERIC(18,2) NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    voucher_id      BIGINT,
    remark          VARCHAR(500),
    created_by      BIGINT,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT uq_declaration_no UNIQUE (declaration_no),
    CONSTRAINT chk_declaration_tax_type CHECK (tax_type IN ('VAT', 'SURCHARGE', 'INCOME')),
    CONSTRAINT chk_declaration_status CHECK (status IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'PAID'))
);

COMMENT ON TABLE  t_tax_declaration IS '纳税申报';
