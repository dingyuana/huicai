-- P11-2: 费用报销单表
CREATE TABLE IF NOT EXISTS t_expense_reimbursement (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reimb_no        VARCHAR(50) NOT NULL,
    employee_id     BIGINT NOT NULL,
    dept_id         BIGINT,
    expense_type    VARCHAR(50) NOT NULL,
    amount          NUMERIC(18,2) NOT NULL,
    summary         VARCHAR(500),
    status          VARCHAR(20) DEFAULT 'DRAFT',
    doc_id          BIGINT,
    voucher_id      BIGINT,
    bank_stmt_id    BIGINT,
    attachment_ids  TEXT,
    submitted_at    TIMESTAMP,
    approved_at     TIMESTAMP,
    created_by      BIGINT,
    approved_by     VARCHAR(100),
    reject_reason   VARCHAR(500),
    created_at      TIMESTAMP DEFAULT now(),
    updated_at      TIMESTAMP DEFAULT now(),
    deleted         INTEGER DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_er_employee ON t_expense_reimbursement(employee_id);
CREATE INDEX IF NOT EXISTS idx_er_status ON t_expense_reimbursement(status);
CREATE INDEX IF NOT EXISTS idx_er_stmt ON t_expense_reimbursement(bank_stmt_id);
