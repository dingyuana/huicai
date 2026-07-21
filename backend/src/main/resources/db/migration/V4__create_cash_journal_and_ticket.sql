-- ============================================================
-- V4: 创建现金日记账和票据管理表
-- ============================================================

-- 现金日记账表
CREATE TABLE IF NOT EXISTS t_cash_journal (
    id                  BIGINT          GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    period              VARCHAR(20)     NOT NULL,
    journal_date        DATE            NOT NULL,
    journal_no          VARCHAR(50)     NOT NULL,
    summary             VARCHAR(500),
    debit               NUMERIC(18,2)   DEFAULT 0,
    credit              NUMERIC(18,2)   DEFAULT 0,
    balance             NUMERIC(18,2)   DEFAULT 0,
    subject_id          BIGINT,
    opposite_subject_id BIGINT,
    voucher_id          BIGINT,
    source              VARCHAR(50),
    created_by          BIGINT,
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          BIGINT,
    updated_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             INTEGER         NOT NULL DEFAULT 0,
    version             INTEGER         DEFAULT 0
);

COMMENT ON TABLE  t_cash_journal              IS '现金日记账';
COMMENT ON COLUMN t_cash_journal.id           IS '主键';
COMMENT ON COLUMN t_cash_journal.period       IS '会计期间';
COMMENT ON COLUMN t_cash_journal.journal_date IS '日期';
COMMENT ON COLUMN t_cash_journal.journal_no   IS '凭证号';
COMMENT ON COLUMN t_cash_journal.summary      IS '摘要';
COMMENT ON COLUMN t_cash_journal.debit        IS '借方金额';
COMMENT ON COLUMN t_cash_journal.credit       IS '贷方金额';
COMMENT ON COLUMN t_cash_journal.balance      IS '余额';
COMMENT ON COLUMN t_cash_journal.subject_id   IS '科目ID';
COMMENT ON COLUMN t_cash_journal.opposite_subject_id IS '对方科目ID';
COMMENT ON COLUMN t_cash_journal.voucher_id   IS '关联凭证ID';
COMMENT ON COLUMN t_cash_journal.source       IS '来源';
COMMENT ON COLUMN t_cash_journal.version      IS '乐观锁';

CREATE INDEX IF NOT EXISTS idx_cash_journal_period ON t_cash_journal(period, deleted);
CREATE INDEX IF NOT EXISTS idx_cash_journal_date  ON t_cash_journal(journal_date);

-- 票据表
CREATE TABLE IF NOT EXISTS t_ticket (
    id          BIGINT          GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ticket_no   VARCHAR(50)     NOT NULL,
    ticket_type VARCHAR(20)     NOT NULL,
    amount      NUMERIC(18,2)   NOT NULL DEFAULT 0,
    bank_id     BIGINT,
    payee       VARCHAR(200),
    drawer      VARCHAR(200),
    issue_date  DATE,
    expire_date DATE,
    status      VARCHAR(20)     NOT NULL DEFAULT 'IN_STOCK',
    remark      VARCHAR(500),
    created_by  BIGINT,
    created_at  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by  BIGINT,
    updated_at  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted     INTEGER         NOT NULL DEFAULT 0,

    CONSTRAINT chk_ticket_type CHECK (ticket_type IN ('CHECK', 'DRAFT', 'CASHIER_CHECK', 'BANK_ACCEPTANCE')),
    CONSTRAINT chk_ticket_status CHECK (status IN ('IN_STOCK', 'ISSUED', 'ENDORSED', 'CASHED', 'VOIDED'))
);

COMMENT ON TABLE  t_ticket              IS '票据管理';
COMMENT ON COLUMN t_ticket.id           IS '主键';
COMMENT ON COLUMN t_ticket.ticket_no    IS '票据号码';
COMMENT ON COLUMN t_ticket.ticket_type  IS '票据类型: CHECK-支票/DRAFT-汇票/CASHIER_CHECK-本票/BANK_ACCEPTANCE-银行承兑汇票';
COMMENT ON COLUMN t_ticket.amount       IS '金额';
COMMENT ON COLUMN t_ticket.bank_id      IS '开户银行ID';
COMMENT ON COLUMN t_ticket.payee        IS '收款人';
COMMENT ON COLUMN t_ticket.drawer       IS '出票人';
COMMENT ON COLUMN t_ticket.issue_date   IS '出票日期';
COMMENT ON COLUMN t_ticket.expire_date  IS '到期日期';
COMMENT ON COLUMN t_ticket.status       IS '状态: IN_STOCK-在库/ISSUED-已领用/ENDORSED-已背书/CASHED-已兑现/VOIDED-已作废';

CREATE INDEX IF NOT EXISTS idx_ticket_status ON t_ticket(status, deleted);
CREATE INDEX IF NOT EXISTS idx_ticket_bank   ON t_ticket(bank_id);

-- 票据交易流水表
CREATE TABLE IF NOT EXISTS t_ticket_transaction (
    id          BIGINT          GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ticket_id   BIGINT          NOT NULL REFERENCES t_ticket(id),
    trans_type  VARCHAR(20)     NOT NULL,
    trans_date  DATE            NOT NULL,
    recipient   VARCHAR(200),
    amount      NUMERIC(18,2)   NOT NULL DEFAULT 0,
    remark      VARCHAR(500),
    operator_id BIGINT,
    voucher_id  BIGINT,
    created_at  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE  t_ticket_transaction              IS '票据交易流水';
COMMENT ON COLUMN t_ticket_transaction.id           IS '主键';
COMMENT ON COLUMN t_ticket_transaction.ticket_id    IS '票据ID';
COMMENT ON COLUMN t_ticket_transaction.trans_type   IS '交易类型: ISSUE/ENDORSE/CASH/VOID/RETURN';
COMMENT ON COLUMN t_ticket_transaction.trans_date   IS '交易日期';
COMMENT ON COLUMN t_ticket_transaction.recipient    IS '对方';
COMMENT ON COLUMN t_ticket_transaction.amount       IS '金额';
COMMENT ON COLUMN t_ticket_transaction.operator_id  IS '操作人';
COMMENT ON COLUMN t_ticket_transaction.voucher_id   IS '关联凭证ID';

CREATE INDEX IF NOT EXISTS idx_ticket_trans_ticket ON t_ticket_transaction(ticket_id);