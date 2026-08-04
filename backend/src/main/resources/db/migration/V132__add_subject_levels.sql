-- ============================================================
-- V132: 扩展分类规则表和流水表 — 新增三级科目字段
-- 三级科目挂在规则上，规则命中时同步写入流水
-- ============================================================

-- 分类规则表：新增三级科目字段（展示用，不替代 debit/credit_subject_id）
ALTER TABLE t_classification_rule
    ADD COLUMN IF NOT EXISTS subject_level1 VARCHAR(100),
    ADD COLUMN IF NOT EXISTS subject_level2 VARCHAR(100),
    ADD COLUMN IF NOT EXISTS subject_level3 VARCHAR(100);

COMMENT ON COLUMN t_classification_rule.subject_level1 IS '一级科目（展示用）';
COMMENT ON COLUMN t_classification_rule.subject_level2 IS '二级科目（展示用）';
COMMENT ON COLUMN t_classification_rule.subject_level3 IS '三级科目（展示用）';

-- 流水表：新增三级科目字段（规则命中时写入）
ALTER TABLE t_bank_statement
    ADD COLUMN IF NOT EXISTS subject_level1 VARCHAR(100),
    ADD COLUMN IF NOT EXISTS subject_level2 VARCHAR(100),
    ADD COLUMN IF NOT EXISTS subject_level3 VARCHAR(100);

COMMENT ON COLUMN t_bank_statement.subject_level1 IS '一级科目（规则分类写入）';
COMMENT ON COLUMN t_bank_statement.subject_level2 IS '二级科目（规则分类写入）';
COMMENT ON COLUMN t_bank_statement.subject_level3 IS '三级科目（规则分类写入）';

-- 种子规则：补填参考三级科目（仅更新已存在且三字段为空的系统规则）
UPDATE t_classification_rule
SET subject_level1 = '银行',
    subject_level2 = '手续费与利息',
    subject_level3 = '银行手续费'
WHERE classification = 'bank_interest_fee'
  AND is_system = true
  AND subject_level1 IS NULL;

UPDATE t_classification_rule
SET subject_level1 = '业务收款',
    subject_level2 = '主营业务收入',
    subject_level3 = '货款收入'
WHERE classification = 'business_receipt'
  AND is_system = true
  AND subject_level1 IS NULL;

UPDATE t_classification_rule
SET subject_level1 = '业务付款',
    subject_level2 = '主营业务成本',
    subject_level3 = '采购支出'
WHERE classification = 'business_payment'
  AND is_system = true
  AND subject_level1 IS NULL;

UPDATE t_classification_rule
SET subject_level1 = '内部转账',
    subject_level2 = '银行存款',
    subject_level3 = '行内转账'
WHERE classification = 'internal_transfer'
  AND is_system = true
  AND subject_level1 IS NULL;

UPDATE t_classification_rule
SET subject_level1 = '税费扣缴',
    subject_level2 = '应交税费',
    subject_level3 = '增值税'
WHERE classification = 'tax_withholding'
  AND is_system = true
  AND subject_level1 IS NULL;

UPDATE t_classification_rule
SET subject_level1 = '薪酬社保',
    subject_level2 = '应付职工薪酬',
    subject_level3 = '工资与社保'
WHERE classification = 'salary_social'
  AND is_system = true
  AND subject_level1 IS NULL;

UPDATE t_classification_rule
SET subject_level1 = '筹资投资',
    subject_level2 = '短期借款',
    subject_level3 = '银行贷款'
WHERE classification = 'financing_invest'
  AND is_system = true
  AND subject_level1 IS NULL;

UPDATE t_classification_rule
SET subject_level1 = '其他',
    subject_level2 = NULL,
    subject_level3 = NULL
WHERE classification = 'other_unknown'
  AND is_system = true
  AND subject_level1 IS NULL;
