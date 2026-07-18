-- V23__add_voucher_template_tables.sql
-- 凭证模板表: 配置驱动的科目映射 (替代 AutoGenerationService 中的硬编码科目)
-- 设计参考: huihua-finance Go 版本 (migrations/012_voucher_template.sql + 027_voucher_template_classification.sql)
-- 适配单租户架构: 移除 tenant_id 字段
-- 注意: 旧版 t_voucher_template (有 entries JSONB 列) 是早期骨架代码, 从未正式使用, 直接 DROP 重建

DROP TABLE IF EXISTS t_voucher_template_line CASCADE;
DROP TABLE IF EXISTS t_voucher_template CASCADE;

-- 凭证模板主表
CREATE TABLE t_voucher_template (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name            VARCHAR(100)  NOT NULL,
    description     VARCHAR(500),
    classification  VARCHAR(50),   -- 绑定的分类: bank_fee / interest_income / tax_payment / ...
    number_prefix   VARCHAR(20)   NOT NULL DEFAULT 'JZ',  -- 凭证前缀, 支持 CD-/FPS-/JZ- 等
    is_active       BOOLEAN       NOT NULL DEFAULT TRUE,
    created_by      BIGINT,
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      BIGINT,
    updated_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         INTEGER       NOT NULL DEFAULT 0,
    CONSTRAINT uq_voucher_template_name UNIQUE (name)
);

-- 每个分类只能有 1 个激活模板 (参考 Go 027 迁移)
CREATE UNIQUE INDEX IF NOT EXISTS uq_voucher_template_classification_active
    ON t_voucher_template (classification)
    WHERE classification IS NOT NULL AND is_active = TRUE AND deleted = 0;

CREATE INDEX IF NOT EXISTS idx_voucher_template_classification ON t_voucher_template (classification) WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_voucher_template_active ON t_voucher_template (is_active) WHERE is_active = TRUE;

-- 凭证模板分录行
CREATE TABLE IF NOT EXISTS t_voucher_template_line (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    template_id         BIGINT        NOT NULL,
    subject_id          BIGINT        NOT NULL,           -- 借贷科目 ID
    dr_amount_template  VARCHAR(100),                     -- 借方金额表达式: e.g. "{{amount}}"
    cr_amount_template  VARCHAR(100),                     -- 贷方金额表达式: e.g. "{{amount}}" 或 ""
    summary_template    VARCHAR(200),                     -- 摘要模板: e.g. "银行手续费 {{summary}}"
    direction           VARCHAR(10)  NOT NULL DEFAULT 'debit',  -- debit / credit (指定行是借还是贷, 用于行级借贷方向)
    line_order          INTEGER       NOT NULL DEFAULT 1,
    created_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             INTEGER       NOT NULL DEFAULT 0,
    CONSTRAINT fk_vtl_template FOREIGN KEY (template_id) REFERENCES t_voucher_template(id) ON DELETE CASCADE,
    CONSTRAINT fk_vtl_subject  FOREIGN KEY (subject_id)  REFERENCES t_subject(id),
    CONSTRAINT chk_vtl_direction CHECK (direction IN ('debit', 'credit'))
);

CREATE INDEX IF NOT EXISTS idx_voucher_template_line_template ON t_voucher_template_line (template_id);

-- t_voucher 加追溯字段: 记录该凭证从哪个模板生成
ALTER TABLE t_voucher ADD COLUMN IF NOT EXISTS template_id BIGINT;
CREATE INDEX IF NOT EXISTS idx_voucher_template_id ON t_voucher (template_id);

-- 预置 5 个常用模板 (覆盖 P0 场景: 银行费用 / 利息收入 / 税务 / 社保 / 保险)
-- 财务经理可通过前端调整; 这里只是初始化默认值
INSERT INTO t_voucher_template (name, description, classification, number_prefix, is_active) VALUES
    ('银行手续费',     '银行手续费支出: 借 财务费用-手续费 6602.01, 贷 银行存款 1002',  'bank_fee',       'JZ', TRUE),
    ('存款利息收入',   '银行结息收入: 借 银行存款 1002, 贷 财务费用-利息收入 6602.02', 'interest_income','JZ', TRUE),
    ('税务缴费',       '缴税: 借 应交税费 2221, 贷 银行存款 1002',                     'tax_payment',    'JZ', TRUE),
    ('社保缴费',       '缴社保: 借 应付职工薪酬-社保 2211, 贷 银行存款 1002',          'social_security','JZ', TRUE),
    ('保险费用',       '保险费: 借 管理费用-保险费 6602.06, 贷 银行存款 1002',         'insurance_fee',  'JZ', TRUE)
ON CONFLICT (name) DO NOTHING;

-- 为上述 5 个模板各插 2 行 (借 + 贷)
-- 银行手续费: 借 6602.01, 贷 1002
INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, line_order)
SELECT t.id, s.id, '{{amount}}', '', '银行手续费: {{summary}}', 'debit', 1
FROM t_voucher_template t, t_subject s
WHERE t.name = '银行手续费' AND s.code = '6602.01'
  AND NOT EXISTS (SELECT 1 FROM t_voucher_template_line WHERE template_id = t.id);

INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, line_order)
SELECT t.id, s.id, '', '{{amount}}', '银行手续费: {{summary}}', 'credit', 2
FROM t_voucher_template t, t_subject s
WHERE t.name = '银行手续费' AND s.code = '1002'
  AND NOT EXISTS (SELECT 1 FROM t_voucher_template_line WHERE template_id = t.id);

-- 利息收入: 借 1002, 贷 6602.02
INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, line_order)
SELECT t.id, s.id, '{{amount}}', '', '存款利息: {{summary}}', 'debit', 1
FROM t_voucher_template t, t_subject s
WHERE t.name = '存款利息收入' AND s.code = '1002'
  AND NOT EXISTS (SELECT 1 FROM t_voucher_template_line WHERE template_id = t.id);

INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, line_order)
SELECT t.id, s.id, '', '{{amount}}', '存款利息: {{summary}}', 'credit', 2
FROM t_voucher_template t, t_subject s
WHERE t.name = '存款利息收入' AND s.code = '6602.02'
  AND NOT EXISTS (SELECT 1 FROM t_voucher_template_line WHERE template_id = t.id);

-- 税务: 借 2221, 贷 1002
INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, line_order)
SELECT t.id, s.id, '{{amount}}', '', '缴税: {{summary}}', 'debit', 1
FROM t_voucher_template t, t_subject s
WHERE t.name = '税务缴费' AND s.code = '2221'
  AND NOT EXISTS (SELECT 1 FROM t_voucher_template_line WHERE template_id = t.id);

INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, line_order)
SELECT t.id, s.id, '', '{{amount}}', '缴税: {{summary}}', 'credit', 2
FROM t_voucher_template t, t_subject s
WHERE t.name = '税务缴费' AND s.code = '1002'
  AND NOT EXISTS (SELECT 1 FROM t_voucher_template_line WHERE template_id = t.id);

-- 社保: 借 2211, 贷 1002
INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, line_order)
SELECT t.id, s.id, '{{amount}}', '', '缴社保: {{summary}}', 'debit', 1
FROM t_voucher_template t, t_subject s
WHERE t.name = '社保缴费' AND s.code = '2211'
  AND NOT EXISTS (SELECT 1 FROM t_voucher_template_line WHERE template_id = t.id);

INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, line_order)
SELECT t.id, s.id, '', '{{amount}}', '缴社保: {{summary}}', 'credit', 2
FROM t_voucher_template t, t_subject s
WHERE t.name = '社保缴费' AND s.code = '1002'
  AND NOT EXISTS (SELECT 1 FROM t_voucher_template_line WHERE template_id = t.id);

-- 保险: 借 6602.06, 贷 1002 (如果 6602.06 不存在, 退到 6602)
INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, line_order)
SELECT t.id, s.id, '{{amount}}', '', '保险费: {{summary}}', 'debit', 1
FROM t_voucher_template t
JOIN t_subject s ON s.code IN ('6602.06', '6602') AND s.code = (SELECT code FROM t_subject WHERE code IN ('6602.06', '6602') ORDER BY code LIMIT 1)
WHERE t.name = '保险费用'
  AND NOT EXISTS (SELECT 1 FROM t_voucher_template_line WHERE template_id = t.id);

INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, line_order)
SELECT t.id, s.id, '', '{{amount}}', '保险费: {{summary}}', 'credit', 2
FROM t_voucher_template t, t_subject s
WHERE t.name = '保险费用' AND s.code = '1002'
  AND NOT EXISTS (SELECT 1 FROM t_voucher_template_line WHERE template_id = t.id);
