-- ============================================================
-- V112: 创建代理内角色体系表（S-26 V2.0 Sprint 5）
-- t_agency_user: 代理公司内部用户角色
-- t_agency_user_enterprise: 会计-客户分配关系（派工记录）
-- ============================================================

-- 代理公司内部用户表
CREATE TABLE IF NOT EXISTS t_agency_user (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    agency_id       BIGINT        NOT NULL,
    user_id         BIGINT        NOT NULL,
    agency_role     VARCHAR(20)   NOT NULL CHECK (agency_role IN ('AGENCY_ADMIN', 'ACCOUNTANT', 'REVIEWER', 'ASSISTANT')),
    status          VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'SUSPENDED', 'TERMINATED')),
    created_by      BIGINT,
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      BIGINT,
    updated_at      TIMESTAMP,
    deleted         INTEGER       NOT NULL DEFAULT 0,
    version         INTEGER       NOT NULL DEFAULT 0,
    CONSTRAINT fk_au_agency FOREIGN KEY (agency_id) REFERENCES t_agency(id),
    CONSTRAINT fk_au_user FOREIGN KEY (user_id) REFERENCES t_user(id),
    CONSTRAINT uk_au_user UNIQUE (user_id)
);

COMMENT ON TABLE  t_agency_user IS '代理公司内部用户角色';
COMMENT ON COLUMN t_agency_user.agency_id IS '所属代理公司ID';
COMMENT ON COLUMN t_agency_user.user_id IS '关联登录用户ID（一个用户只能属于一个代理公司的一个角色）';
COMMENT ON COLUMN t_agency_user.agency_role IS '代理内角色: AGENCY_ADMIN(经理)/ACCOUNTANT(会计)/REVIEWER(审核员)/ASSISTANT(助理)';
COMMENT ON COLUMN t_agency_user.status IS '状态: ACTIVE(在职)/SUSPENDED(暂停)/TERMINATED(离职)';

CREATE INDEX IF NOT EXISTS idx_au_agency ON t_agency_user(agency_id);
CREATE INDEX IF NOT EXISTS idx_au_user ON t_agency_user(user_id);
CREATE INDEX IF NOT EXISTS idx_au_role ON t_agency_user(agency_role);

-- 会计-客户分配关系表（派工记录）
CREATE TABLE IF NOT EXISTS t_agency_user_enterprise (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    agency_user_id  BIGINT        NOT NULL,
    enterprise_id   BIGINT        NOT NULL,
    assigned_by     BIGINT        NOT NULL,
    assigned_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    unassigned_by   BIGINT,
    unassigned_at   TIMESTAMP,
    deleted         INTEGER       NOT NULL DEFAULT 0,
    CONSTRAINT fk_aue_au FOREIGN KEY (agency_user_id) REFERENCES t_agency_user(id),
    CONSTRAINT fk_aue_ent FOREIGN KEY (enterprise_id) REFERENCES t_enterprise(id),
    CONSTRAINT uk_aue UNIQUE (agency_user_id, enterprise_id)
);

COMMENT ON TABLE  t_agency_user_enterprise IS '会计-客户分配关系（派工记录）';
COMMENT ON COLUMN t_agency_user_enterprise.agency_user_id IS '被分配的代理用户ID';
COMMENT ON COLUMN t_agency_user_enterprise.enterprise_id IS '分配的客户企业ID';
COMMENT ON COLUMN t_agency_user_enterprise.assigned_by IS '分配人用户ID';
COMMENT ON COLUMN t_agency_user_enterprise.assigned_at IS '分配时间';
COMMENT ON COLUMN t_agency_user_enterprise.unassigned_by IS '取消分配人用户ID';
COMMENT ON COLUMN t_agency_user_enterprise.unassigned_at IS '取消分配时间';

CREATE INDEX IF NOT EXISTS idx_aue_au ON t_agency_user_enterprise(agency_user_id);
CREATE INDEX IF NOT EXISTS idx_aue_ent ON t_agency_user_enterprise(enterprise_id);
