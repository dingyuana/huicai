-- ============================================================
-- V2: 创建分类规则表 t_classification_rule
-- 银行流水智能分类规则，支持关键词/正则/对方户名匹配
-- ============================================================

CREATE TABLE IF NOT EXISTS t_classification_rule (
    id                  BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id           BIGINT      NOT NULL DEFAULT 1,
    name                VARCHAR(100) NOT NULL,
    rule_type           VARCHAR(50) NOT NULL DEFAULT 'keyword_regex',
    pattern             TEXT        NOT NULL DEFAULT '',
    match_field         VARCHAR(50) NOT NULL DEFAULT 'description',
    direction           VARCHAR(10),
    classification      VARCHAR(50) NOT NULL,
    priority            INTEGER     NOT NULL DEFAULT 0,
    is_active           BOOLEAN     NOT NULL DEFAULT TRUE,
    route_type          VARCHAR(10),
    is_system           BOOLEAN     NOT NULL DEFAULT FALSE,
    debit_subject_id    BIGINT,
    credit_subject_id   BIGINT,
    created_by          BIGINT,
    created_at          TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          BIGINT,
    updated_at          TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             INTEGER     NOT NULL DEFAULT 0,

    CONSTRAINT chk_rule_type CHECK (rule_type IN ('keyword', 'keyword_regex', 'counterparty_match')),
    CONSTRAINT chk_match_field CHECK (match_field IN ('description', 'counterparty')),
    CONSTRAINT chk_direction CHECK (direction IN ('in', 'out')),
    CONSTRAINT chk_route_type CHECK (route_type IN ('A', 'B', 'C'))
);

COMMENT ON TABLE  t_classification_rule IS '分类规则表';
COMMENT ON COLUMN t_classification_rule.id IS '主键';
COMMENT ON COLUMN t_classification_rule.tenant_id IS '租户ID';
COMMENT ON COLUMN t_classification_rule.name IS '规则名称';
COMMENT ON COLUMN t_classification_rule.rule_type IS '匹配类型: keyword/keyword_regex/counterparty_match';
COMMENT ON COLUMN t_classification_rule.pattern IS '匹配模式, keyword_regex用|分隔';
COMMENT ON COLUMN t_classification_rule.match_field IS '匹配字段: description/counterparty';
COMMENT ON COLUMN t_classification_rule.direction IS '方向过滤: in/out/null';
COMMENT ON COLUMN t_classification_rule.classification IS '分类结果';
COMMENT ON COLUMN t_classification_rule.priority IS '优先级, 越小越优先';
COMMENT ON COLUMN t_classification_rule.is_active IS '是否启用';
COMMENT ON COLUMN t_classification_rule.route_type IS '路由类型: A-直接制证/B-生单后制证/C-待人工';
COMMENT ON COLUMN t_classification_rule.is_system IS '是否系统内置兜底规则';
COMMENT ON COLUMN t_classification_rule.debit_subject_id IS '借方科目ID';
COMMENT ON COLUMN t_classification_rule.credit_subject_id IS '贷方科目ID';
COMMENT ON COLUMN t_classification_rule.deleted IS '逻辑删除: 0-正常, 1-删除';

CREATE INDEX IF NOT EXISTS idx_classification_rule_tenant ON t_classification_rule(tenant_id, deleted);
CREATE INDEX IF NOT EXISTS idx_classification_rule_priority ON t_classification_rule(priority);