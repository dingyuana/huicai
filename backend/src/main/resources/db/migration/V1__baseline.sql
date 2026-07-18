-- ============================================================
-- V1: 数据库基线 (2026-07-18)
-- 基于 V1-V91 迁移文件合并后的最终 Schema
-- 包含: 系统基础、RBAC、财务核心、业务单据、往来管理、税务、
--       固定资产、预算、AI服务、报表等所有模块
-- ============================================================

-- ============================================================
-- 1. 系统基础表
-- ============================================================

-- 会计期间表
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

-- 科目表
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

-- 凭证类型表
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

-- 系统参数表
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

-- 常用摘要库
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

-- ============================================================
-- 2. RBAC 权限表
-- ============================================================

-- 用户表
CREATE TABLE IF NOT EXISTS t_user (
    id              BIGINT PRIMARY KEY,
    username        VARCHAR(50)  NOT NULL,
    password        VARCHAR(255) NOT NULL,
    real_name       VARCHAR(100),
    phone           VARCHAR(32),
    email           VARCHAR(128),
    dept_id         BIGINT,
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_by      BIGINT,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      BIGINT,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT uq_username UNIQUE (username),
    CONSTRAINT chk_user_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'LOCKED'))
);

COMMENT ON TABLE  t_user                IS '用户表';
COMMENT ON COLUMN t_user.id             IS '主键';
COMMENT ON COLUMN t_user.username       IS '用户名';
COMMENT ON COLUMN t_user.password       IS '密码(BCrypt)';
COMMENT ON COLUMN t_user.real_name      IS '真实姓名';
COMMENT ON COLUMN t_user.phone          IS '手机号';
COMMENT ON COLUMN t_user.email          IS '邮箱';
COMMENT ON COLUMN t_user.dept_id        IS '所属部门ID';
COMMENT ON COLUMN t_user.status         IS '状态: ACTIVE-启用, INACTIVE-禁用, LOCKED-锁定';
COMMENT ON COLUMN t_user.deleted        IS '逻辑删除(0-未删,1-已删)';

-- 角色表
CREATE TABLE IF NOT EXISTS t_role (
    id              BIGINT PRIMARY KEY,
    role_code       VARCHAR(50)  NOT NULL,
    role_name       VARCHAR(100) NOT NULL,
    role_type       VARCHAR(20)  NOT NULL DEFAULT 'NORMAL',
    is_system       BOOLEAN      NOT NULL DEFAULT FALSE,
    remark          VARCHAR(500),
    created_by      BIGINT,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      BIGINT,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT uq_role_code UNIQUE (role_code),
    CONSTRAINT chk_role_type CHECK (role_type IN ('ADMIN', 'NORMAL', 'CUSTOM'))
);

COMMENT ON TABLE  t_role                IS '角色表';
COMMENT ON COLUMN t_role.role_code      IS '角色编码';
COMMENT ON COLUMN t_role.role_name      IS '角色名称';
COMMENT ON COLUMN t_role.role_type      IS '角色类型: ADMIN-管理员, NORMAL-普通, CUSTOM-自定义';
COMMENT ON COLUMN t_role.is_system      IS '是否系统角色';

-- 用户-角色关联表
CREATE TABLE IF NOT EXISTS t_user_role (
    id              BIGINT PRIMARY KEY,
    user_id         BIGINT NOT NULL,
    role_id         BIGINT NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_user_role UNIQUE (user_id, role_id),
    CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES t_user(id),
    CONSTRAINT fk_user_role_role FOREIGN KEY (role_id) REFERENCES t_role(id)
);

COMMENT ON TABLE  t_user_role           IS '用户角色关联表';

-- 菜单表
CREATE TABLE IF NOT EXISTS t_menu (
    id              BIGINT PRIMARY KEY,
    parent_id       BIGINT,
    menu_name       VARCHAR(100) NOT NULL,
    menu_code       VARCHAR(50)  NOT NULL,
    path            VARCHAR(255),
    component       VARCHAR(255),
    icon            VARCHAR(100),
    sort_order      INTEGER      NOT NULL DEFAULT 1,
    menu_type       VARCHAR(20)  NOT NULL DEFAULT 'MENU',
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    permission      VARCHAR(100),
    created_by      BIGINT,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      BIGINT,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT uq_menu_code UNIQUE (menu_code),
    CONSTRAINT chk_menu_type CHECK (menu_type IN ('MENU', 'BUTTON', 'DIR'))
);

COMMENT ON TABLE  t_menu                IS '菜单表';
COMMENT ON COLUMN t_menu.menu_code      IS '菜单编码';
COMMENT ON COLUMN t_menu.menu_type      IS '类型: MENU-菜单, BUTTON-按钮, DIR-目录';
COMMENT ON COLUMN t_menu.permission     IS '权限标识';

-- 角色-菜单关联表
CREATE TABLE IF NOT EXISTS t_role_menu (
    id              BIGINT PRIMARY KEY,
    role_id         BIGINT NOT NULL,
    menu_id         BIGINT NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_role_menu UNIQUE (role_id, menu_id),
    CONSTRAINT fk_role_menu_role FOREIGN KEY (role_id) REFERENCES t_role(id),
    CONSTRAINT fk_role_menu_menu FOREIGN KEY (menu_id) REFERENCES t_menu(id)
);

COMMENT ON TABLE  t_role_menu           IS '角色菜单关联表';

-- 部门表
CREATE TABLE IF NOT EXISTS t_dept (
    id              BIGINT PRIMARY KEY,
    parent_id       BIGINT,
    dept_name       VARCHAR(100) NOT NULL,
    dept_code       VARCHAR(50)  NOT NULL,
    sort_order      INTEGER      NOT NULL DEFAULT 1,
    remark          VARCHAR(500),
    created_by      BIGINT,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      BIGINT,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT uq_dept_code UNIQUE (dept_code)
);

COMMENT ON TABLE  t_dept                IS '部门表';

-- ============================================================
-- 3. 财务核心表
-- ============================================================

-- 凭证主表
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
    business_doc_id BIGINT,
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
    CONSTRAINT chk_voucher_status CHECK (status IN ('DRAFT', 'SUBMITTED', 'AUDITED', 'POSTED', 'CLOSED', 'REVERSED')),
    CONSTRAINT chk_voucher_source CHECK (source IN ('MANUAL', 'TEMPLATE', 'GENERATED', 'REVERSAL')),
    CONSTRAINT fk_voucher_type FOREIGN KEY (voucher_type_id) REFERENCES t_voucher_type(id),
    CONSTRAINT fk_voucher_business_doc FOREIGN KEY (business_doc_id) REFERENCES t_business_doc(id)
);

CREATE INDEX IF NOT EXISTS idx_voucher_period ON t_voucher(period);
CREATE INDEX IF NOT EXISTS idx_voucher_status ON t_voucher(status);
CREATE INDEX IF NOT EXISTS idx_voucher_business_doc_id ON t_voucher(business_doc_id);

COMMENT ON TABLE  t_voucher                IS '凭证主表';
COMMENT ON COLUMN t_voucher.id              IS '主键';
COMMENT ON COLUMN t_voucher.voucher_no      IS '凭证号(格式: 类型+年份+月份+流水号)';
COMMENT ON COLUMN t_voucher.period          IS '会计期间(YYYYMM)';
COMMENT ON COLUMN t_voucher.voucher_type_id IS '凭证类型ID';
COMMENT ON COLUMN t_voucher.status          IS '状态: DRAFT-草稿, SUBMITTED-已提交, AUDITED-已审核, POSTED-已记账, CLOSED-已结账, REVERSED-已红冲';
COMMENT ON COLUMN t_voucher.total_debit     IS '借方总金额';
COMMENT ON COLUMN t_voucher.total_credit    IS '贷方总金额';
COMMENT ON COLUMN t_voucher.summary         IS '摘要';
COMMENT ON COLUMN t_voucher.source          IS '来源: MANUAL-手工录入, TEMPLATE-模板生成, GENERATED-单据生成, REVERSAL-红冲';
COMMENT ON COLUMN t_voucher.attachment_ids  IS '附件ID列表(逗号分隔)';
COMMENT ON COLUMN t_voucher.business_doc_id IS '关联业务单据ID';
COMMENT ON COLUMN t_voucher.created_by      IS '制单人';
COMMENT ON COLUMN t_voucher.created_at      IS '制单时间';
COMMENT ON COLUMN t_voucher.updated_by      IS '更新人';
COMMENT ON COLUMN t_voucher.updated_at      IS '更新时间';
COMMENT ON COLUMN t_voucher.submitted_by    IS '提交人';
COMMENT ON COLUMN t_voucher.submitted_at    IS '提交时间';
COMMENT ON COLUMN t_voucher.audited_by      IS '审核人';
COMMENT ON COLUMN t_voucher.audited_at      IS '审核时间';
COMMENT ON COLUMN t_voucher.posted_by       IS '记账人';
COMMENT ON COLUMN t_voucher.posted_at       IS '记账时间';
COMMENT ON COLUMN t_voucher.reversed_from   IS '被红冲凭证ID';
COMMENT ON COLUMN t_voucher.deleted         IS '逻辑删除(0-未删,1-已删)';

-- 凭证分录表
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
COMMENT ON COLUMN t_voucher_entry.id           IS '主键';
COMMENT ON COLUMN t_voucher_entry.voucher_id   IS '凭证ID';
COMMENT ON COLUMN t_voucher_entry.subject_id   IS '科目ID';
COMMENT ON COLUMN t_voucher_entry.debit        IS '借方金额';
COMMENT ON COLUMN t_voucher_entry.credit       IS '贷方金额';
COMMENT ON COLUMN t_voucher_entry.summary      IS '分录摘要';
COMMENT ON COLUMN t_voucher_entry.assist_json  IS '辅助核算信息(JSON)';
COMMENT ON COLUMN t_voucher_entry.sort_order   IS '排序号';

-- 科目余额表
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
COMMENT ON COLUMN t_subject_balance.id           IS '主键';
COMMENT ON COLUMN t_subject_balance.subject_id   IS '科目ID';
COMMENT ON COLUMN t_subject_balance.year         IS '会计年度';
COMMENT ON COLUMN t_subject_balance.period       IS '会计期间(YYYYMM)';
COMMENT ON COLUMN t_subject_balance.begin_balance IS '期初余额';
COMMENT ON COLUMN t_subject_balance.debit_total   IS '本期借方发生额';
COMMENT ON COLUMN t_subject_balance.credit_total  IS '本期贷方发生额';
COMMENT ON COLUMN t_subject_balance.end_balance   IS '期末余额';

-- ============================================================
-- 4. 业务单据与出纳管理表
-- ============================================================

-- 业务单据主表
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
    invoice_id      BIGINT,
    invoice_no      VARCHAR(64),
    voucher_no      VARCHAR(32),
    settled_amount  NUMERIC(18,2) NOT NULL DEFAULT 0,
    unsettled_amount NUMERIC(18,2) NOT NULL DEFAULT 0,
    due_date        DATE,
    bank_stmt_id    BIGINT,
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
    version         INTEGER       NOT NULL DEFAULT 1,
    CONSTRAINT uq_doc_no_type UNIQUE (doc_type, doc_no),
    CONSTRAINT chk_doc_status CHECK (status IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'VOUCHERED', 'PARTIALLY_RECONCILED', 'FULLY_RECONCILED', 'CLOSED', 'REJECTED', 'REVERSED')),
    CONSTRAINT chk_doc_type CHECK (doc_type IN ('RECEIPT', 'PAYMENT', 'EXPENSE', 'INVOICE_IN', 'INVOICE_OUT', 'OTHER_RECEIVABLE', 'OTHER_PAYABLE', 'PREPAYMENT'))
);

CREATE INDEX IF NOT EXISTS idx_doc_period ON t_business_doc(period);
CREATE INDEX IF NOT EXISTS idx_doc_type ON t_business_doc(doc_type);
CREATE INDEX IF NOT EXISTS idx_doc_status ON t_business_doc(status);
CREATE INDEX IF NOT EXISTS idx_doc_date ON t_business_doc(doc_date);
CREATE INDEX IF NOT EXISTS idx_business_doc_invoice_id ON t_business_doc(invoice_id);
CREATE INDEX IF NOT EXISTS idx_business_doc_settled_amount ON t_business_doc(settled_amount);
CREATE INDEX IF NOT EXISTS idx_business_doc_due_date ON t_business_doc(due_date);

COMMENT ON TABLE  t_business_doc                IS '业务单据主表';
COMMENT ON COLUMN t_business_doc.id             IS '主键';
COMMENT ON COLUMN t_business_doc.doc_no         IS '单据编号';
COMMENT ON COLUMN t_business_doc.doc_type       IS '单据类型';
COMMENT ON COLUMN t_business_doc.doc_date       IS '单据日期';
COMMENT ON COLUMN t_business_doc.period         IS '会计期间';
COMMENT ON COLUMN t_business_doc.amount         IS '单据金额';
COMMENT ON COLUMN t_business_doc.status         IS '状态: DRAFT-草稿, SUBMITTED-已提交, APPROVED-已审批, VOUCHERED-已生成凭证, PARTIALLY_RECONCILED-部分核销, FULLY_RECONCILED-完全核销, CLOSED-已关闭, REJECTED-已驳回, REVERSED-已红冲';
COMMENT ON COLUMN t_business_doc.supplier_id    IS '供应商ID';
COMMENT ON COLUMN t_business_doc.customer_id    IS '客户ID';
COMMENT ON COLUMN t_business_doc.applicant_id   IS '申请人ID';
COMMENT ON COLUMN t_business_doc.dept_id        IS '部门ID';
COMMENT ON COLUMN t_business_doc.summary        IS '摘要';
COMMENT ON COLUMN t_business_doc.source         IS '来源: MANUAL, OCR, IMPORTED';
COMMENT ON COLUMN t_business_doc.ocr_data       IS 'OCR 识别数据';
COMMENT ON COLUMN t_business_doc.attachment_ids IS '附件ID列表';
COMMENT ON COLUMN t_business_doc.voucher_id     IS '生成的凭证ID';
COMMENT ON COLUMN t_business_doc.invoice_id     IS '关联发票ID';
COMMENT ON COLUMN t_business_doc.invoice_no     IS '发票号';
COMMENT ON COLUMN t_business_doc.voucher_no     IS '关联凭证号';
COMMENT ON COLUMN t_business_doc.settled_amount IS '已核销金额';
COMMENT ON COLUMN t_business_doc.unsettled_amount IS '未核销金额';
COMMENT ON COLUMN t_business_doc.due_date       IS '到期日';
COMMENT ON COLUMN t_business_doc.bank_stmt_id   IS '关联银行流水ID';
COMMENT ON COLUMN t_business_doc.reversed_from  IS '被红冲单据ID';
COMMENT ON COLUMN t_business_doc.submitted_by   IS '提交人';
COMMENT ON COLUMN t_business_doc.submitted_at   IS '提交时间';
COMMENT ON COLUMN t_business_doc.approved_by    IS '审批人';
COMMENT ON COLUMN t_business_doc.approved_at    IS '审批时间';
COMMENT ON COLUMN t_business_doc.version        IS '乐观锁版本号';

-- 业务单据分录表
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
COMMENT ON COLUMN t_business_doc_entry.id           IS '主键';
COMMENT ON COLUMN t_business_doc_entry.doc_id       IS '单据ID';
COMMENT ON COLUMN t_business_doc_entry.expense_type IS '费用类别';
COMMENT ON COLUMN t_business_doc_entry.subject_id   IS '科目ID';
COMMENT ON COLUMN t_business_doc_entry.amount       IS '金额';
COMMENT ON COLUMN t_business_doc_entry.invoice_no   IS '发票号';
COMMENT ON COLUMN t_business_doc_entry.assist_json  IS '辅助核算JSON';
COMMENT ON COLUMN t_business_doc_entry.summary      IS '摘要';

-- 凭证模板表
CREATE TABLE IF NOT EXISTS t_voucher_template (
    id              BIGINT PRIMARY KEY,
    template_code   VARCHAR(32)   NOT NULL,
    template_name   VARCHAR(64)   NOT NULL,
    doc_type        VARCHAR(32)   NOT NULL,
    voucher_type_code VARCHAR(20),
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
COMMENT ON COLUMN t_voucher_template.template_code IS '模板编码';
COMMENT ON COLUMN t_voucher_template.template_name IS '模板名称';
COMMENT ON COLUMN t_voucher_template.doc_type IS '适用单据类型';
COMMENT ON COLUMN t_voucher_template.voucher_type_code IS '凭证类型编码';
COMMENT ON COLUMN t_voucher_template.entries IS '分录模板 JSON: [{summary, debitSubjectCode, creditSubjectCode, ...}]';

-- 银行账户表
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

-- 银行日记账表(企业账)
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

-- 银行对账单表(银行账)
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
    category        VARCHAR(50),
    category_source VARCHAR(20)   DEFAULT 'MANUAL',
    review_status   VARCHAR(20)   DEFAULT 'PENDING',
    imported_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    generated_doc_no VARCHAR(32),
    deleted         INTEGER       NOT NULL DEFAULT 0,
    CONSTRAINT fk_statement_account FOREIGN KEY (account_id) REFERENCES t_bank_account(id),
    CONSTRAINT fk_statement_journal FOREIGN KEY (matched_journal_id) REFERENCES t_bank_journal(id),
    CONSTRAINT chk_stmt_type CHECK (tx_type IN ('INCOME', 'EXPENSE', 'TRANSFER_IN', 'TRANSFER_OUT')),
    CONSTRAINT chk_stmt_match_status CHECK (match_status IN ('UNMATCHED', 'MATCHED', 'MANUAL_MATCHED', 'IGNORED')),
    CONSTRAINT chk_stmt_category_source CHECK (category_source IN ('AI', 'MANUAL', 'RULE')),
    CONSTRAINT chk_stmt_review_status CHECK (review_status IN ('PENDING', 'CONFIRMED', 'REJECTED'))
);

CREATE INDEX IF NOT EXISTS idx_stmt_account ON t_bank_statement(account_id);
CREATE INDEX IF NOT EXISTS idx_stmt_date ON t_bank_statement(tx_date);
CREATE INDEX IF NOT EXISTS idx_stmt_match_status ON t_bank_statement(match_status);

COMMENT ON TABLE  t_bank_statement IS '银行对账单(银行账)';
COMMENT ON COLUMN t_bank_statement.category IS '分类';
COMMENT ON COLUMN t_bank_statement.category_source IS '分类来源: AI-智能分类, MANUAL-手工, RULE-规则';
COMMENT ON COLUMN t_bank_statement.review_status IS '复核状态: PENDING-待复核, CONFIRMED-已确认, REJECTED-已驳回';
COMMENT ON COLUMN t_bank_statement.generated_doc_no IS '生成的单据编号';

-- 对账匹配建议表
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

-- ============================================================
-- 5. 往来管理模块
-- ============================================================

-- 客户档案
CREATE TABLE IF NOT EXISTS t_customer (
    id              BIGINT PRIMARY KEY,
    code            VARCHAR(32)  NOT NULL,
    name            VARCHAR(200) NOT NULL,
    contact_person  VARCHAR(64),
    phone           VARCHAR(32),
    email           VARCHAR(128),
    address         VARCHAR(500),
    tax_no          VARCHAR(64),
    bank_name       VARCHAR(128),
    bank_account    VARCHAR(64),
    credit_limit    NUMERIC(18,2) DEFAULT 0,
    credit_days     INTEGER      DEFAULT 30,
    subject_id      BIGINT,
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    remark          VARCHAR(500),
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT uq_customer_code UNIQUE (code),
    CONSTRAINT fk_customer_subject FOREIGN KEY (subject_id) REFERENCES t_subject(id)
);

COMMENT ON TABLE  t_customer IS '客户档案';
COMMENT ON COLUMN t_customer.credit_limit IS '信用额度';
COMMENT ON COLUMN t_customer.credit_days IS '账期(天)';
COMMENT ON COLUMN t_customer.subject_id IS '默认应收科目ID';

-- 供应商档案
CREATE TABLE IF NOT EXISTS t_vendor (
    id              BIGINT PRIMARY KEY,
    code            VARCHAR(32)  NOT NULL,
    name            VARCHAR(200) NOT NULL,
    contact_person  VARCHAR(64),
    phone           VARCHAR(32),
    email           VARCHAR(128),
    address         VARCHAR(500),
    tax_no          VARCHAR(64),
    bank_name       VARCHAR(128),
    bank_account    VARCHAR(64),
    credit_limit    NUMERIC(18,2) DEFAULT 0,
    credit_days     INTEGER      DEFAULT 30,
    subject_id      BIGINT,
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    remark          VARCHAR(500),
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT uq_vendor_code UNIQUE (code),
    CONSTRAINT fk_vendor_subject FOREIGN KEY (subject_id) REFERENCES t_subject(id)
);

COMMENT ON TABLE  t_vendor IS '供应商档案';

-- 核销记录
CREATE TABLE IF NOT EXISTS t_arap_settlement (
    id              BIGINT PRIMARY KEY,
    settlement_no   VARCHAR(32)  NOT NULL,
    settlement_type VARCHAR(20)  NOT NULL,
    settlement_date DATE         NOT NULL,
    period          VARCHAR(6)   NOT NULL,
    party_id        BIGINT       NOT NULL,
    party_type      VARCHAR(20)  NOT NULL,
    total_amount    NUMERIC(18,2) NOT NULL,
    discount_amount NUMERIC(18,2) DEFAULT 0,
    voucher_id      BIGINT,
    status          VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    created_by      BIGINT,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      BIGINT,
    deleted         INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT uq_settlement_no UNIQUE (settlement_no),
    CONSTRAINT chk_settlement_type CHECK (settlement_type IN ('RECEIVE', 'PAY')),
    CONSTRAINT chk_settlement_party_type CHECK (party_type IN ('CUSTOMER', 'VENDOR')),
    CONSTRAINT chk_settlement_status CHECK (status IN ('DRAFT', 'CONFIRMED', 'VOUCHERED', 'REVERSED'))
);

COMMENT ON TABLE  t_arap_settlement IS '核销单';

-- 核销明细
CREATE TABLE IF NOT EXISTS t_arap_settlement_entry (
    id              BIGINT PRIMARY KEY,
    settlement_id   BIGINT       NOT NULL,
    business_doc_id BIGINT,
    settled_amount  NUMERIC(18,2) NOT NULL,
    discount_amount NUMERIC(18,2) DEFAULT 0,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_settle_entry_settle FOREIGN KEY (settlement_id) REFERENCES t_arap_settlement(id) ON DELETE CASCADE,
    CONSTRAINT fk_settle_entry_doc FOREIGN KEY (business_doc_id) REFERENCES t_business_doc(id)
);

CREATE INDEX IF NOT EXISTS idx_settle_entry_settle ON t_arap_settlement_entry(settlement_id);
CREATE INDEX IF NOT EXISTS idx_arap_settlement_entry_business_doc_id ON t_arap_settlement_entry(business_doc_id);

COMMENT ON TABLE  t_arap_settlement_entry IS '核销单明细';

-- 坏账准备
CREATE TABLE IF NOT EXISTS t_bad_debt_provision (
    id              BIGINT PRIMARY KEY,
    period          VARCHAR(6)   NOT NULL,
    method          VARCHAR(20)  NOT NULL,
    provision_date  DATE         NOT NULL,
    total_amount    NUMERIC(18,2) NOT NULL,
    voucher_id      BIGINT,
    status          VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    remark          VARCHAR(500),
    created_by      BIGINT,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT chk_bad_debt_method CHECK (method IN ('AGING_RATIO', 'INDIVIDUAL', 'PERCENTAGE')),
    CONSTRAINT chk_bad_debt_status CHECK (status IN ('DRAFT', 'CONFIRMED', 'VOUCHERED'))
);

COMMENT ON TABLE  t_bad_debt_provision IS '坏账准备计提单';
COMMENT ON COLUMN t_bad_debt_provision.method IS '计提方法: AGING_RATIO-账龄比例法, INDIVIDUAL-个别认定法, PERCENTAGE-余额百分比法';

-- 坏账方案
CREATE TABLE IF NOT EXISTS t_bad_debt_scheme (
    id              BIGINT PRIMARY KEY,
    scheme_code     VARCHAR(32)  NOT NULL,
    scheme_name     VARCHAR(100) NOT NULL,
    method          VARCHAR(20)  NOT NULL,
    aging_rules     JSONB,
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    remark          VARCHAR(500),
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT uq_bad_debt_scheme_code UNIQUE (scheme_code),
    CONSTRAINT chk_bad_debt_scheme_method CHECK (method IN ('AGING_RATIO', 'INDIVIDUAL', 'PERCENTAGE'))
);

COMMENT ON TABLE  t_bad_debt_scheme IS '坏账方案';

-- 坏账明细
CREATE TABLE IF NOT EXISTS t_bad_debt_detail (
    id              BIGINT PRIMARY KEY,
    scheme_id       BIGINT       NOT NULL,
    customer_id     BIGINT,
    doc_id          BIGINT,
    invoice_no      VARCHAR(64),
    amount          NUMERIC(18,2) NOT NULL,
    provision_rate  NUMERIC(8,4),
    provision_amount NUMERIC(18,2) NOT NULL,
    period          VARCHAR(6)   NOT NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_bad_debt_detail_scheme FOREIGN KEY (scheme_id) REFERENCES t_bad_debt_scheme(id)
);

COMMENT ON TABLE  t_bad_debt_detail IS '坏账明细';

-- 应收票据
CREATE TABLE IF NOT EXISTS t_note_receivable (
    id              BIGINT PRIMARY KEY,
    note_no         VARCHAR(64)  NOT NULL,
    customer_id     BIGINT,
    amount          NUMERIC(18,2) NOT NULL,
    issue_date      DATE         NOT NULL,
    due_date        DATE         NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'HOLDING',
    discount_rate   NUMERIC(8,4),
    discount_amount NUMERIC(18,2),
    voucher_id      BIGINT,
    remark          VARCHAR(500),
    created_by      BIGINT,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT uq_note_receivable_no UNIQUE (note_no),
    CONSTRAINT chk_note_receivable_status CHECK (status IN ('HOLDING', 'DISCOUNTED', 'ENDORSED', 'PAID', 'OVERDUE', 'BAD_DEBT'))
);

COMMENT ON TABLE  t_note_receivable IS '应收票据';

-- ============================================================
-- 6. 固定资产模块
-- ============================================================

-- 资产类别表
CREATE TABLE IF NOT EXISTS t_asset_category (
    id              BIGINT PRIMARY KEY,
    code            VARCHAR(32)  NOT NULL,
    name            VARCHAR(100) NOT NULL,
    parent_id       BIGINT,
    level           INTEGER      NOT NULL DEFAULT 1,
    depreciation_method VARCHAR(20) DEFAULT 'STRAIGHT_LINE',
    useful_life     INTEGER      DEFAULT 5,
    residual_rate   NUMERIC(5,4) DEFAULT 0.05,
    asset_subject_id BIGINT,
    depreciation_subject_id BIGINT,
    expense_subject_id BIGINT,
    remark          VARCHAR(500),
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT uq_asset_category_code UNIQUE (code),
    CONSTRAINT chk_dep_method CHECK (depreciation_method IN ('STRAIGHT_LINE', 'DOUBLE_DECLINING', 'SUM_OF_YEARS'))
);

COMMENT ON TABLE  t_asset_category IS '资产类别表';
COMMENT ON COLUMN t_asset_category.depreciation_method IS '折旧方法: STRAIGHT_LINE-平均年限法, DOUBLE_DECLINING-双倍余额法, SUM_OF_YEARS-年数总和法';
COMMENT ON COLUMN t_asset_category.residual_rate IS '残值率';
COMMENT ON COLUMN t_asset_category.asset_subject_id IS '资产科目ID(固定资产)';
COMMENT ON COLUMN t_asset_category.depreciation_subject_id IS '累计折旧科目ID';
COMMENT ON COLUMN t_asset_category.expense_subject_id IS '折旧费用科目ID';

-- 资产卡片表
CREATE TABLE IF NOT EXISTS t_asset_card (
    id              BIGINT PRIMARY KEY,
    asset_code      VARCHAR(32)  NOT NULL,
    asset_name      VARCHAR(200) NOT NULL,
    category_id     BIGINT       NOT NULL,
    spec            VARCHAR(200),
    dept_id         BIGINT,
    custodian_id    BIGINT,
    acquisition_date DATE         NOT NULL,
    original_value  NUMERIC(18,2) NOT NULL,
    residual_value  NUMERIC(18,2) NOT NULL DEFAULT 0,
    useful_life     INTEGER      NOT NULL,
    depreciation_method VARCHAR(20) DEFAULT 'STRAIGHT_LINE',
    status          VARCHAR(20)  NOT NULL DEFAULT 'IN_USE',
    location        VARCHAR(200),
    serial_no       VARCHAR(100),
    remark          VARCHAR(500),
    accumulated_depreciation NUMERIC(18,2) NOT NULL DEFAULT 0,
    net_value       NUMERIC(18,2) NOT NULL DEFAULT 0,
    last_depreciation_period VARCHAR(6),
    voucher_id      BIGINT,
    created_by      BIGINT,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      BIGINT,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT uq_asset_code UNIQUE (asset_code),
    CONSTRAINT fk_asset_category FOREIGN KEY (category_id) REFERENCES t_asset_category(id),
    CONSTRAINT chk_asset_status CHECK (status IN ('DRAFT', 'IN_USE', 'IDLE', 'DISPOSED', 'SCRAPPED'))
);

CREATE INDEX IF NOT EXISTS idx_asset_status ON t_asset_card(status);
CREATE INDEX IF NOT EXISTS idx_asset_category ON t_asset_card(category_id);
CREATE INDEX IF NOT EXISTS idx_asset_acq_date ON t_asset_card(acquisition_date);

COMMENT ON TABLE  t_asset_card IS '资产卡片';
COMMENT ON COLUMN t_asset_card.status IS '状态: DRAFT-草稿, IN_USE-在用, IDLE-闲置, DISPOSED-已处置, SCRAPPED-已报废';
COMMENT ON COLUMN t_asset_card.accumulated_depreciation IS '累计折旧';
COMMENT ON COLUMN t_asset_card.net_value IS '净值';
COMMENT ON COLUMN t_asset_card.last_depreciation_period IS '最后折旧期间(YYYYMM)';

-- 资产变动记录表
CREATE TABLE IF NOT EXISTS t_asset_change (
    id              BIGINT PRIMARY KEY,
    asset_id        BIGINT       NOT NULL,
    change_type     VARCHAR(20)  NOT NULL,
    before_value    VARCHAR(500),
    after_value     VARCHAR(500),
    change_date     DATE         NOT NULL,
    voucher_id      BIGINT,
    remark          VARCHAR(500),
    created_by      BIGINT,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT fk_change_asset FOREIGN KEY (asset_id) REFERENCES t_asset_card(id),
    CONSTRAINT chk_change_type CHECK (change_type IN ('VALUE_ADJUST', 'DEPT_TRANSFER', 'STATUS_CHANGE', 'DEPRECIATION'))
);

CREATE INDEX IF NOT EXISTS idx_change_asset ON t_asset_change(asset_id);
CREATE INDEX IF NOT EXISTS idx_change_date ON t_asset_change(change_date);

COMMENT ON TABLE  t_asset_change IS '资产变动记录';
COMMENT ON COLUMN t_asset_change.change_type IS '变动类型: VALUE_ADJUST-原值调整, DEPT_TRANSFER-部门转移, STATUS_CHANGE-状态变更, DEPRECIATION-折旧';

-- 资产折旧明细表
CREATE TABLE IF NOT EXISTS t_asset_depreciation (
    id              BIGINT PRIMARY KEY,
    asset_id        BIGINT       NOT NULL,
    period          VARCHAR(6)   NOT NULL,
    depreciation_amount NUMERIC(18,2) NOT NULL,
    accumulated_depreciation NUMERIC(18,2) NOT NULL,
    net_value       NUMERIC(18,2) NOT NULL,
    voucher_id      BIGINT,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_dep_asset FOREIGN KEY (asset_id) REFERENCES t_asset_card(id),
    CONSTRAINT uq_dep_asset_period UNIQUE (asset_id, period)
);

CREATE INDEX IF NOT EXISTS idx_dep_period ON t_asset_depreciation(period);

COMMENT ON TABLE  t_asset_depreciation IS '资产折旧明细';

-- 资产盘点表
CREATE TABLE IF NOT EXISTS t_asset_inventory (
    id              BIGINT PRIMARY KEY,
    inventory_no    VARCHAR(32)  NOT NULL,
    inventory_date  DATE         NOT NULL,
    period          VARCHAR(6)   NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    total_count     INTEGER      NOT NULL DEFAULT 0,
    profit_count    INTEGER      NOT NULL DEFAULT 0,
    loss_count      INTEGER      NOT NULL DEFAULT 0,
    voucher_id      BIGINT,
    created_by      BIGINT,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT uq_inventory_no UNIQUE (inventory_no),
    CONSTRAINT chk_inv_status CHECK (status IN ('DRAFT', 'IN_PROGRESS', 'CONFIRMED', 'VOUCHERED'))
);

COMMENT ON TABLE  t_asset_inventory IS '资产盘点单';

-- 资产盘点明细表
CREATE TABLE IF NOT EXISTS t_asset_inventory_entry (
    id              BIGINT PRIMARY KEY,
    inventory_id    BIGINT       NOT NULL,
    asset_id        BIGINT       NOT NULL,
    book_quantity   INTEGER      NOT NULL DEFAULT 1,
    actual_quantity INTEGER      NOT NULL DEFAULT 0,
    diff_quantity   INTEGER      NOT NULL DEFAULT 0,
    diff_type       VARCHAR(10),
    diff_amount     NUMERIC(18,2),
    remark          VARCHAR(500),
    CONSTRAINT fk_inv_entry_inv FOREIGN KEY (inventory_id) REFERENCES t_asset_inventory(id) ON DELETE CASCADE,
    CONSTRAINT fk_inv_entry_asset FOREIGN KEY (asset_id) REFERENCES t_asset_card(id),
    CONSTRAINT chk_diff_type CHECK (diff_type IS NULL OR diff_type IN ('PROFIT', 'LOSS', 'NORMAL'))
);

CREATE INDEX IF NOT EXISTS idx_inv_entry_inv ON t_asset_inventory_entry(inventory_id);

COMMENT ON TABLE  t_asset_inventory_entry IS '资产盘点明细';

-- 资产处置表
CREATE TABLE IF NOT EXISTS t_asset_disposal (
    id              BIGINT PRIMARY KEY,
    disposal_no     VARCHAR(32)  NOT NULL,
    asset_id        BIGINT       NOT NULL,
    disposal_type   VARCHAR(20)  NOT NULL,
    disposal_date   DATE         NOT NULL,
    period          VARCHAR(6)   NOT NULL,
    original_value  NUMERIC(18,2) NOT NULL,
    accumulated_depreciation NUMERIC(18,2) NOT NULL,
    net_value       NUMERIC(18,2) NOT NULL,
    disposal_income NUMERIC(18,2) NOT NULL DEFAULT 0,
    disposal_expense NUMERIC(18,2) NOT NULL DEFAULT 0,
    gain_loss       NUMERIC(18,2) NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    voucher_id      BIGINT,
    created_by      BIGINT,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT uq_disposal_no UNIQUE (disposal_no),
    CONSTRAINT fk_disposal_asset FOREIGN KEY (asset_id) REFERENCES t_asset_card(id),
    CONSTRAINT chk_disposal_type CHECK (disposal_type IN ('SCRAP', 'SALE', 'DONATE', 'INV_LOSS')),
    CONSTRAINT chk_disposal_status CHECK (status IN ('DRAFT', 'APPROVED', 'VOUCHERED'))
);

COMMENT ON TABLE  t_asset_disposal IS '资产处置单';
COMMENT ON COLUMN t_asset_disposal.disposal_type IS '处置类型: SCRAP-报废, SALE-出售, DONATE-捐赠, INV_LOSS-盘亏';
COMMENT ON COLUMN t_asset_disposal.gain_loss IS '处置损益(收入-净值-费用)';

-- ============================================================
-- 7. 税务管理模块
-- ============================================================

-- 税种定义
CREATE TABLE IF NOT EXISTS t_tax_type (
    id              BIGINT PRIMARY KEY,
    code            VARCHAR(32)  NOT NULL,
    name            VARCHAR(100) NOT NULL,
    tax_category    VARCHAR(20)  NOT NULL,
    rate            NUMERIC(8,4) NOT NULL,
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    remark          VARCHAR(500),
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT uq_tax_type_code UNIQUE (code),
    CONSTRAINT chk_tax_category CHECK (tax_category IN ('VAT_INPUT', 'VAT_OUTPUT', 'SURCHARGE', 'INCOME', 'OTHER'))
);

COMMENT ON TABLE  t_tax_type IS '税种定义';
COMMENT ON COLUMN t_tax_type.tax_category IS '税种类别: VAT_INPUT-进项税, VAT_OUTPUT-销项税, SURCHARGE-附加税, INCOME-所得税, OTHER-其他';

-- 进项发票
CREATE TABLE IF NOT EXISTS t_input_invoice (
    id              BIGINT PRIMARY KEY,
    invoice_no      VARCHAR(64)  NOT NULL,
    invoice_date    DATE         NOT NULL,
    period          VARCHAR(6)   NOT NULL,
    vendor_id       BIGINT,
    vendor_name     VARCHAR(200),
    amount          NUMERIC(18,2) NOT NULL,
    tax_rate        NUMERIC(8,4) NOT NULL,
    tax_amount      NUMERIC(18,2) NOT NULL,
    total_amount    NUMERIC(18,2) NOT NULL,
    invoice_type    VARCHAR(20)  NOT NULL,
    certification_status VARCHAR(20) DEFAULT 'UNCERTIFIED',
    certified_date  DATE,
    deduction_period VARCHAR(6),
    deduction_amount NUMERIC(18,2),
    doc_id          BIGINT,
    voucher_id      BIGINT,
    status          VARCHAR(20)  DEFAULT 'PENDING_CONFIRM',
    reject_reason   VARCHAR(500),
    audited_by      BIGINT,
    audited_at      TIMESTAMP,
    remark          VARCHAR(500),
    created_by      BIGINT,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT uq_input_invoice_no UNIQUE (invoice_no),
    CONSTRAINT fk_input_invoice_vendor FOREIGN KEY (vendor_id) REFERENCES t_vendor(id),
    CONSTRAINT chk_invoice_type CHECK (invoice_type IN ('SPECIAL', 'PLAIN', 'CUSTOMS', 'TRANSPORT')),
    CONSTRAINT chk_cert_status CHECK (certification_status IN ('UNCERTIFIED', 'CERTIFIED', 'INVALID', 'CANCELLED')),
    CONSTRAINT chk_input_invoice_status CHECK (status IN ('PENDING_CONFIRM', 'PENDING_REVIEW', 'CONFIRMED', 'VOUCHERED', 'FULLY_RECONCILED', 'PARTIALLY_RECONCILED', 'VOIDED', 'REVERSED'))
);

CREATE INDEX IF NOT EXISTS idx_input_invoice_period ON t_input_invoice(period);
CREATE INDEX IF NOT EXISTS idx_input_invoice_vendor ON t_input_invoice(vendor_id);
CREATE INDEX IF NOT EXISTS idx_input_invoice_cert ON t_input_invoice(certification_status);
CREATE INDEX IF NOT EXISTS idx_input_invoice_status ON t_input_invoice(status);

COMMENT ON TABLE  t_input_invoice IS '进项发票';
COMMENT ON COLUMN t_input_invoice.invoice_type IS '发票类型: SPECIAL-增值税专用, PLAIN-普通, CUSTOMS-海关缴款, TRANSPORT-运输';
COMMENT ON COLUMN t_input_invoice.certification_status IS '认证状态: UNCERTIFIED-未认证, CERTIFIED-已认证, INVALID-无效, CANCELLED-已注销';
COMMENT ON COLUMN t_input_invoice.status IS '审核状态: PENDING_CONFIRM-待确认, PENDING_REVIEW-待审核, CONFIRMED-已确认, VOUCHERED-已生成凭证, FULLY_RECONCILED-完全核销, PARTIALLY_RECONCILED-部分核销, VOIDED-已作废, REVERSED-已红冲';
COMMENT ON COLUMN t_input_invoice.reject_reason IS '审核驳回原因';
COMMENT ON COLUMN t_input_invoice.audited_by IS '审核人ID';
COMMENT ON COLUMN t_input_invoice.audited_at IS '审核时间';

-- 销项发票
CREATE TABLE IF NOT EXISTS t_output_invoice (
    id              BIGINT PRIMARY KEY,
    invoice_no      VARCHAR(64)  NOT NULL,
    invoice_date    DATE         NOT NULL,
    period          VARCHAR(6)   NOT NULL,
    customer_id     BIGINT,
    customer_name   VARCHAR(200),
    amount          NUMERIC(18,2) NOT NULL,
    tax_rate        NUMERIC(8,4) NOT NULL,
    tax_amount      NUMERIC(18,2) NOT NULL,
    total_amount    NUMERIC(18,2) NOT NULL,
    invoice_type    VARCHAR(20)  NOT NULL,
    status          VARCHAR(20)  DEFAULT 'ISSUED',
    receivable_no   VARCHAR(32),
    reversed_by_invoice_id BIGINT,
    doc_id          BIGINT,
    voucher_id      BIGINT,
    remark          VARCHAR(500),
    created_by      BIGINT,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT uq_output_invoice_no UNIQUE (invoice_no),
    CONSTRAINT fk_output_invoice_customer FOREIGN KEY (customer_id) REFERENCES t_customer(id),
    CONSTRAINT chk_output_invoice_type CHECK (invoice_type IN ('SPECIAL', 'PLAIN', 'CUSTOMS')),
    CONSTRAINT chk_output_invoice_status CHECK (status IN ('DRAFT', 'ISSUED', 'VOID', 'RED_INK'))
);

CREATE INDEX IF NOT EXISTS idx_output_invoice_period ON t_output_invoice(period);
CREATE INDEX IF NOT EXISTS idx_output_invoice_customer ON t_output_invoice(customer_id);

COMMENT ON TABLE  t_output_invoice IS '销项发票';
COMMENT ON COLUMN t_output_invoice.receivable_no IS '关联应收编号';
COMMENT ON COLUMN t_output_invoice.reversed_by_invoice_id IS '红字冲销关联发票ID';

-- 税金结转记录
CREATE TABLE IF NOT EXISTS t_tax_carry_over (
    id              BIGINT PRIMARY KEY,
    period          VARCHAR(6)   NOT NULL,
    tax_type        VARCHAR(20)  NOT NULL,
    output_tax      NUMERIC(18,2) DEFAULT 0,
    input_tax       NUMERIC(18,2) DEFAULT 0,
    payable_tax     NUMERIC(18,2) DEFAULT 0,
    surcharge_total NUMERIC(18,2) DEFAULT 0,
    voucher_id      BIGINT,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_carry_tax_type CHECK (tax_type IN ('VAT', 'SURCHARGE')),
    CONSTRAINT uq_carry_period_type UNIQUE (period, tax_type)
);

COMMENT ON TABLE  t_tax_carry_over IS '税金结转记录';

-- 纳税申报
CREATE TABLE IF NOT EXISTS t_tax_declaration (
    id              BIGINT PRIMARY KEY,
    declaration_no  VARCHAR(32)  NOT NULL,
    period          VARCHAR(6)   NOT NULL,
    tax_type        VARCHAR(20)  NOT NULL,
    declared_date   DATE         NOT NULL,
    payable_amount  NUMERIC(18,2) NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    voucher_id      BIGINT,
    remark          VARCHAR(500),
    created_by      BIGINT,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT uq_declaration_no UNIQUE (declaration_no),
    CONSTRAINT chk_declaration_tax_type CHECK (tax_type IN ('VAT', 'SURCHARGE', 'INCOME')),
    CONSTRAINT chk_declaration_status CHECK (status IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'PAID'))
);

COMMENT ON TABLE  t_tax_declaration IS '纳税申报';

-- ============================================================
-- 8. 预算管理模块
-- ============================================================

CREATE TABLE IF NOT EXISTS t_budget (
    id              BIGINT PRIMARY KEY,
    budget_no       VARCHAR(32)  NOT NULL,
    budget_name     VARCHAR(200) NOT NULL,
    period          VARCHAR(6)   NOT NULL,
    budget_type     VARCHAR(20)  NOT NULL,
    total_amount    NUMERIC(18,2) NOT NULL DEFAULT 0,
    used_amount     NUMERIC(18,2) NOT NULL DEFAULT 0,
    status          VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    remark          VARCHAR(500),
    created_by      BIGINT,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT uq_budget_no UNIQUE (budget_no),
    CONSTRAINT chk_budget_type CHECK (budget_type IN ('DEPARTMENT', 'PROJECT', 'SUBJECT', 'OVERALL')),
    CONSTRAINT chk_budget_status CHECK (status IN ('DRAFT', 'APPROVED', 'EXECUTING', 'CLOSED'))
);

COMMENT ON TABLE  t_budget IS '预算表';

-- ============================================================
-- 9. AI服务模块
-- ============================================================

-- AI任务表
CREATE TABLE IF NOT EXISTS t_ai_task (
    id              BIGINT PRIMARY KEY,
    task_no         VARCHAR(32)  NOT NULL,
    task_type       VARCHAR(50)  NOT NULL,
    biz_type        VARCHAR(50),
    biz_id          BIGINT,
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    input_data      JSONB,
    output_data     JSONB,
    error_message   TEXT,
    confidence      NUMERIC(5,4),
    reviewed        BOOLEAN      DEFAULT FALSE,
    reviewed_by     BIGINT,
    reviewed_at     TIMESTAMP,
    apply_status    VARCHAR(20)  DEFAULT 'PENDING',
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at      TIMESTAMP,
    completed_at    TIMESTAMP,
    deleted         INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT uq_ai_task_no UNIQUE (task_no),
    CONSTRAINT chk_ai_task_status CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED'))
);

COMMENT ON TABLE  t_ai_task IS 'AI任务表';

-- AI反馈日志表
CREATE TABLE IF NOT EXISTS t_ai_feedback_log (
    id              BIGINT PRIMARY KEY,
    tenant_id       BIGINT,
    bank_txn_id     BIGINT,
    ai_suggested_action VARCHAR(50),
    ai_confidence   INTEGER,
    ai_business_scene VARCHAR(50),
    human_action    VARCHAR(50),
    human_modified_fields JSONB,
    created_by      BIGINT,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE  t_ai_feedback_log IS 'AI分类反馈日志';

-- ============================================================
-- 10. 报表模块
-- ============================================================

CREATE TABLE IF NOT EXISTS t_report_template (
    id              BIGINT PRIMARY KEY,
    template_code   VARCHAR(32)  NOT NULL,
    template_name   VARCHAR(100) NOT NULL,
    report_type     VARCHAR(20)  NOT NULL,
    config          JSONB,
    is_active       BOOLEAN      DEFAULT TRUE,
    remark          VARCHAR(500),
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT uq_report_template_code UNIQUE (template_code)
);

COMMENT ON TABLE  t_report_template IS '报表模板';

-- ============================================================
-- 11. 核销对账扩展表
-- ============================================================

-- 对账异常记录表
CREATE TABLE IF NOT EXISTS t_reconciliation_exception (
    id              BIGINT PRIMARY KEY,
    account_id      BIGINT       NOT NULL,
    period          VARCHAR(6)   NOT NULL,
    exception_type  VARCHAR(20)  NOT NULL,
    amount          NUMERIC(18,2),
    description     VARCHAR(500),
    status          VARCHAR(20)  DEFAULT 'PENDING',
    resolved_by     BIGINT,
    resolved_at     TIMESTAMP,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT fk_rec_exception_account FOREIGN KEY (account_id) REFERENCES t_bank_account(id),
    CONSTRAINT chk_exception_type CHECK (exception_type IN ('AMOUNT_DIFF', 'DATE_DIFF', 'UNMATCHED', 'DUPLICATE'))
);

COMMENT ON TABLE  t_reconciliation_exception IS '对账异常记录';

-- 核销容差表
CREATE TABLE IF NOT EXISTS t_reconciliation_tolerance (
    id              BIGINT PRIMARY KEY,
    party_type      VARCHAR(20)  NOT NULL,
    party_id        BIGINT,
    tolerance_type  VARCHAR(20)  NOT NULL DEFAULT 'ABSOLUTE',
    tolerance_value NUMERIC(18,2) NOT NULL DEFAULT 0,
    is_active       BOOLEAN      DEFAULT TRUE,
    remark          VARCHAR(500),
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_tolerance_type CHECK (tolerance_type IN ('ABSOLUTE', 'PERCENTAGE'))
);

COMMENT ON TABLE  t_reconciliation_tolerance IS '核销容差配置';

-- ============================================================
-- 12. 辅助核算表
-- ============================================================

-- 辅助核算维度表
CREATE TABLE IF NOT EXISTS t_aux_dimension (
    id              BIGINT PRIMARY KEY,
    dimension_code  VARCHAR(32)  NOT NULL,
    dimension_name  VARCHAR(100) NOT NULL,
    parent_id       BIGINT,
    level           INTEGER      DEFAULT 1,
    is_active       BOOLEAN      DEFAULT TRUE,
    remark          VARCHAR(500),
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT uq_aux_dimension_code UNIQUE (dimension_code)
);

COMMENT ON TABLE  t_aux_dimension IS '辅助核算维度';

-- ============================================================
-- 13. 账户映射规则表
-- ============================================================

CREATE TABLE IF NOT EXISTS t_account_mapping_rule (
    id              BIGINT PRIMARY KEY,
    rule_code       VARCHAR(32)  NOT NULL,
    rule_name       VARCHAR(100) NOT NULL,
    source_type     VARCHAR(20)  NOT NULL,
    target_subject_id BIGINT,
    match_pattern   VARCHAR(500),
    ai_result       JSONB,
    is_active       BOOLEAN      DEFAULT TRUE,
    remark          VARCHAR(500),
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT uq_account_mapping_rule_code UNIQUE (rule_code),
    CONSTRAINT fk_mapping_subject FOREIGN KEY (target_subject_id) REFERENCES t_subject(id)
);

COMMENT ON TABLE  t_account_mapping_rule IS '账户映射规则';

-- ============================================================
-- 14. 账龄预警表
-- ============================================================

CREATE TABLE IF NOT EXISTS t_aging_alert (
    id              BIGINT PRIMARY KEY,
    doc_id          BIGINT       NOT NULL,
    doc_type        VARCHAR(20)  NOT NULL,
    party_id        BIGINT,
    party_type      VARCHAR(20)  NOT NULL,
    due_date        DATE         NOT NULL,
    days_overdue    INTEGER      NOT NULL,
    amount          NUMERIC(18,2) NOT NULL,
    alert_level     VARCHAR(20)  NOT NULL DEFAULT 'WARNING',
    status          VARCHAR(20)  DEFAULT 'ACTIVE',
    processed_by    BIGINT,
    processed_at    TIMESTAMP,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_aging_alert_doc FOREIGN KEY (doc_id) REFERENCES t_business_doc(id),
    CONSTRAINT chk_aging_alert_level CHECK (alert_level IN ('INFO', 'WARNING', 'CRITICAL'))
);

CREATE INDEX IF NOT EXISTS idx_aging_alert_due_date ON t_aging_alert(due_date);

COMMENT ON TABLE  t_aging_alert IS '账龄预警';

-- ============================================================
-- 15. 客户对账单表
-- ============================================================

CREATE TABLE IF NOT EXISTS t_customer_statement (
    id              BIGINT PRIMARY KEY,
    statement_no    VARCHAR(32)  NOT NULL,
    customer_id     BIGINT       NOT NULL,
    period_start    DATE         NOT NULL,
    period_end      DATE         NOT NULL,
    period          VARCHAR(6)   NOT NULL,
    opening_balance NUMERIC(18,2) NOT NULL DEFAULT 0,
    closing_balance NUMERIC(18,2) NOT NULL DEFAULT 0,
    status          VARCHAR(20)  DEFAULT 'DRAFT',
    sent_at         TIMESTAMP,
    remark          VARCHAR(500),
    created_by      BIGINT,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT uq_customer_statement_no UNIQUE (statement_no),
    CONSTRAINT fk_customer_statement_customer FOREIGN KEY (customer_id) REFERENCES t_customer(id),
    CONSTRAINT chk_customer_statement_status CHECK (status IN ('DRAFT', 'GENERATED', 'SENT', 'CONFIRMED'))
);

COMMENT ON TABLE  t_customer_statement IS '客户对账单';

-- ============================================================
-- 16. 审计日志表
-- ============================================================

CREATE TABLE IF NOT EXISTS t_audit_log (
    id              BIGINT PRIMARY KEY,
    module          VARCHAR(50)  NOT NULL,
    operation       VARCHAR(50)  NOT NULL,
    entity_type     VARCHAR(100),
    entity_id       BIGINT,
    entity_no       VARCHAR(100),
    before_data     JSONB,
    after_data      JSONB,
    operator_id     BIGINT,
    operator_name   VARCHAR(100),
    operation_time  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ip_address      VARCHAR(50),
    deleted         INTEGER      NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_audit_log_entity ON t_audit_log(entity_type, entity_id);
CREATE INDEX IF NOT EXISTS idx_audit_log_operation_time ON t_audit_log(operation_time);

COMMENT ON TABLE  t_audit_log IS '审计日志';

-- ============================================================
-- 17. 费用报销表
-- ============================================================

CREATE TABLE IF NOT EXISTS t_expense_reimbursement (
    id              BIGINT PRIMARY KEY,
    reimb_no        VARCHAR(32)  NOT NULL,
    applicant_id    BIGINT       NOT NULL,
    dept_id         BIGINT,
    total_amount    NUMERIC(18,2) NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    reimb_type      VARCHAR(20)  NOT NULL,
    summary         VARCHAR(500),
    doc_id          BIGINT,
    voucher_id      BIGINT,
    created_by      BIGINT,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      BIGINT,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT uq_reimb_no UNIQUE (reimb_no),
    CONSTRAINT chk_reimb_status CHECK (status IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'REJECTED', 'VOUCHERED')),
    CONSTRAINT chk_reimb_type CHECK (reimb_type IN ('TRAVEL', 'ENTERTAINMENT', 'OFFICE', 'OTHER'))
);

COMMENT ON TABLE  t_expense_reimbursement IS '费用报销单';

-- ============================================================
-- 18. 员工表
-- ============================================================

CREATE TABLE IF NOT EXISTS t_employee (
    id              BIGINT PRIMARY KEY,
    emp_code        VARCHAR(32)  NOT NULL,
    emp_name        VARCHAR(100) NOT NULL,
    dept_id         BIGINT,
    position        VARCHAR(100),
    phone           VARCHAR(32),
    email           VARCHAR(128),
    subject_id      BIGINT,
    is_active       BOOLEAN      DEFAULT TRUE,
    remark          VARCHAR(500),
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT uq_emp_code UNIQUE (emp_code),
    CONSTRAINT fk_employee_subject FOREIGN KEY (subject_id) REFERENCES t_subject(id)
);

COMMENT ON TABLE  t_employee IS '员工档案';

-- ============================================================
-- 初始数据
-- ============================================================

-- 默认凭证类型
INSERT INTO t_voucher_type (id, code, name, sort_order, numbering_rule) VALUES
(1, 'JZ', '记账凭证', 1, 'JZ-{year}{month}-{serial}')
ON CONFLICT (code) DO NOTHING;

-- 系统参数
INSERT INTO t_sys_config (id, config_key, config_value, config_type, description) VALUES
(1, 'company.name',    '慧财财务',        'system',    '公司名称'),
(2, 'company.tax_id',  '',               'system',    '纳税人识别号'),
(3, 'accounting.start_year', '2026',     'accounting','账套启用年度'),
(4, 'accounting.start_month','1',        'accounting','账套启用月份'),
(5, 'accounting.default_currency', 'CNY','accounting','默认币种')
ON CONFLICT (config_key) DO NOTHING;

-- 初始化管理员用户 (密码: admin123 BCrypt)
INSERT INTO t_user (id, username, password, real_name, status) VALUES
(1, 'admin', '$2a$10$N9qo8uLOickgx2ZMRZoMye.IjzqAKL9xL5jvMFVdNJHvGCgTq/VEq', '系统管理员', 'ACTIVE')
ON CONFLICT (username) DO NOTHING;

-- 初始化管理员角色
INSERT INTO t_role (id, role_code, role_name, role_type, is_system) VALUES
(1, 'ADMIN', '管理员', 'ADMIN', TRUE)
ON CONFLICT (role_code) DO NOTHING;

-- 关联管理员用户与角色
INSERT INTO t_user_role (id, user_id, role_id) VALUES
(1, 1, 1)
ON CONFLICT (user_id, role_id) DO NOTHING;
