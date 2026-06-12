-- ============================================================
-- V21: 补充 P1 子科目 + 修复 V20 规则科目映射
-- ============================================================
-- 段 1: 补 5 个缺失子科目（6602.01/6602.02/6602.06/2221.01/2211.04）
-- 段 2: 修复 V20 5 条规则中 NULL 的 debit/credit_subject_id
-- ============================================================

BEGIN;

-- ============================================================
-- 段 1: 补 5 个子科目
-- 子科目名称使用 01 章节 §4.6 标准科目名称
-- ============================================================

-- 1. 6602.01 财务费用-手续费（parent=6602 管理费用）
INSERT INTO t_subject (id, code, name, parent_id, level, direction, is_leaf, aux_calc_type, is_active, remark, created_by, created_at, updated_by, updated_at, deleted)
SELECT 100, '6602.01', '财务费用-手续费', t.id, 2, 'debit', TRUE, NULL, TRUE, NULL, 1, NOW(), 1, NOW(), 0
FROM t_subject t WHERE t.code = '6602'
  AND NOT EXISTS (SELECT 1 FROM t_subject s WHERE s.code = '6602.01')
LIMIT 1
ON CONFLICT (code) DO NOTHING;

-- 2. 6602.02 财务费用-利息收入（parent=6602 管理费用）
INSERT INTO t_subject (id, code, name, parent_id, level, direction, is_leaf, aux_calc_type, is_active, remark, created_by, created_at, updated_by, updated_at, deleted)
SELECT 101, '6602.02', '财务费用-利息收入', t.id, 2, 'debit', TRUE, NULL, TRUE, NULL, 1, NOW(), 1, NOW(), 0
FROM t_subject t WHERE t.code = '6602'
  AND NOT EXISTS (SELECT 1 FROM t_subject s WHERE s.code = '6602.02')
LIMIT 1
ON CONFLICT (code) DO NOTHING;

-- 3. 6602.06 管理费用-保险费（parent=6602 管理费用）
INSERT INTO t_subject (id, code, name, parent_id, level, direction, is_leaf, aux_calc_type, is_active, remark, created_by, created_at, updated_by, updated_at, deleted)
SELECT 102, '6602.06', '管理费用-保险费', t.id, 2, 'debit', TRUE, NULL, TRUE, NULL, 1, NOW(), 1, NOW(), 0
FROM t_subject t WHERE t.code = '6602'
  AND NOT EXISTS (SELECT 1 FROM t_subject s WHERE s.code = '6602.06')
LIMIT 1
ON CONFLICT (code) DO NOTHING;

-- 4. 2221.01 应交税费-应交增值税（parent=2221 应交税费）
INSERT INTO t_subject (id, code, name, parent_id, level, direction, is_leaf, aux_calc_type, is_active, remark, created_by, created_at, updated_by, updated_at, deleted)
SELECT 103, '2221.01', '应交税费-应交增值税', t.id, 2, 'credit', TRUE, NULL, TRUE, NULL, 1, NOW(), 1, NOW(), 0
FROM t_subject t WHERE t.code = '2221'
  AND NOT EXISTS (SELECT 1 FROM t_subject s WHERE s.code = '2221.01')
LIMIT 1
ON CONFLICT (code) DO NOTHING;

-- 5. 2211.04 应付职工薪酬-社保（parent=2211 应付职工薪酬）
INSERT INTO t_subject (id, code, name, parent_id, level, direction, is_leaf, aux_calc_type, is_active, remark, created_by, created_at, updated_by, updated_at, deleted)
SELECT 104, '2211.04', '应付职工薪酬-社保', t.id, 2, 'credit', TRUE, NULL, TRUE, NULL, 1, NOW(), 1, NOW(), 0
FROM t_subject t WHERE t.code = '2211'
  AND NOT EXISTS (SELECT 1 FROM t_subject s WHERE s.code = '2211.04')
LIMIT 1
ON CONFLICT (code) DO NOTHING;

-- 更新父科目 is_leaf（有子科目后不再为末级）
UPDATE t_subject SET is_leaf = FALSE
WHERE code IN ('6602', '2221', '2211') AND is_leaf = TRUE;

-- ============================================================
-- 段 2: 修复 V20 规则中因子科目不存在而 NULL 的科目映射
-- ============================================================

-- id=1 银行手续费: 补 debit_subject_id = 6602.01（财务费用-手续费）
UPDATE t_classification_rule
SET debit_subject_id = (SELECT id FROM t_subject WHERE code = '6602.01')
WHERE id = 1;

-- id=2 利息收入: 补 credit_subject_id = 6602.02（财务费用-利息收入）
UPDATE t_classification_rule
SET credit_subject_id = (SELECT id FROM t_subject WHERE code = '6602.02')
WHERE id = 2;

-- id=6 税务缴费: 补 debit_subject_id = 2221.01（应交税费-应交增值税）
UPDATE t_classification_rule
SET debit_subject_id = (SELECT id FROM t_subject WHERE code = '2221.01')
WHERE id = 6;

-- id=7 社保缴费: 补 debit_subject_id = 2211.04（应付职工薪酬-社保）
UPDATE t_classification_rule
SET debit_subject_id = (SELECT id FROM t_subject WHERE code = '2211.04')
WHERE id = 7;

-- id=8 保险费用: 补 debit_subject_id = 6602.06（管理费用-保险费）
UPDATE t_classification_rule
SET debit_subject_id = (SELECT id FROM t_subject WHERE code = '6602.06')
WHERE id = 8;

COMMIT;
