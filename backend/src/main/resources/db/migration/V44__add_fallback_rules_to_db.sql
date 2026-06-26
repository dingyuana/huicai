-- V44: 兜底关键词从硬编码改为 DB 驱动
-- 1. 添加 is_system 标记列
-- 2. 将 FallbackHeuristicService 中 9 条兜底规则写入 t_classification_rule
-- 3. 标记 is_system=true 方便前端展示为只读

ALTER TABLE t_classification_rule
    ADD COLUMN IF NOT EXISTS is_system BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN t_classification_rule.is_system IS '系统内置规则(兜底), 前端只读不可删除/编辑';

-- 兜底规则: priority 90-99, is_system=true, tenant_id=1
-- 方向列: in/out/空字符串(不限方向)
-- 用 CTE 计算当前最大 id，保证幂等
WITH max_id AS (SELECT COALESCE(MAX(id), 0) AS m FROM t_classification_rule)
INSERT INTO t_classification_rule (id, tenant_id, name, rule_type, pattern, match_field, direction, classification, priority, is_active, is_system, route_type, created_by, updated_by)
OVERRIDING SYSTEM VALUE
SELECT m + 1,  1, '系统兜底-银行手续费',      'keyword_regex', '手续费|工本费|年费|账户管理费',             'description', 'out', 'bank_fee',          90, TRUE, TRUE, 'A', 1, 1 FROM max_id
WHERE NOT EXISTS (SELECT 1 FROM t_classification_rule WHERE tenant_id=1 AND name='系统兜底-银行手续费');

WITH max_id AS (SELECT COALESCE(MAX(id), 0) AS m FROM t_classification_rule)
INSERT INTO t_classification_rule (id, tenant_id, name, rule_type, pattern, match_field, direction, classification, priority, is_active, is_system, route_type, created_by, updated_by)
OVERRIDING SYSTEM VALUE
SELECT m + 1,  1, '系统兜底-利息收入',        'keyword_regex', '利息|结息|存款利息',                       'description', 'in',  'interest_income',   91, TRUE, TRUE, 'A', 1, 1 FROM max_id
WHERE NOT EXISTS (SELECT 1 FROM t_classification_rule WHERE tenant_id=1 AND name='系统兜底-利息收入');

WITH max_id AS (SELECT COALESCE(MAX(id), 0) AS m FROM t_classification_rule)
INSERT INTO t_classification_rule (id, tenant_id, name, rule_type, pattern, match_field, direction, classification, priority, is_active, is_system, route_type, created_by, updated_by)
OVERRIDING SYSTEM VALUE
SELECT m + 1,  1, '系统兜底-税务缴费',        'keyword_regex', '税|税务|缴税|税金|增值税|所得税|城建税|教育费附加|国库|金库|印花|国家金库', 'description', 'out', 'tax_payment',       92, TRUE, TRUE, 'A', 1, 1 FROM max_id
WHERE NOT EXISTS (SELECT 1 FROM t_classification_rule WHERE tenant_id=1 AND name='系统兜底-税务缴费');

WITH max_id AS (SELECT COALESCE(MAX(id), 0) AS m FROM t_classification_rule)
INSERT INTO t_classification_rule (id, tenant_id, name, rule_type, pattern, match_field, direction, classification, priority, is_active, is_system, route_type, created_by, updated_by)
OVERRIDING SYSTEM VALUE
SELECT m + 1,  1, '系统兜底-社保缴费',        'keyword_regex', '社保|公积金|养老|医疗|失业|工伤|生育',     'description', 'out', 'social_security',   93, TRUE, TRUE, 'A', 1, 1 FROM max_id
WHERE NOT EXISTS (SELECT 1 FROM t_classification_rule WHERE tenant_id=1 AND name='系统兜底-社保缴费');

WITH max_id AS (SELECT COALESCE(MAX(id), 0) AS m FROM t_classification_rule)
INSERT INTO t_classification_rule (id, tenant_id, name, rule_type, pattern, match_field, direction, classification, priority, is_active, is_system, route_type, created_by, updated_by)
OVERRIDING SYSTEM VALUE
SELECT m + 1,  1, '系统兜底-保险费用',        'keyword_regex', '保险|保费|投保',                           'description', 'out', 'insurance_fee',     94, TRUE, TRUE, 'A', 1, 1 FROM max_id
WHERE NOT EXISTS (SELECT 1 FROM t_classification_rule WHERE tenant_id=1 AND name='系统兜底-保险费用');

WITH max_id AS (SELECT COALESCE(MAX(id), 0) AS m FROM t_classification_rule)
INSERT INTO t_classification_rule (id, tenant_id, name, rule_type, pattern, match_field, direction, classification, priority, is_active, is_system, route_type, created_by, updated_by)
OVERRIDING SYSTEM VALUE
SELECT m + 1,  1, '系统兜底-工资发放',        'keyword_regex', '工资|薪资|薪酬|劳务费|奖金|津贴',         'description', 'out', 'salary_payment',    95, TRUE, TRUE, 'A', 1, 1 FROM max_id
WHERE NOT EXISTS (SELECT 1 FROM t_classification_rule WHERE tenant_id=1 AND name='系统兜底-工资发放');

WITH max_id AS (SELECT COALESCE(MAX(id), 0) AS m FROM t_classification_rule)
INSERT INTO t_classification_rule (id, tenant_id, name, rule_type, pattern, match_field, direction, classification, priority, is_active, is_system, route_type, created_by, updated_by)
OVERRIDING SYSTEM VALUE
SELECT m + 1,  1, '系统兜底-业务收款',        'keyword_regex', '货款|收款|销售|回款|客户|应收|收入',       'description', 'in',  'business_receipt',  96, TRUE, TRUE, 'B', 1, 1 FROM max_id
WHERE NOT EXISTS (SELECT 1 FROM t_classification_rule WHERE tenant_id=1 AND name='系统兜底-业务收款');

WITH max_id AS (SELECT COALESCE(MAX(id), 0) AS m FROM t_classification_rule)
INSERT INTO t_classification_rule (id, tenant_id, name, rule_type, pattern, match_field, direction, classification, priority, is_active, is_system, route_type, created_by, updated_by)
OVERRIDING SYSTEM VALUE
SELECT m + 1,  1, '系统兜底-业务付款',        'keyword_regex', '货款|付款|采购|支付|供应商|应付|支出',     'description', 'out', 'business_payment',  97, TRUE, TRUE, 'B', 1, 1 FROM max_id
WHERE NOT EXISTS (SELECT 1 FROM t_classification_rule WHERE tenant_id=1 AND name='系统兜底-业务付款');

WITH max_id AS (SELECT COALESCE(MAX(id), 0) AS m FROM t_classification_rule)
INSERT INTO t_classification_rule (id, tenant_id, name, rule_type, pattern, match_field, direction, classification, priority, is_active, is_system, route_type, created_by, updated_by)
OVERRIDING SYSTEM VALUE
SELECT m + 1,  1, '系统兜底-内部转账',        'keyword_regex', '转账|转存|调拨|上划|下拨|内部',           'description', NULL, 'internal_transfer', 98, TRUE, TRUE, 'B', 1, 1 FROM max_id
WHERE NOT EXISTS (SELECT 1 FROM t_classification_rule WHERE tenant_id=1 AND name='系统兜底-内部转账');
