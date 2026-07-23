-- ============================================================
-- V107: S-26 Agency 分支 — 客户合同表
-- 关联 SPEC: S-26-agency-branch-development.md §3.6
-- ============================================================

CREATE TABLE IF NOT EXISTS t_contract (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    enterprise_id       BIGINT NOT NULL,
    agency_id           BIGINT NOT NULL,
    contract_no         VARCHAR(64) NOT NULL,
    start_date          DATE NOT NULL,
    end_date            DATE NOT NULL,
    contract_type       VARCHAR(32) NOT NULL DEFAULT 'ACCOUNTING',
    amount              NUMERIC(18,2) NOT NULL DEFAULT 0,
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    renewal_notice_sent BOOLEAN NOT NULL DEFAULT FALSE,
    created_by          BIGINT,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          BIGINT,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             INTEGER NOT NULL DEFAULT 0,
    version             INTEGER NOT NULL DEFAULT 1,
    CONSTRAINT uq_contract_no UNIQUE (contract_no),
    CONSTRAINT chk_contract_status CHECK (status IN ('ACTIVE', 'EXPIRED', 'TERMINATED')),
    CONSTRAINT chk_contract_type CHECK (contract_type IN ('ACCOUNTING', 'TAX', 'CONSULTING', 'OTHER')),
    CONSTRAINT fk_contract_enterprise FOREIGN KEY (enterprise_id) REFERENCES t_enterprise(id),
    CONSTRAINT fk_contract_agency FOREIGN KEY (agency_id) REFERENCES t_agency(id)
);

COMMENT ON TABLE  t_contract                  IS '客户合同表';
COMMENT ON COLUMN t_contract.enterprise_id     IS '客户企业ID';
COMMENT ON COLUMN t_contract.agency_id         IS '代理公司ID';
COMMENT ON COLUMN t_contract.contract_no       IS '合同编号';
COMMENT ON COLUMN t_contract.start_date        IS '合同开始日期';
COMMENT ON COLUMN t_contract.end_date          IS '合同结束日期';
COMMENT ON COLUMN t_contract.contract_type     IS '合同类型: ACCOUNTING/TAX/CONSULTING/OTHER';
COMMENT ON COLUMN t_contract.amount            IS '合同金额';
COMMENT ON COLUMN t_contract.status            IS '状态: ACTIVE/EXPIRED/TERMINATED';
COMMENT ON COLUMN t_contract.renewal_notice_sent IS '续费提醒是否已发送';

CREATE INDEX IF NOT EXISTS idx_t_contract_enterprise ON t_contract(enterprise_id);
CREATE INDEX IF NOT EXISTS idx_t_contract_agency     ON t_contract(agency_id);
CREATE INDEX IF NOT EXISTS idx_t_contract_end_date   ON t_contract(end_date);
