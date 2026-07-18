-- ============================================================
-- V9: 预算管理模块
-- 预算编制、预算执行、预算调整
-- ============================================================

-- 1. 预算主表(按期间)
CREATE TABLE IF NOT EXISTS t_budget (
    id              BIGINT PRIMARY KEY,
    budget_no       VARCHAR(32)  NOT NULL,
    period          VARCHAR(6)   NOT NULL,
    budget_type     VARCHAR(20)  NOT NULL,
    total_amount    NUMERIC(18,2) NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    approved_by     BIGINT,
    approved_at     TIMESTAMP,
    remark          VARCHAR(500),
    created_by      BIGINT,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      BIGINT,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT uq_budget_no UNIQUE (budget_no),
    CONSTRAINT chk_budget_type CHECK (budget_type IN ('EXPENSE', 'REVENUE', 'CAPEX')),
    CONSTRAINT chk_budget_status CHECK (status IN ('DRAFT', 'APPROVED', 'ACTIVE', 'CLOSED'))
);

CREATE INDEX IF NOT EXISTS idx_budget_period ON t_budget(period);

COMMENT ON TABLE  t_budget IS '预算主表';
COMMENT ON COLUMN t_budget.budget_type IS '预算类型: EXPENSE-费用, REVENUE-收入, CAPEX-资本性支出';

-- 2. 预算明细(按科目/部门/项目)
CREATE TABLE IF NOT EXISTS t_budget_entry (
    id              BIGINT PRIMARY KEY,
    budget_id       BIGINT       NOT NULL,
    subject_id      BIGINT,
    dept_id         BIGINT,
    project_id      BIGINT,
    period_month    INTEGER,
    amount          NUMERIC(18,2) NOT NULL,
    control_type    VARCHAR(20)  NOT NULL DEFAULT 'WARN',
    used_amount     NUMERIC(18,2) NOT NULL DEFAULT 0,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_budget_entry_budget FOREIGN KEY (budget_id) REFERENCES t_budget(id) ON DELETE CASCADE,
    CONSTRAINT fk_budget_entry_subject FOREIGN KEY (subject_id) REFERENCES t_subject(id),
    CONSTRAINT chk_control_type CHECK (control_type IN ('WARN', 'APPROVE', 'BLOCK'))
);

CREATE INDEX IF NOT EXISTS idx_budget_entry_budget ON t_budget_entry(budget_id);
CREATE INDEX IF NOT EXISTS idx_budget_entry_subject ON t_budget_entry(subject_id);
CREATE INDEX IF NOT EXISTS idx_budget_entry_dept ON t_budget_entry(dept_id);

COMMENT ON TABLE  t_budget_entry IS '预算明细';
COMMENT ON COLUMN t_budget_entry.control_type IS '控制类型: WARN-预警, APPROVE-审批, BLOCK-禁止';

-- 3. 预算调整
CREATE TABLE IF NOT EXISTS t_budget_adjustment (
    id              BIGINT PRIMARY KEY,
    adjustment_no   VARCHAR(32)  NOT NULL,
    budget_id       BIGINT       NOT NULL,
    adjustment_type VARCHAR(20)  NOT NULL,
    adjustment_date DATE         NOT NULL,
    period          VARCHAR(6)   NOT NULL,
    adjustment_amount NUMERIC(18,2) NOT NULL,
    reason          VARCHAR(500),
    status          VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    approved_by     BIGINT,
    approved_at     TIMESTAMP,
    created_by      BIGINT,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT uq_adjustment_no UNIQUE (adjustment_no),
    CONSTRAINT fk_adjustment_budget FOREIGN KEY (budget_id) REFERENCES t_budget(id),
    CONSTRAINT chk_adjustment_type CHECK (adjustment_type IN ('INCREASE', 'DECREASE', 'TRANSFER')),
    CONSTRAINT chk_adjustment_status CHECK (status IN ('DRAFT', 'APPROVED', 'REJECTED'))
);

COMMENT ON TABLE  t_budget_adjustment IS '预算调整';
