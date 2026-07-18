-- ============================================================
-- V75: 银行流水分类重构 — 8类体系迁移
-- 旧分类(10类): bank_fee/interest_income/tax_payment/social_security/
--               insurance_fee/salary_payment/business_receipt/
--               business_payment/internal_transfer/pending
-- 新分类(8类): bank_interest_fee/tax_withholding/salary_social/
--              business_receipt/business_payment/internal_transfer/
--              financing_invest/other_unknown
-- ============================================================

-- 1. 更新已有银行流水历史数据 (t_bank_statement.classification)
UPDATE t_bank_statement SET classification = 'bank_interest_fee'
    WHERE classification IN ('bank_fee', 'interest_income');

UPDATE t_bank_statement SET classification = 'tax_withholding'
    WHERE classification = 'tax_payment';

UPDATE t_bank_statement SET classification = 'salary_social'
    WHERE classification IN ('social_security', 'insurance_fee', 'salary_payment');

UPDATE t_bank_statement SET classification = 'other_unknown'
    WHERE classification = 'pending' OR classification IS NULL OR classification = '';

-- 2. 删除旧的种子规则和兜底规则 (V20 id 1-8, V26 id 9-17, V44 系统规则)
DELETE FROM t_classification_rule
    WHERE classification IN ('bank_fee', 'interest_income', 'tax_payment',
                             'social_security', 'insurance_fee', 'salary_payment', 'pending')
      AND deleted = 0;

-- 3. 重置序列
SELECT setval(pg_get_serial_sequence('t_classification_rule', 'id'),
              COALESCE((SELECT MAX(id) FROM t_classification_rule), 0), true);

-- 4. 插入新的8条种子规则 (priority 1-8, tenant_id=1)
-- 注意: 使用子查询查科目ID避免硬编码

-- 4.1 银行利息与手续费 (合并原 bank_fee + interest_income, 不限方向)
INSERT INTO t_classification_rule (tenant_id, name, rule_type, pattern, match_field, direction,
                                    classification, priority, is_active, is_system, route_type,
                                    debit_subject_id, credit_subject_id, created_at, updated_at,
                                    created_by, updated_by, deleted)
VALUES (1, '银行利息与手续费', 'keyword_regex',
        '手续费|工本费|年费|账户管理费|利息|结息|存款利息',
        'description', NULL, 'bank_interest_fee', 1, TRUE, FALSE, 'A',
        NULL, NULL, NOW(), NOW(), 1, 1, 0);

-- 4.2 业务收款
INSERT INTO t_classification_rule (tenant_id, name, rule_type, pattern, match_field, direction,
                                    classification, priority, is_active, is_system, route_type,
                                    debit_subject_id, credit_subject_id, created_at, updated_at,
                                    created_by, updated_by, deleted)
VALUES (1, '业务收款', 'keyword_regex',
        '货款|收款|销售|回款|客户|应收|收入',
        'description', 'in', 'business_receipt', 2, TRUE, FALSE, 'B',
        NULL, NULL, NOW(), NOW(), 1, 1, 0);

-- 4.3 业务付款
INSERT INTO t_classification_rule (tenant_id, name, rule_type, pattern, match_field, direction,
                                    classification, priority, is_active, is_system, route_type,
                                    debit_subject_id, credit_subject_id, created_at, updated_at,
                                    created_by, updated_by, deleted)
VALUES (1, '业务付款', 'keyword_regex',
        '货款|付款|采购|支付|供应商|应付|支出',
        'description', 'out', 'business_payment', 3, TRUE, FALSE, 'B',
        NULL, NULL, NOW(), NOW(), 1, 1, 0);

-- 4.4 内部转账
INSERT INTO t_classification_rule (tenant_id, name, rule_type, pattern, match_field, direction,
                                    classification, priority, is_active, is_system, route_type,
                                    debit_subject_id, credit_subject_id, created_at, updated_at,
                                    created_by, updated_by, deleted)
VALUES (1, '内部转账', 'keyword_regex',
        '转账|转存|调拨|上划|下拨',
        'description', NULL, 'internal_transfer', 4, TRUE, FALSE, 'B',
        NULL, NULL, NOW(), NOW(), 1, 1, 0);

-- 4.5 税费扣缴
INSERT INTO t_classification_rule (tenant_id, name, rule_type, pattern, match_field, direction,
                                    classification, priority, is_active, is_system, route_type,
                                    debit_subject_id, credit_subject_id, created_at, updated_at,
                                    created_by, updated_by, deleted)
VALUES (1, '税费扣缴', 'keyword_regex',
        '税|税务|缴税|税金|税款|增值税|所得税|城建税|教育费附加|国家金库|国库|印花',
        'description', 'out', 'tax_withholding', 5, TRUE, FALSE, 'A',
        (SELECT id FROM t_subject WHERE code = '2221.01' LIMIT 1),
        (SELECT id FROM t_subject WHERE code = '1002' LIMIT 1),
        NOW(), NOW(), 1, 1, 0);

-- 4.6 薪酬与社保
INSERT INTO t_classification_rule (tenant_id, name, rule_type, pattern, match_field, direction,
                                    classification, priority, is_active, is_system, route_type,
                                    debit_subject_id, credit_subject_id, created_at, updated_at,
                                    created_by, updated_by, deleted)
VALUES (1, '薪酬与社保', 'keyword_regex',
        '工资|薪酬|社保|公积金|养老|医疗|失业|工伤|生育|代扣|个税',
        'description', 'out', 'salary_social', 6, TRUE, FALSE, 'B',
        (SELECT id FROM t_subject WHERE code = '2211' LIMIT 1),
        (SELECT id FROM t_subject WHERE code = '1002' LIMIT 1),
        NOW(), NOW(), 1, 1, 0);

-- 4.7 筹资与投资活动 (新增分类, C类人工处理)
INSERT INTO t_classification_rule (tenant_id, name, rule_type, pattern, match_field, direction,
                                    classification, priority, is_active, is_system, route_type,
                                    debit_subject_id, credit_subject_id, created_at, updated_at,
                                    created_by, updated_by, deleted)
VALUES (1, '筹资与投资活动', 'keyword_regex',
        '借款|还款|贷款|理财|投资|融资|分红|股本|债券',
        'description', NULL, 'financing_invest', 7, TRUE, FALSE, 'C',
        NULL, NULL, NOW(), NOW(), 1, 1, 0);

-- 4.8 其它/待认领 (空规则兜底, 最低优先级)
INSERT INTO t_classification_rule (tenant_id, name, rule_type, pattern, match_field, direction,
                                    classification, priority, is_active, is_system, route_type,
                                    debit_subject_id, credit_subject_id, created_at, updated_at,
                                    created_by, updated_by, deleted)
VALUES (1, '其它/待认领', 'keyword_regex', '', 'description', NULL, 'other_unknown', 8, TRUE, FALSE, 'C',
        NULL, NULL, NOW(), NOW(), 1, 1, 0);

-- 5. 插入新的系统兜底规则 (priority 90-97, is_system=true, tenant_id=1)
-- 5.1 系统兜底-银行利息与手续费
INSERT INTO t_classification_rule (tenant_id, name, rule_type, pattern, match_field, direction,
                                    classification, priority, is_active, is_system, route_type,
                                    created_at, updated_at, created_by, updated_by, deleted)
VALUES (1, '系统兜底-银行利息与手续费', 'keyword_regex',
        '手续费|工本费|年费|账户管理费|利息|结息|存款利息',
        'description', NULL, 'bank_interest_fee', 90, TRUE, TRUE, 'A',
        NOW(), NOW(), 1, 1, 0);

-- 5.2 系统兜底-税费扣缴
INSERT INTO t_classification_rule (tenant_id, name, rule_type, pattern, match_field, direction,
                                    classification, priority, is_active, is_system, route_type,
                                    created_at, updated_at, created_by, updated_by, deleted)
VALUES (1, '系统兜底-税费扣缴', 'keyword_regex',
        '税|税务|缴税|税金|税款|增值税|所得税|城建税|教育费附加|国库|金库|印花|国家金库',
        'description', 'out', 'tax_withholding', 91, TRUE, TRUE, 'A',
        NOW(), NOW(), 1, 1, 0);

-- 5.3 系统兜底-薪酬与社保
INSERT INTO t_classification_rule (tenant_id, name, rule_type, pattern, match_field, direction,
                                    classification, priority, is_active, is_system, route_type,
                                    created_at, updated_at, created_by, updated_by, deleted)
VALUES (1, '系统兜底-薪酬与社保', 'keyword_regex',
        '工资|薪资|薪酬|劳务费|奖金|津贴|社保|公积金|养老|医疗|失业|工伤|生育',
        'description', 'out', 'salary_social', 92, TRUE, TRUE, 'B',
        NOW(), NOW(), 1, 1, 0);

-- 5.4 系统兜底-业务收款
INSERT INTO t_classification_rule (tenant_id, name, rule_type, pattern, match_field, direction,
                                    classification, priority, is_active, is_system, route_type,
                                    created_at, updated_at, created_by, updated_by, deleted)
VALUES (1, '系统兜底-业务收款', 'keyword_regex',
        '货款|收款|销售|回款|客户|应收|收入',
        'description', 'in', 'business_receipt', 93, TRUE, TRUE, 'B',
        NOW(), NOW(), 1, 1, 0);

-- 5.5 系统兜底-业务付款
INSERT INTO t_classification_rule (tenant_id, name, rule_type, pattern, match_field, direction,
                                    classification, priority, is_active, is_system, route_type,
                                    created_at, updated_at, created_by, updated_by, deleted)
VALUES (1, '系统兜底-业务付款', 'keyword_regex',
        '货款|付款|采购|支付|供应商|应付|支出',
        'description', 'out', 'business_payment', 94, TRUE, TRUE, 'B',
        NOW(), NOW(), 1, 1, 0);

-- 5.6 系统兜底-内部转账
INSERT INTO t_classification_rule (tenant_id, name, rule_type, pattern, match_field, direction,
                                    classification, priority, is_active, is_system, route_type,
                                    created_at, updated_at, created_by, updated_by, deleted)
VALUES (1, '系统兜底-内部转账', 'keyword_regex',
        '转账|转存|调拨|上划|下拨|内部',
        'description', NULL, 'internal_transfer', 95, TRUE, TRUE, 'B',
        NOW(), NOW(), 1, 1, 0);

-- 5.7 系统兜底-筹资与投资
INSERT INTO t_classification_rule (tenant_id, name, rule_type, pattern, match_field, direction,
                                    classification, priority, is_active, is_system, route_type,
                                    created_at, updated_at, created_by, updated_by, deleted)
VALUES (1, '系统兜底-筹资与投资', 'keyword_regex',
        '借款|还款|贷款|理财|投资|融资|分红|股本|债券',
        'description', NULL, 'financing_invest', 96, TRUE, TRUE, 'C',
        NOW(), NOW(), 1, 1, 0);

-- 6. 更新comment
COMMENT ON COLUMN t_bank_statement.classification IS
'业务分类: bank_interest_fee-银行利息与手续费, tax_withholding-税费扣缴, salary_social-薪酬与社保, business_receipt-业务收款, business_payment-业务付款, internal_transfer-内部转账, financing_invest-筹资与投资, other_unknown-其它/待认领';
