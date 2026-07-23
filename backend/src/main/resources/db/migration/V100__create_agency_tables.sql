-- ============================================================
-- V100: S-26 Agency 分支 — 新建三张管理表
-- 关联 SPEC: S-26-agency-branch-development.md
-- 关联架构: 多租户架构设计.md §2.1
-- 关联 REQ: REQ-2026-066~075
-- ============================================================

-- 1. 代理公司表
CREATE TABLE IF NOT EXISTS t_agency (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    agency_code     VARCHAR(32)  NOT NULL,
    agency_name     VARCHAR(200) NOT NULL,
    contact_name    VARCHAR(100),
    contact_phone   VARCHAR(32),
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    created_by      BIGINT,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      BIGINT,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         INTEGER      NOT NULL DEFAULT 0,
    version         INTEGER      NOT NULL DEFAULT 1,
    CONSTRAINT uq_agency_code UNIQUE (agency_code),
    CONSTRAINT chk_agency_status CHECK (status IN ('PENDING', 'ACTIVE', 'SUSPENDED', 'TERMINATED'))
);

COMMENT ON TABLE  t_agency                IS '代理公司表';
COMMENT ON COLUMN t_agency.agency_code    IS '代理公司编码';
COMMENT ON COLUMN t_agency.agency_name    IS '代理公司名称';
COMMENT ON COLUMN t_agency.contact_name   IS '联系人姓名';
COMMENT ON COLUMN t_agency.contact_phone  IS '联系人电话';
COMMENT ON COLUMN t_agency.status         IS '状态: PENDING/ACTIVE/SUSPENDED/TERMINATED';

-- 2. 企业（账套）表
CREATE TABLE IF NOT EXISTS t_enterprise (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    enterprise_code VARCHAR(32)  NOT NULL,
    enterprise_name VARCHAR(200) NOT NULL,
    tax_id          VARCHAR(32),
    mode            VARCHAR(20)  NOT NULL DEFAULT 'SME',
    agency_id       BIGINT,
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    seed_data_done  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_by      BIGINT,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      BIGINT,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         INTEGER      NOT NULL DEFAULT 0,
    version         INTEGER      NOT NULL DEFAULT 1,
    CONSTRAINT uq_enterprise_code UNIQUE (enterprise_code),
    CONSTRAINT chk_enterprise_mode CHECK (mode IN ('SME', 'AGENCY_CLIENT')),
    CONSTRAINT chk_enterprise_status CHECK (status IN ('PENDING', 'ACTIVE', 'SUSPENDED', 'TERMINATED')),
    CONSTRAINT fk_enterprise_agency FOREIGN KEY (agency_id) REFERENCES t_agency(id)
);

COMMENT ON TABLE  t_enterprise                 IS '企业（账套）表';
COMMENT ON COLUMN t_enterprise.enterprise_code  IS '企业编码';
COMMENT ON COLUMN t_enterprise.enterprise_name  IS '企业名称';
COMMENT ON COLUMN t_enterprise.tax_id           IS '纳税人识别号';
COMMENT ON COLUMN t_enterprise.mode             IS '模式: SME/AGENCY_CLIENT';
COMMENT ON COLUMN t_enterprise.agency_id        IS '所属代理公司';
COMMENT ON COLUMN t_enterprise.status           IS '状态: PENDING/ACTIVE/SUSPENDED/TERMINATED';
COMMENT ON COLUMN t_enterprise.seed_data_done   IS '种子数据是否已初始化';

-- 3. 代理-企业绑定关系表
CREATE TABLE IF NOT EXISTS t_agency_enterprise (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    agency_id       BIGINT NOT NULL,
    enterprise_id   BIGINT NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_agency_enterprise UNIQUE (agency_id, enterprise_id),
    CONSTRAINT fk_ae_agency FOREIGN KEY (agency_id) REFERENCES t_agency(id),
    CONSTRAINT fk_ae_enterprise FOREIGN KEY (enterprise_id) REFERENCES t_enterprise(id),
    CONSTRAINT chk_ae_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

COMMENT ON TABLE  t_agency_enterprise               IS '代理-企业绑定关系表';
COMMENT ON COLUMN t_agency_enterprise.agency_id      IS '代理公司ID';
COMMENT ON COLUMN t_agency_enterprise.enterprise_id  IS '企业ID';
COMMENT ON COLUMN t_agency_enterprise.status         IS '绑定状态: ACTIVE/INACTIVE';
