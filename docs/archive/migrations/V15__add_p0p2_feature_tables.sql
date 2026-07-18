-- ============================================================
-- V15: P0-P2 功能增强 - 新增表和字段
-- 现金日记账 / 票据管理 / 期初建账控制 / 账龄分析
-- ============================================================

-- 1. 现金日记账
CREATE TABLE IF NOT EXISTS t_cash_journal (
    id              BIGINT PRIMARY KEY,
    period          VARCHAR(6)    NOT NULL,
    journal_date    DATE          NOT NULL,
    journal_no      VARCHAR(32)   NOT NULL,
    summary         VARCHAR(500),
    debit           NUMERIC(18,2) NOT NULL DEFAULT 0,
    credit          NUMERIC(18,2) NOT NULL DEFAULT 0,
    balance         NUMERIC(18,2) NOT NULL DEFAULT 0,
    subject_id      BIGINT        NOT NULL REFERENCES t_subject(id),
    opposite_subject_id BIGINT    REFERENCES t_subject(id),
    voucher_id      BIGINT        REFERENCES t_voucher(id),
    source          VARCHAR(20)   DEFAULT 'MANUAL',
    created_by      BIGINT,
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      BIGINT,
    updated_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         INTEGER       NOT NULL DEFAULT 0,
    CONSTRAINT uq_cash_journal_no UNIQUE (journal_no),
    CONSTRAINT chk_cash_source CHECK (source IN ('MANUAL', 'AUTO', 'GENERATED'))
);
CREATE INDEX IF NOT EXISTS idx_cash_journal_period ON t_cash_journal(period);
CREATE INDEX IF NOT EXISTS idx_cash_journal_date ON t_cash_journal(journal_date);

COMMENT ON TABLE  t_cash_journal             IS '现金日记账';
COMMENT ON COLUMN t_cash_journal.journal_no  IS '日记账编号';
COMMENT ON COLUMN t_cash_journal.balance     IS '余额(当日结存)';
COMMENT ON COLUMN t_cash_journal.subject_id  IS '现金科目ID';
COMMENT ON COLUMN t_cash_journal.source      IS '来源: MANUAL手工/AUTO自动/GENERATED生成';

-- 2. 票据管理
CREATE TABLE IF NOT EXISTS t_ticket (
    id              BIGINT PRIMARY KEY,
    ticket_no       VARCHAR(32)   NOT NULL,
    ticket_type     VARCHAR(20)   NOT NULL,
    amount          NUMERIC(18,2) NOT NULL DEFAULT 0,
    bank_id         BIGINT        REFERENCES t_bank_account(id),
    payee           VARCHAR(100),
    drawer          VARCHAR(100),
    issue_date      DATE          NOT NULL,
    expire_date     DATE,
    status          VARCHAR(20)   NOT NULL DEFAULT 'IN_STOCK',
    remark          VARCHAR(500),
    created_by      BIGINT,
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      BIGINT,
    updated_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         INTEGER       NOT NULL DEFAULT 0,
    CONSTRAINT uq_ticket_no UNIQUE (ticket_no),
    CONSTRAINT chk_ticket_type CHECK (ticket_type IN ('CHECK', 'DRAFT', 'CASHIER_CHECK', 'BANK_ACCEPTANCE')),
    CONSTRAINT chk_ticket_status CHECK (status IN ('IN_STOCK', 'ISSUED', 'ENDORSED', 'CASHED', 'VOIDED'))
);

COMMENT ON TABLE  t_ticket              IS '票据管理(支票/汇票)';
COMMENT ON COLUMN t_ticket.ticket_type  IS 'CHECK支票/DRAFT汇票/CASHIER_CHECK本票/BANK_ACCEPTANCE银行承兑';
COMMENT ON COLUMN t_ticket.status       IS 'IN_STOCK在库/ISSUED已领用/ENDORSED已背书/CASHED已兑现/VOIDED已作废';

-- 3. 票据交易流水
CREATE TABLE IF NOT EXISTS t_ticket_transaction (
    id              BIGINT PRIMARY KEY,
    ticket_id       BIGINT        NOT NULL REFERENCES t_ticket(id) ON DELETE CASCADE,
    trans_type      VARCHAR(20)   NOT NULL,
    trans_date      DATE          NOT NULL,
    recipient       VARCHAR(100),
    amount          NUMERIC(18,2) NOT NULL DEFAULT 0,
    remark          VARCHAR(500),
    operator_id     BIGINT,
    voucher_id      BIGINT        REFERENCES t_voucher(id),
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_ticket_trans_type CHECK (trans_type IN ('ISSUE', 'ENDORSE', 'CASH', 'VOID', 'RETURN'))
);

CREATE INDEX IF NOT EXISTS idx_ticket_trans_ticket ON t_ticket_transaction(ticket_id);
CREATE INDEX IF NOT EXISTS idx_ticket_trans_date ON t_ticket_transaction(trans_date);

COMMENT ON TABLE  t_ticket_transaction IS '票据交易流水';
COMMENT ON COLUMN t_ticket_transaction.trans_type IS 'ISSUE领用/ENDORSE背书/CASH兑现/VOID作废/RETURN退回';

-- 4. 期初建账控制
CREATE TABLE IF NOT EXISTS t_beginning_control (
    id              BIGINT PRIMARY KEY,
    period          VARCHAR(6)    NOT NULL,
    is_initialized  BOOLEAN       NOT NULL DEFAULT FALSE,
    is_balanced     BOOLEAN       NOT NULL DEFAULT FALSE,
    is_locked       BOOLEAN       NOT NULL DEFAULT FALSE,
    initialized_by  BIGINT,
    initialized_at  TIMESTAMP,
    locked_by       BIGINT,
    locked_at       TIMESTAMP,
    note            VARCHAR(500),
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_begin_control_period UNIQUE (period)
);

COMMENT ON TABLE  t_beginning_control              IS '期初建账控制';
COMMENT ON COLUMN t_beginning_control.is_initialized IS '是否已录入期初';
COMMENT ON COLUMN t_beginning_control.is_balanced    IS '试算是否平衡';
COMMENT ON COLUMN t_beginning_control.is_locked      IS '是否已锁定(启用)';

-- 5. 预算执行记录表(用于预算控制)
CREATE TABLE IF NOT EXISTS t_budget_execution (
    id              BIGINT PRIMARY KEY,
    budget_entry_id BIGINT        NOT NULL REFERENCES t_budget_entry(id) ON DELETE CASCADE,
    period          VARCHAR(6)    NOT NULL,
    actual_amount   NUMERIC(18,2) NOT NULL DEFAULT 0,
    source_type     VARCHAR(20)   NOT NULL,
    source_id       BIGINT,
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_budget_exec_source CHECK (source_type IN ('VOUCHER', 'DOC', 'MANUAL'))
);

CREATE INDEX IF NOT EXISTS idx_budget_exec_entry ON t_budget_execution(budget_entry_id);
CREATE INDEX IF NOT EXISTS idx_budget_exec_period ON t_budget_execution(period);

COMMENT ON TABLE  t_budget_execution              IS '预算执行记录';
COMMENT ON COLUMN t_budget_execution.actual_amount IS '实际发生金额';
COMMENT ON COLUMN t_budget_execution.source_type   IS '来源类型: VOUCHER凭证/DOC单据/MANUAL手工';
