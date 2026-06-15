-- ============================================================
-- V26: P0.4 兜底启发式关键词迁移到 DB
-- 将 FallbackHeuristicService 中的硬编码关键词写入规则表,
-- 优先级 100-108 (低于种子规则的 1-8), 用户可在管理页查看和修改.
-- ============================================================

BEGIN;

-- 1. 银行手续费兜底 (priority 100, direction out)
INSERT INTO t_classification_rule (id, tenant_id, name, rule_type, pattern, match_field, direction, classification, priority, is_active,
                                   debit_subject_id, credit_subject_id, created_at, updated_at, created_by, updated_by, deleted)
VALUES (9, 1, '[兜底] 银行手续费', 'keyword_regex', '手续费|工本费|年费|账户管理费', 'description', 'out', 'bank_fee', 100, TRUE,
        (SELECT id FROM t_subject WHERE code = '6602.01'),
        (SELECT id FROM t_subject WHERE code = '1002'),
        NOW(), NOW(), 1, 1, 0);

-- 2. 利息收入兜底 (priority 101, direction in)
INSERT INTO t_classification_rule (id, tenant_id, name, rule_type, pattern, match_field, direction, classification, priority, is_active,
                                   debit_subject_id, credit_subject_id, created_at, updated_at, created_by, updated_by, deleted)
VALUES (10, 1, '[兜底] 利息收入', 'keyword_regex', '利息|结息|存款利息', 'description', 'in', 'interest_income', 101, TRUE,
        (SELECT id FROM t_subject WHERE code = '1002'),
        (SELECT id FROM t_subject WHERE code = '6602.02'),
        NOW(), NOW(), 1, 1, 0);

-- 3. 税务缴费兜底 (priority 102, direction out)
INSERT INTO t_classification_rule (id, tenant_id, name, rule_type, pattern, match_field, direction, classification, priority, is_active,
                                   debit_subject_id, credit_subject_id, created_at, updated_at, created_by, updated_by, deleted)
VALUES (11, 1, '[兜底] 税务缴费', 'keyword_regex', '税|税务|缴税|税金|增值税|所得税|城建税|教育费附加|国库|印花', 'description', 'out', 'tax_payment', 102, TRUE,
        (SELECT id FROM t_subject WHERE code = '2221.01'),
        (SELECT id FROM t_subject WHERE code = '1002'),
        NOW(), NOW(), 1, 1, 0);

-- 4. 社保缴费兜底 (priority 103, direction out)
INSERT INTO t_classification_rule (id, tenant_id, name, rule_type, pattern, match_field, direction, classification, priority, is_active,
                                   debit_subject_id, credit_subject_id, created_at, updated_at, created_by, updated_by, deleted)
VALUES (12, 1, '[兜底] 社保缴费', 'keyword_regex', '社保|公积金|养老|医疗|失业|工伤|生育', 'description', 'out', 'social_security', 103, TRUE,
        (SELECT id FROM t_subject WHERE code = '2211.04'),
        (SELECT id FROM t_subject WHERE code = '1002'),
        NOW(), NOW(), 1, 1, 0);

-- 5. 保险费用兜底 (priority 104, direction out)
INSERT INTO t_classification_rule (id, tenant_id, name, rule_type, pattern, match_field, direction, classification, priority, is_active,
                                   debit_subject_id, credit_subject_id, created_at, updated_at, created_by, updated_by, deleted)
VALUES (13, 1, '[兜底] 保险费用', 'keyword_regex', '保险|保费|投保|财产险|责任险|雇主责任险|意外险', 'description', 'out', 'insurance_fee', 104, TRUE,
        (SELECT id FROM t_subject WHERE code = '6602.06'),
        (SELECT id FROM t_subject WHERE code = '1002'),
        NOW(), NOW(), 1, 1, 0);

-- 6. 工资薪酬兜底 (priority 105, direction out) — 种子规则未覆盖
INSERT INTO t_classification_rule (id, tenant_id, name, rule_type, pattern, match_field, direction, classification, priority, is_active,
                                   debit_subject_id, credit_subject_id, created_at, updated_at, created_by, updated_by, deleted)
VALUES (14, 1, '[兜底] 工资薪酬', 'keyword_regex', '工资|薪资|薪酬|劳务费|奖金|津贴', 'description', 'out', 'salary_payment', 105, TRUE,
        (SELECT id FROM t_subject WHERE code = '2211'),
        (SELECT id FROM t_subject WHERE code = '1002'),
        NOW(), NOW(), 1, 1, 0);

-- 7. 业务收款兜底 (priority 106, direction in) — 补充种子规则中未覆盖的关键词
INSERT INTO t_classification_rule (id, tenant_id, name, rule_type, pattern, match_field, direction, classification, priority, is_active,
                                   debit_subject_id, credit_subject_id, created_at, updated_at, created_by, updated_by, deleted)
VALUES (15, 1, '[兜底] 业务收款', 'keyword_regex', '收款|销售|回款|客户|应收|收入', 'description', 'in', 'business_receipt', 106, TRUE,
        NULL, NULL, NOW(), NOW(), 1, 1, 0);

-- 8. 业务付款兜底 (priority 107, direction out) — 补充种子规则中未覆盖的关键词
INSERT INTO t_classification_rule (id, tenant_id, name, rule_type, pattern, match_field, direction, classification, priority, is_active,
                                   debit_subject_id, credit_subject_id, created_at, updated_at, created_by, updated_by, deleted)
VALUES (16, 1, '[兜底] 业务付款', 'keyword_regex', '付款|采购|支付|供应商|应付|支出', 'description', 'out', 'business_payment', 107, TRUE,
        NULL, NULL, NOW(), NOW(), 1, 1, 0);

-- 9. 内部转账兜底 (priority 108, direction 不限) — 补充"内部"关键词
INSERT INTO t_classification_rule (id, tenant_id, name, rule_type, pattern, match_field, direction, classification, priority, is_active,
                                   debit_subject_id, credit_subject_id, created_at, updated_at, created_by, updated_by, deleted)
VALUES (17, 1, '[兜底] 内部转账', 'keyword_regex', '转账|转存|调拨|上划|下拨|内部', 'description', NULL, 'internal_transfer', 108, TRUE,
        NULL, NULL, NOW(), NOW(), 1, 1, 0);

COMMIT;
