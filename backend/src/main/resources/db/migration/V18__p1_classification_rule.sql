-- ============================================================
-- V18: P1 分类规则表
-- 银行流水智能分类规则引擎配置表 (SPEC §1.1)
-- ============================================================

CREATE TABLE IF NOT EXISTS t_classification_rule (
    id                  BIGINT PRIMARY KEY,
    tenant_id           BIGINT        NOT NULL,
    name                VARCHAR(100)  NOT NULL,
    rule_type           VARCHAR(30)   NOT NULL DEFAULT 'keyword',
    pattern             TEXT          NOT NULL,
    match_field         VARCHAR(30)   NOT NULL DEFAULT 'description',
    direction           VARCHAR(10),
    classification      VARCHAR(50)   NOT NULL,
    priority            INTEGER       NOT NULL DEFAULT 0,
    is_active           BOOLEAN       NOT NULL DEFAULT TRUE,
    debit_subject_id    BIGINT,
    credit_subject_id   BIGINT,
    created_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by          BIGINT,
    updated_by          BIGINT,
    deleted             INTEGER       NOT NULL DEFAULT 0,
    CONSTRAINT fk_cls_rule_debit_subject  FOREIGN KEY (debit_subject_id)  REFERENCES t_subject(id),
    CONSTRAINT fk_cls_rule_credit_subject FOREIGN KEY (credit_subject_id) REFERENCES t_subject(id),
    CONSTRAINT chk_cls_rule_type       CHECK (rule_type IN ('keyword', 'keyword_regex', 'counterparty_match')),
    CONSTRAINT chk_cls_rule_match_field CHECK (match_field IN ('description', 'counterparty')),
    CONSTRAINT chk_cls_rule_direction   CHECK (direction IN ('in', 'out') OR direction IS NULL)
);

CREATE INDEX IF NOT EXISTS idx_cls_rule_tenant     ON t_classification_rule(tenant_id, is_active, priority);
CREATE INDEX IF NOT EXISTS idx_cls_rule_debit_sub  ON t_classification_rule(debit_subject_id);
CREATE INDEX IF NOT EXISTS idx_cls_rule_credit_sub ON t_classification_rule(credit_subject_id);

COMMENT ON TABLE  t_classification_rule IS 'P1 银行流水智能分类规则';

COMMENT ON COLUMN t_classification_rule.id                IS '主键';
COMMENT ON COLUMN t_classification_rule.tenant_id         IS '租户 ID';
COMMENT ON COLUMN t_classification_rule.name              IS '规则名称, 如"银行手续费"';
COMMENT ON COLUMN t_classification_rule.rule_type         IS '匹配类型: keyword/keyword_regex/counterparty_match';
COMMENT ON COLUMN t_classification_rule.pattern           IS '匹配模式, keyword_regex 用 | 分隔';
COMMENT ON COLUMN t_classification_rule.match_field       IS '匹配字段: description/counterparty';
COMMENT ON COLUMN t_classification_rule.direction         IS '方向过滤: in/out/不限';
COMMENT ON COLUMN t_classification_rule.classification    IS '分类结果: bank_fee/interest_income/...';
COMMENT ON COLUMN t_classification_rule.priority          IS '优先级, 数字越小越优先';
COMMENT ON COLUMN t_classification_rule.is_active         IS '是否启用';
COMMENT ON COLUMN t_classification_rule.debit_subject_id  IS '借方科目 ID (自动凭证)';
COMMENT ON COLUMN t_classification_rule.credit_subject_id IS '贷方科目 ID (自动凭证)';
COMMENT ON COLUMN t_classification_rule.created_at        IS '创建时间';
COMMENT ON COLUMN t_classification_rule.updated_at        IS '更新时间';
COMMENT ON COLUMN t_classification_rule.created_by        IS '创建人';
COMMENT ON COLUMN t_classification_rule.updated_by        IS '更新人';
COMMENT ON COLUMN t_classification_rule.deleted           IS '逻辑删除(0-未删,1-已删)';