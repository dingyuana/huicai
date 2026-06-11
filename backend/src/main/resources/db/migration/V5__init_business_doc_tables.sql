-- ============================================================
-- V5: 业务单据与出纳管理表
-- ============================================================

-- 1. 业务单据主表
CREATE TABLE IF NOT EXISTS t_business_doc (
    id              BIGINT PRIMARY KEY,
    doc_no          VARCHAR(32)   NOT NULL,
    doc_type        VARCHAR(32)   NOT NULL,
    doc_date        DATE          NOT NULL,
    period          VARCHAR(6)    NOT NULL,
    amount          NUMERIC(18,2) NOT NULL DEFAULT 0,
    status          VARCHAR(20)   NOT NULL DEFAULT 'DRAFT',
    supplier_id     BIGINT,
    customer_id     BIGINT,
    applicant_id    BIGINT,
    dept_id         BIGINT,
    summary         VARCHAR(500),
    source          VARCHAR(20)   DEFAULT 'MANUAL',
    ocr_data        JSONB,
    attachment_ids  TEXT,
    voucher_id      BIGINT,
    reversed_from   BIGINT,
    created_by      BIGINT,
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      BIGINT,
    updated_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    submitted_by    BIGINT,
    submitted_at    TIMESTAMP,
    approved_by     BIGINT,
    approved_at     TIMESTAMP,
    deleted         INTEGER       NOT NULL DEFAULT 0,
    CONSTRAINT uq_doc_no_type UNIQUE (doc_type, doc_no),
    CONSTRAINT chk_doc_status CHECK (status IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'VOUCHERED', 'CLOSED', 'REJECTED')),
    CONSTRAINT chk_doc_type CHECK (doc_type IN ('RECEIPT', 'PAYMENT', 'EXPENSE', 'INVOICE_IN', 'INVOICE_OUT', 'OTHER_RECEIVABLE', 'OTHER_PAYABLE'))
);

CREATE INDEX IF NOT EXISTS idx_doc_period ON t_business_doc(period);
CREATE INDEX IF NOT EXISTS idx_doc_type ON t_business_doc(doc_type);
CREATE INDEX IF NOT EXISTS idx_doc_status ON t_business_doc(status);
CREATE INDEX IF NOT EXISTS idx_doc_date ON t_business_doc(doc_date);

COMMENT ON TABLE  t_business_doc                IS '业务单据主表';
COMMENT ON COLUMN t_business_doc.doc_no          IS '单据编号';
COMMENT ON COLUMN t_business_doc.doc_type        IS '单据类型';
COMMENT ON COLUMN t_business_doc.doc_date        IS '单据日期';
COMMENT ON COLUMN t_business_doc.period          IS '会计期间';
COMMENT ON COLUMN t_business_doc.amount          IS '单据金额';
COMMENT ON COLUMN t_business_doc.status          IS '状态: DRAFT-草稿, SUBMITTED-已提交, APPROVED-已审批, VOUCHERED-已生成凭证, CLOSED-已关闭, REJECTED-已驳回';
COMMENT ON COLUMN t_business_doc.supplier_id     IS '供应商ID';
COMMENT ON COLUMN t_business_doc.customer_id     IS '客户ID';
COMMENT ON COLUMN t_business_doc.applicant_id    IS '申请人ID';
COMMENT ON COLUMN t_business_doc.dept_id         IS '部门ID';
COMMENT ON COLUMN t_business_doc.summary         IS '摘要';
COMMENT ON COLUMN t_business_doc.source          IS '来源: MANUAL, OCR, IMPORTED';
COMMENT ON COLUMN t_business_doc.ocr_data        IS 'OCR 识别数据';
COMMENT ON COLUMN t_business_doc.attachment_ids  IS '附件ID列表';
COMMENT ON COLUMN t_business_doc.voucher_id      IS '生成的凭证ID';
COMMENT ON COLUMN t_business_doc.reversed_from   IS '被红冲单据ID';
COMMENT ON COLUMN t_business_doc.submitted_by    IS '提交人';
COMMENT ON COLUMN t_business_doc.submitted_at    IS '提交时间';
COMMENT ON COLUMN t_business_doc.approved_by     IS '审批人';
COMMENT ON COLUMN t_business_doc.approved_at     IS '审批时间';

-- 2. 业务单据分录表
CREATE TABLE IF NOT EXISTS t_business_doc_entry (
    id              BIGINT PRIMARY KEY,
    doc_id          BIGINT        NOT NULL,
    expense_type    VARCHAR(32),
    subject_id      BIGINT,
    amount          NUMERIC(18,2) NOT NULL DEFAULT 0,
    invoice_no      VARCHAR(64),
    assist_json     JSONB,
    summary         VARCHAR(500),
    sort_order      INTEGER       NOT NULL DEFAULT 1,
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_doc_entry_doc FOREIGN KEY (doc_id) REFERENCES t_business_doc(id) ON DELETE CASCADE,
    CONSTRAINT fk_doc_entry_subject FOREIGN KEY (subject_id) REFERENCES t_subject(id)
);

CREATE INDEX IF NOT EXISTS idx_doc_entry_doc_id ON t_business_doc_entry(doc_id);
CREATE INDEX IF NOT EXISTS idx_doc_entry_subject_id ON t_business_doc_entry(subject_id);

COMMENT ON TABLE  t_business_doc_entry              IS '业务单据分录';
COMMENT ON COLUMN t_business_doc_entry.expense_type   IS '费用类别';
COMMENT ON COLUMN t_business_doc_entry.subject_id     IS '科目ID';
COMMENT ON COLUMN t_business_doc_entry.amount         IS '金额';
COMMENT ON COLUMN t_business_doc_entry.invoice_no     IS '发票号';
COMMENT ON COLUMN t_business_doc_entry.assist_json    IS '辅助核算JSON';
COMMENT ON COLUMN t_business_doc_entry.summary        IS '摘要';

-- 3. 凭证模板表
CREATE TABLE IF NOT EXISTS t_voucher_template (
    id              BIGINT PRIMARY KEY,
    template_code   VARCHAR(32)   NOT NULL,
    template_name   VARCHAR(64)   NOT NULL,
    doc_type        VARCHAR(32)   NOT NULL,
    summary         VARCHAR(500),
    entries         JSONB         NOT NULL,
    is_active       BOOLEAN       DEFAULT TRUE,
    remark          VARCHAR(500),
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         INTEGER       NOT NULL DEFAULT 0,
    CONSTRAINT uq_tpl_code UNIQUE (template_code)
);

COMMENT ON TABLE  t_voucher_template IS '凭证模板';
COMMENT ON COLUMN t_voucher_template.entries IS '分录模板 JSON: [{summary, debitSubjectCode, creditSubjectCode, ...}]';

-- 4. 银行账户表
CREATE TABLE IF NOT EXISTS t_bank_account (
    id              BIGINT PRIMARY KEY,
    account_no      VARCHAR(64)   NOT NULL,
    account_name    VARCHAR(128)  NOT NULL,
    bank_name       VARCHAR(128),
    currency        VARCHAR(8)    DEFAULT 'CNY',
    subject_id      BIGINT,
    balance         NUMERIC(18,2) NOT NULL DEFAULT 0,
    is_active       BOOLEAN       DEFAULT TRUE,
    remark          VARCHAR(500),
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         INTEGER       NOT NULL DEFAULT 0,
    CONSTRAINT fk_bank_account_subject FOREIGN KEY (subject_id) REFERENCES t_subject(id),
    CONSTRAINT uq_bank_account_no UNIQUE (account_no)
);

COMMENT ON TABLE  t_bank_account IS '银行账户';
COMMENT ON COLUMN t_bank_account.account_no IS '账号';
COMMENT ON COLUMN t_bank_account.subject_id IS '对应科目ID(银行存款末级)';

-- 5. 银行日记账表(企业账)
CREATE TABLE IF NOT EXISTS t_bank_journal (
    id              BIGINT PRIMARY KEY,
    account_id      BIGINT        NOT NULL,
    tx_date         DATE          NOT NULL,
    period          VARCHAR(6)    NOT NULL,
    tx_type         VARCHAR(20)   NOT NULL,
    counter_account VARCHAR(128),
    amount          NUMERIC(18,2) NOT NULL,
    summary         VARCHAR(500),
    business_doc_id BIGINT,
    voucher_id      BIGINT,
    is_reconciled   BOOLEAN       DEFAULT FALSE,
    created_by      BIGINT,
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         INTEGER       NOT NULL DEFAULT 0,
    CONSTRAINT fk_journal_account FOREIGN KEY (account_id) REFERENCES t_bank_account(id),
    CONSTRAINT fk_journal_doc FOREIGN KEY (business_doc_id) REFERENCES t_business_doc(id),
    CONSTRAINT chk_journal_type CHECK (tx_type IN ('INCOME', 'EXPENSE', 'TRANSFER_IN', 'TRANSFER_OUT'))
);

CREATE INDEX IF NOT EXISTS idx_journal_account ON t_bank_journal(account_id);
CREATE INDEX IF NOT EXISTS idx_journal_period ON t_bank_journal(period);
CREATE INDEX IF NOT EXISTS idx_journal_date ON t_bank_journal(tx_date);
CREATE INDEX IF NOT EXISTS idx_journal_reconciled ON t_bank_journal(is_reconciled);

COMMENT ON TABLE  t_bank_journal IS '银行日记账(企业账)';

-- 6. 银行对账单表(银行账)
CREATE TABLE IF NOT EXISTS t_bank_statement (
    id              BIGINT PRIMARY KEY,
    account_id      BIGINT        NOT NULL,
    tx_date         DATE          NOT NULL,
    tx_type         VARCHAR(20)   NOT NULL,
    counter_account VARCHAR(128),
    amount          NUMERIC(18,2) NOT NULL,
    summary         VARCHAR(500),
    external_no     VARCHAR(64),
    raw_data        JSONB,
    matched_journal_id BIGINT,
    match_status    VARCHAR(20)   DEFAULT 'UNMATCHED',
    imported_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         INTEGER       NOT NULL DEFAULT 0,
    CONSTRAINT fk_statement_account FOREIGN KEY (account_id) REFERENCES t_bank_account(id),
    CONSTRAINT fk_statement_journal FOREIGN KEY (matched_journal_id) REFERENCES t_bank_journal(id),
    CONSTRAINT chk_stmt_type CHECK (tx_type IN ('INCOME', 'EXPENSE', 'TRANSFER_IN', 'TRANSFER_OUT')),
    CONSTRAINT chk_stmt_match_status CHECK (match_status IN ('UNMATCHED', 'MATCHED', 'MANUAL_MATCHED', 'IGNORED'))
);

CREATE INDEX IF NOT EXISTS idx_stmt_account ON t_bank_statement(account_id);
CREATE INDEX IF NOT EXISTS idx_stmt_date ON t_bank_statement(tx_date);
CREATE INDEX IF NOT EXISTS idx_stmt_match_status ON t_bank_statement(match_status);

COMMENT ON TABLE  t_bank_statement IS '银行对账单(银行账)';

-- 7. 对账匹配建议表
CREATE TABLE IF NOT EXISTS t_reconciliation_suggestion (
    id              BIGINT PRIMARY KEY,
    account_id      BIGINT        NOT NULL,
    statement_id    BIGINT        NOT NULL,
    journal_id      BIGINT        NOT NULL,
    score           NUMERIC(5,4)  NOT NULL,
    status          VARCHAR(20)   DEFAULT 'PENDING',
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    confirmed_at    TIMESTAMP,
    CONSTRAINT fk_suggest_stmt FOREIGN KEY (statement_id) REFERENCES t_bank_statement(id),
    CONSTRAINT fk_suggest_journal FOREIGN KEY (journal_id) REFERENCES t_bank_journal(id),
    CONSTRAINT chk_suggest_status CHECK (status IN ('PENDING', 'CONFIRMED', 'REJECTED'))
);

COMMENT ON TABLE t_reconciliation_suggestion IS '对账匹配建议(AI 辅助)';
