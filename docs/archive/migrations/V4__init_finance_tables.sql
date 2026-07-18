-- ============================================================
-- V4: 初始化财务核心表
-- 凭证主表、凭证分录表、科目余额表
-- ============================================================

-- 1. 凭证主表
CREATE TABLE IF NOT EXISTS t_voucher (
    id              BIGINT PRIMARY KEY,
    voucher_no      VARCHAR(32)   NOT NULL,
    period          VARCHAR(6)    NOT NULL,
    voucher_type_id BIGINT        NOT NULL,
    status          VARCHAR(20)   NOT NULL DEFAULT 'DRAFT',
    total_debit     NUMERIC(18,2) NOT NULL DEFAULT 0,
    total_credit    NUMERIC(18,2) NOT NULL DEFAULT 0,
    summary         VARCHAR(500),
    source          VARCHAR(20)   DEFAULT 'MANUAL',
    attachment_ids  TEXT,
    created_by      BIGINT,
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      BIGINT,
    updated_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    submitted_by    BIGINT,
    submitted_at    TIMESTAMP,
    audited_by      BIGINT,
    audited_at      TIMESTAMP,
    posted_by       BIGINT,
    posted_at       TIMESTAMP,
    reversed_from   BIGINT,
    deleted         INTEGER       NOT NULL DEFAULT 0,
    CONSTRAINT uq_voucher_no UNIQUE (voucher_no),
    CONSTRAINT chk_voucher_status CHECK (status IN ('DRAFT', 'SUBMITTED', 'AUDITED', 'POSTED')),
    CONSTRAINT chk_voucher_source CHECK (source IN ('MANUAL', 'TEMPLATE', 'GENERATED', 'REVERSAL')),
    CONSTRAINT fk_voucher_type FOREIGN KEY (voucher_type_id) REFERENCES t_voucher_type(id)
);

COMMENT ON TABLE  t_voucher                IS '凭证主表';
COMMENT ON COLUMN t_voucher.id              IS '主键';
COMMENT ON COLUMN t_voucher.voucher_no      IS '凭证号(格式: 类型+年份+月份+流水号)';
COMMENT ON COLUMN t_voucher.period           IS '会计期间(YYYYMM)';
COMMENT ON COLUMN t_voucher.voucher_type_id  IS '凭证类型ID';
COMMENT ON COLUMN t_voucher.status           IS '状态: DRAFT-草稿, SUBMITTED-已提交, AUDITED-已审核, POSTED-已记账';
COMMENT ON COLUMN t_voucher.total_debit      IS '借方总金额';
COMMENT ON COLUMN t_voucher.total_credit     IS '贷方总金额';
COMMENT ON COLUMN t_voucher.summary          IS '摘要';
COMMENT ON COLUMN t_voucher.source           IS '来源: MANUAL-手工录入, TEMPLATE-模板生成, GENERATED-单据生成, REVERSAL-红冲';
COMMENT ON COLUMN t_voucher.attachment_ids   IS '附件ID列表(逗号分隔)';
COMMENT ON COLUMN t_voucher.created_by       IS '制单人';
COMMENT ON COLUMN t_voucher.created_at       IS '制单时间';
COMMENT ON COLUMN t_voucher.updated_by       IS '更新人';
COMMENT ON COLUMN t_voucher.updated_at       IS '更新时间';
COMMENT ON COLUMN t_voucher.submitted_by     IS '提交人';
COMMENT ON COLUMN t_voucher.submitted_at     IS '提交时间';
COMMENT ON COLUMN t_voucher.audited_by       IS '审核人';
COMMENT ON COLUMN t_voucher.audited_at       IS '审核时间';
COMMENT ON COLUMN t_voucher.posted_by        IS '记账人';
COMMENT ON COLUMN t_voucher.posted_at        IS '记账时间';
COMMENT ON COLUMN t_voucher.reversed_from    IS '被红冲凭证ID';
COMMENT ON COLUMN t_voucher.deleted          IS '逻辑删除(0-未删,1-已删)';

-- 2. 凭证分录表
CREATE TABLE IF NOT EXISTS t_voucher_entry (
    id              BIGINT PRIMARY KEY,
    voucher_id      BIGINT        NOT NULL,
    subject_id      BIGINT        NOT NULL,
    debit           NUMERIC(18,2) NOT NULL DEFAULT 0,
    credit          NUMERIC(18,2) NOT NULL DEFAULT 0,
    summary         VARCHAR(500),
    assist_json     JSONB,
    sort_order      INTEGER       NOT NULL DEFAULT 1,
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_entry_voucher FOREIGN KEY (voucher_id) REFERENCES t_voucher(id) ON DELETE CASCADE,
    CONSTRAINT fk_entry_subject FOREIGN KEY (subject_id) REFERENCES t_subject(id),
    CONSTRAINT chk_entry_amount CHECK (debit >= 0 AND credit >= 0),
    CONSTRAINT chk_entry_not_both_zero CHECK (NOT (debit = 0 AND credit = 0))
);

CREATE INDEX IF NOT EXISTS idx_entry_voucher_id ON t_voucher_entry(voucher_id);
CREATE INDEX IF NOT EXISTS idx_entry_subject_id ON t_voucher_entry(subject_id);

COMMENT ON TABLE  t_voucher_entry              IS '凭证分录表';
COMMENT ON COLUMN t_voucher_entry.id            IS '主键';
COMMENT ON COLUMN t_voucher_entry.voucher_id    IS '凭证ID';
COMMENT ON COLUMN t_voucher_entry.subject_id    IS '科目ID';
COMMENT ON COLUMN t_voucher_entry.debit         IS '借方金额';
COMMENT ON COLUMN t_voucher_entry.credit        IS '贷方金额';
COMMENT ON COLUMN t_voucher_entry.summary       IS '分录摘要';
COMMENT ON COLUMN t_voucher_entry.assist_json   IS '辅助核算信息(JSON)';
COMMENT ON COLUMN t_voucher_entry.sort_order    IS '排序号';

-- 3. 科目余额表
CREATE TABLE IF NOT EXISTS t_subject_balance (
    id              BIGINT PRIMARY KEY,
    subject_id      BIGINT        NOT NULL,
    year            INTEGER       NOT NULL,
    period          VARCHAR(6)    NOT NULL,
    begin_balance   NUMERIC(18,2) NOT NULL DEFAULT 0,
    debit_total     NUMERIC(18,2) NOT NULL DEFAULT 0,
    credit_total    NUMERIC(18,2) NOT NULL DEFAULT 0,
    end_balance     NUMERIC(18,2) NOT NULL DEFAULT 0,
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_balance_subject FOREIGN KEY (subject_id) REFERENCES t_subject(id),
    CONSTRAINT uq_subject_period UNIQUE (subject_id, period)
);

CREATE INDEX IF NOT EXISTS idx_balance_period ON t_subject_balance(period);
CREATE INDEX IF NOT EXISTS idx_balance_subject ON t_subject_balance(subject_id);

COMMENT ON TABLE  t_subject_balance              IS '科目余额表';
COMMENT ON COLUMN t_subject_balance.id            IS '主键';
COMMENT ON COLUMN t_subject_balance.subject_id    IS '科目ID';
COMMENT ON COLUMN t_subject_balance.year          IS '会计年度';
COMMENT ON COLUMN t_subject_balance.period        IS '会计期间(YYYYMM)';
COMMENT ON COLUMN t_subject_balance.begin_balance  IS '期初余额';
COMMENT ON COLUMN t_subject_balance.debit_total    IS '本期借方发生额';
COMMENT ON COLUMN t_subject_balance.credit_total   IS '本期贷方发生额';
COMMENT ON COLUMN t_subject_balance.end_balance    IS '期末余额';
