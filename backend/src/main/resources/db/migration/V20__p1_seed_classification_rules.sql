-- ============================================================
-- V20: P1 分类规则种子数据
-- 8 条种子规则覆盖 80% 常见银行流水（01 章节 §4.3 表格）
-- ============================================================

-- 预查科目 ID（避免硬编码）
-- 注意：若科目不存在则子查询返回 NULL，debit_subject_id/credit_subject_id 为空

-- 1. 银行手续费
INSERT INTO t_classification_rule (id, tenant_id, name, rule_type, pattern, match_field, direction, classification, priority, is_active, debit_subject_id, credit_subject_id, created_at, updated_at, created_by, updated_by, deleted)
OVERRIDING SYSTEM VALUE
VALUES (1, 1, '银行手续费', 'keyword_regex', '手续费|工本费|年费|账户管理费', 'description', 'out', 'bank_fee', 1, TRUE,
        (SELECT id FROM t_subject WHERE code = '6602.01'),
        (SELECT id FROM t_subject WHERE code = '1002'),
        NOW(), NOW(), 1, 1, 0);

-- 2. 利息收入
INSERT INTO t_classification_rule (id, tenant_id, name, rule_type, pattern, match_field, direction, classification, priority, is_active, debit_subject_id, credit_subject_id, created_at, updated_at, created_by, updated_by, deleted)
OVERRIDING SYSTEM VALUE
VALUES (2, 1, '利息收入', 'keyword_regex', '利息|结息|存款利息', 'description', 'in', 'interest_income', 2, TRUE,
        (SELECT id FROM t_subject WHERE code = '1002'),
        (SELECT id FROM t_subject WHERE code = '6602.02'),
        NOW(), NOW(), 1, 1, 0);

-- 3. 业务收款
INSERT INTO t_classification_rule (id, tenant_id, name, rule_type, pattern, match_field, direction, classification, priority, is_active, debit_subject_id, credit_subject_id, created_at, updated_at, created_by, updated_by, deleted)
OVERRIDING SYSTEM VALUE
VALUES (3, 1, '业务收款', 'keyword_regex', '货款', 'description', 'in', 'business_receipt', 3, TRUE,
        NULL, NULL, NOW(), NOW(), 1, 1, 0);

-- 4. 业务付款
INSERT INTO t_classification_rule (id, tenant_id, name, rule_type, pattern, match_field, direction, classification, priority, is_active, debit_subject_id, credit_subject_id, created_at, updated_at, created_by, updated_by, deleted)
OVERRIDING SYSTEM VALUE
VALUES (4, 1, '业务付款', 'keyword_regex', '货款', 'description', 'out', 'business_payment', 4, TRUE,
        NULL, NULL, NOW(), NOW(), 1, 1, 0);

-- 5. 内部转账
INSERT INTO t_classification_rule (id, tenant_id, name, rule_type, pattern, match_field, direction, classification, priority, is_active, debit_subject_id, credit_subject_id, created_at, updated_at, created_by, updated_by, deleted)
OVERRIDING SYSTEM VALUE
VALUES (5, 1, '内部转账', 'keyword_regex', '转账|转存|调拨|上划|下拨', 'description', NULL, 'internal_transfer', 5, TRUE,
        NULL, NULL, NOW(), NOW(), 1, 1, 0);

-- 6. 税务缴费
INSERT INTO t_classification_rule (id, tenant_id, name, rule_type, pattern, match_field, direction, classification, priority, is_active, debit_subject_id, credit_subject_id, created_at, updated_at, created_by, updated_by, deleted)
OVERRIDING SYSTEM VALUE
VALUES (6, 1, '税务缴费', 'keyword_regex', '税|税务|缴税|税金|税款|增值税|所得税|城建税|教育费附加|国家金库|国库|印花', 'description', 'out', 'tax_payment', 6, TRUE,
        (SELECT id FROM t_subject WHERE code = '2221.01'),
        (SELECT id FROM t_subject WHERE code = '1002'),
        NOW(), NOW(), 1, 1, 0);

-- 7. 社保缴费
INSERT INTO t_classification_rule (id, tenant_id, name, rule_type, pattern, match_field, direction, classification, priority, is_active, debit_subject_id, credit_subject_id, created_at, updated_at, created_by, updated_by, deleted)
OVERRIDING SYSTEM VALUE
VALUES (7, 1, '社保缴费', 'keyword_regex', '社保|公积金|养老|医疗|失业|工伤|生育', 'description', 'out', 'social_security', 7, TRUE,
        (SELECT id FROM t_subject WHERE code = '2211.04'),
        (SELECT id FROM t_subject WHERE code = '1002'),
        NOW(), NOW(), 1, 1, 0);

-- 8. 保险费用
INSERT INTO t_classification_rule (id, tenant_id, name, rule_type, pattern, match_field, direction, classification, priority, is_active, debit_subject_id, credit_subject_id, created_at, updated_at, created_by, updated_by, deleted)
OVERRIDING SYSTEM VALUE
VALUES (8, 1, '保险费用', 'keyword_regex', '保险|保费|投保|财产险|责任险|雇主责任险|意外险', 'description', 'out', 'insurance_fee', 8, TRUE,
        (SELECT id FROM t_subject WHERE code = '6602.06'),
        (SELECT id FROM t_subject WHERE code = '1002'),
        NOW(), NOW(), 1, 1, 0);
