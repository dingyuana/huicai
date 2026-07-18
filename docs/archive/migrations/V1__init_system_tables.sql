-- ============================================================
-- V1: 初始化系统基础表
-- 会计期间、科目、凭证类型、系统参数、常用摘要
-- ============================================================

-- 1. 会计期间表
CREATE TABLE IF NOT EXISTS t_period (
    id          BIGINT PRIMARY KEY,
    year        INTEGER     NOT NULL,
    month       INTEGER     NOT NULL CHECK (month >= 1 AND month <= 12),
    period_code VARCHAR(20) NOT NULL,
    start_date  DATE        NOT NULL,
    end_date    DATE        NOT NULL,
    status      VARCHAR(20) NOT NULL DEFAULT 'open',
    created_by  BIGINT,
    created_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by  BIGINT,
    updated_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted     INTEGER     NOT NULL DEFAULT 0,
    CONSTRAINT uq_period_code UNIQUE (period_code),
    CONSTRAINT chk_period_status CHECK (status IN ('open', 'closed', 'locked'))
);

COMMENT ON TABLE  t_period      IS '会计期间表';
COMMENT ON COLUMN t_period.id           IS '主键';
COMMENT ON COLUMN t_period.year         IS '会计年度';
COMMENT ON COLUMN t_period.month        IS '会计月份(1-12)';
COMMENT ON COLUMN t_period.period_code  IS '期间编码(YYYYMM)';
COMMENT ON COLUMN t_period.start_date   IS '开始日期';
COMMENT ON COLUMN t_period.end_date     IS '结束日期';
COMMENT ON COLUMN t_period.status       IS '状态: open-开启, closed-已结账, locked-已锁定';
COMMENT ON COLUMN t_period.created_by   IS '创建人';
COMMENT ON COLUMN t_period.created_at   IS '创建时间';
COMMENT ON COLUMN t_period.updated_by   IS '更新人';
COMMENT ON COLUMN t_period.updated_at   IS '更新时间';
COMMENT ON COLUMN t_period.deleted      IS '逻辑删除(0-未删,1-已删)';

-- 2. 科目表
CREATE TABLE IF NOT EXISTS t_subject (
    id              BIGINT PRIMARY KEY,
    code            VARCHAR(50)  NOT NULL,
    name            VARCHAR(200) NOT NULL,
    parent_id       BIGINT,
    level           INTEGER      NOT NULL DEFAULT 1,
    direction       VARCHAR(10)  NOT NULL DEFAULT 'debit',
    is_leaf         BOOLEAN      NOT NULL DEFAULT TRUE,
    aux_calc_type   VARCHAR(50),
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    remark          VARCHAR(500),
    created_by      BIGINT,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      BIGINT,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT uq_subject_code UNIQUE (code),
    CONSTRAINT chk_direction CHECK (direction IN ('debit', 'credit')),
    CONSTRAINT chk_aux_calc_type CHECK (aux_calc_type IS NULL OR aux_calc_type IN ('customer', 'vendor', 'department', 'project', 'employee'))
);

COMMENT ON TABLE  t_subject              IS '科目表';
COMMENT ON COLUMN t_subject.id           IS '主键';
COMMENT ON COLUMN t_subject.code         IS '科目编码';
COMMENT ON COLUMN t_subject.name         IS '科目名称';
COMMENT ON COLUMN t_subject.parent_id    IS '父科目ID';
COMMENT ON COLUMN t_subject.level        IS '科目层级(1-一级,2-二级...)';
COMMENT ON COLUMN t_subject.direction    IS '借贷方向: debit-借方, credit-贷方';
COMMENT ON COLUMN t_subject.is_leaf      IS '是否末级科目';
COMMENT ON COLUMN t_subject.aux_calc_type IS '辅助核算类型: customer-客户, vendor-供应商, department-部门, project-项目, employee-员工';
COMMENT ON COLUMN t_subject.is_active    IS '是否启用';
COMMENT ON COLUMN t_subject.remark       IS '备注';
COMMENT ON COLUMN t_subject.created_by   IS '创建人';
COMMENT ON COLUMN t_subject.created_at   IS '创建时间';
COMMENT ON COLUMN t_subject.updated_by   IS '更新人';
COMMENT ON COLUMN t_subject.updated_at   IS '更新时间';
COMMENT ON COLUMN t_subject.deleted      IS '逻辑删除(0-未删,1-已删)';

-- 3. 凭证类型表
CREATE TABLE IF NOT EXISTS t_voucher_type (
    id               BIGINT PRIMARY KEY,
    code             VARCHAR(20)  NOT NULL,
    name             VARCHAR(100) NOT NULL,
    sort_order       INTEGER      NOT NULL DEFAULT 1,
    numbering_rule   VARCHAR(100) NOT NULL DEFAULT '{type}-{year}{month}-{serial}',
    is_active        BOOLEAN      NOT NULL DEFAULT TRUE,
    remark           VARCHAR(500),
    created_by       BIGINT,
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by       BIGINT,
    updated_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted          INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT uq_voucher_type_code UNIQUE (code)
);

COMMENT ON TABLE  t_voucher_type              IS '凭证类型表';
COMMENT ON COLUMN t_voucher_type.id            IS '主键';
COMMENT ON COLUMN t_voucher_type.code          IS '类型编码';
COMMENT ON COLUMN t_voucher_type.name          IS '类型名称(记账凭证/收款凭证/付款凭证/转账凭证)';
COMMENT ON COLUMN t_voucher_type.sort_order    IS '排序号';
COMMENT ON COLUMN t_voucher_type.numbering_rule IS '编号规则';
COMMENT ON COLUMN t_voucher_type.is_active     IS '是否启用';
COMMENT ON COLUMN t_voucher_type.remark        IS '备注';
COMMENT ON COLUMN t_voucher_type.created_by    IS '创建人';
COMMENT ON COLUMN t_voucher_type.created_at    IS '创建时间';
COMMENT ON COLUMN t_voucher_type.updated_by    IS '更新人';
COMMENT ON COLUMN t_voucher_type.updated_at    IS '更新时间';
COMMENT ON COLUMN t_voucher_type.deleted       IS '逻辑删除(0-未删,1-已删)';

-- 4. 系统参数表
CREATE TABLE IF NOT EXISTS t_sys_config (
    id              BIGINT PRIMARY KEY,
    config_key      VARCHAR(100) NOT NULL,
    config_value    TEXT,
    config_type     VARCHAR(50)  NOT NULL DEFAULT 'system',
    description     VARCHAR(500),
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_by      BIGINT,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      BIGINT,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT uq_config_key UNIQUE (config_key),
    CONSTRAINT chk_config_type CHECK (config_type IN ('system', 'business', 'accounting'))
);

COMMENT ON TABLE  t_sys_config              IS '系统参数表';
COMMENT ON COLUMN t_sys_config.id            IS '主键';
COMMENT ON COLUMN t_sys_config.config_key    IS '参数键';
COMMENT ON COLUMN t_sys_config.config_value  IS '参数值';
COMMENT ON COLUMN t_sys_config.config_type   IS '参数类型: system-系统, business-业务, accounting-财务';
COMMENT ON COLUMN t_sys_config.description   IS '参数说明';
COMMENT ON COLUMN t_sys_config.is_active     IS '是否启用';
COMMENT ON COLUMN t_sys_config.created_by    IS '创建人';
COMMENT ON COLUMN t_sys_config.created_at    IS '创建时间';
COMMENT ON COLUMN t_sys_config.updated_by    IS '更新人';
COMMENT ON COLUMN t_sys_config.updated_at    IS '更新时间';
COMMENT ON COLUMN t_sys_config.deleted       IS '逻辑删除(0-未删,1-已删)';

-- 5. 常用摘要库
CREATE TABLE IF NOT EXISTS t_summary_lib (
    id              BIGINT PRIMARY KEY,
    summary_code    VARCHAR(50),
    summary_text    VARCHAR(500) NOT NULL,
    category        VARCHAR(100),
    sort_order      INTEGER      NOT NULL DEFAULT 1,
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_by      BIGINT,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      BIGINT,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT uq_summary_code UNIQUE (summary_code)
);

COMMENT ON TABLE  t_summary_lib              IS '常用摘要库';
COMMENT ON COLUMN t_summary_lib.id            IS '主键';
COMMENT ON COLUMN t_summary_lib.summary_code  IS '摘要编码';
COMMENT ON COLUMN t_summary_lib.summary_text  IS '摘要内容';
COMMENT ON COLUMN t_summary_lib.category      IS '分类(费用/收入/往来/转账等)';
COMMENT ON COLUMN t_summary_lib.sort_order    IS '排序号';
COMMENT ON COLUMN t_summary_lib.is_active     IS '是否启用';
COMMENT ON COLUMN t_summary_lib.created_by    IS '创建人';
COMMENT ON COLUMN t_summary_lib.created_at    IS '创建时间';
COMMENT ON COLUMN t_summary_lib.updated_by    IS '更新人';
COMMENT ON COLUMN t_summary_lib.updated_at    IS '更新时间';
COMMENT ON COLUMN t_summary_lib.deleted       IS '逻辑删除(0-未删,1-已删)';

-- 初始数据: 默认凭证类型
INSERT INTO t_voucher_type (id, code, name, sort_order, numbering_rule) VALUES
(1, 'JZ', '记账凭证', 1, 'JZ-{year}{month}-{serial}')
ON CONFLICT (code) DO NOTHING;

-- 初始数据: 系统参数
INSERT INTO t_sys_config (id, config_key, config_value, config_type, description) VALUES
(1, 'company.name',    '慧财财务',        'system',    '公司名称'),
(2, 'company.tax_id',  '',               'system',    '纳税人识别号'),
(3, 'accounting.start_year', '2026',     'accounting','账套启用年度'),
(4, 'accounting.start_month','1',        'accounting','账套启用月份'),
(5, 'accounting.default_currency', 'CNY','accounting','默认币种')
ON CONFLICT (config_key) DO NOTHING;
