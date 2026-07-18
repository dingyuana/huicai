-- ============================================================
-- V60: 补充基础科目 + 常用凭证模板
-- ============================================================
-- 背景:
--   当前系统仅 4 个科目(1122/2221/2221.01/5001)，V40/V50 预置的
--   19 个凭证模板因科目缺失，分录行全部为空。
--   本迁移补充常用的一级/二级科目，并新增一批常用手工制证模板。
-- ============================================================

BEGIN;

-- ============================================================
-- 段 1: 补充基础科目（仅插入尚不存在的科目）
-- ============================================================

-- 1a. 资产类
INSERT INTO t_subject (id, code, name, parent_id, level, direction, is_leaf, is_active) OVERRIDING SYSTEM VALUE
VALUES
  (200, '1001', '库存现金',     NULL, 1, 'debit',  TRUE, TRUE),
  (201, '1002', '银行存款',     NULL, 1, 'debit',  TRUE, TRUE),
  (202, '1123', '预付账款',     NULL, 1, 'debit',  TRUE, TRUE),
  (203, '1221', '其他应收款',   NULL, 1, 'debit',  TRUE, TRUE),
  (204, '1403', '原材料',       NULL, 1, 'debit',  TRUE, TRUE),
  (205, '1405', '库存商品',     NULL, 1, 'debit',  TRUE, TRUE),
  (206, '1601', '固定资产',     NULL, 1, 'debit',  TRUE, TRUE),
  (207, '1602', '累计折旧',     NULL, 1, 'credit', TRUE, TRUE)
ON CONFLICT (code) DO NOTHING;

-- 1b. 负债类
INSERT INTO t_subject (id, code, name, parent_id, level, direction, is_leaf, is_active) OVERRIDING SYSTEM VALUE
VALUES
  (210, '2202', '应付账款',     NULL, 1, 'credit', TRUE, TRUE),
  (211, '2203', '预收账款',     NULL, 1, 'credit', TRUE, TRUE),
  (212, '2211', '应付职工薪酬', NULL, 1, 'credit', TRUE, TRUE),
  (213, '2241', '其他应付款',   NULL, 1, 'credit', TRUE, TRUE)
ON CONFLICT (code) DO NOTHING;

-- 1c. 权益类
INSERT INTO t_subject (id, code, name, parent_id, level, direction, is_leaf, is_active) OVERRIDING SYSTEM VALUE
VALUES
  (215, '4001', '实收资本', NULL, 1, 'credit', TRUE, TRUE),
  (216, '4103', '本年利润', NULL, 1, 'credit', TRUE, TRUE),
  (217, '4104', '利润分配', NULL, 1, 'credit', TRUE, TRUE)
ON CONFLICT (code) DO NOTHING;

-- 1d. 损益类
INSERT INTO t_subject (id, code, name, parent_id, level, direction, is_leaf, is_active) OVERRIDING SYSTEM VALUE
VALUES
  (220, '6001', '主营业务收入', NULL, 1, 'credit', TRUE, TRUE),
  (221, '6401', '主营业务成本', NULL, 1, 'debit',  TRUE, TRUE),
  (222, '6601', '销售费用',     NULL, 1, 'debit',  TRUE, TRUE),
  (223, '6602', '管理费用',     NULL, 1, 'debit',  TRUE, TRUE),
  (224, '6603', '财务费用',     NULL, 1, 'debit',  TRUE, TRUE),
  (225, '6801', '所得税费用',   NULL, 1, 'debit',  TRUE, TRUE)
ON CONFLICT (code) DO NOTHING;

-- ============================================================
-- 段 2: 补充常用二级科目
-- ============================================================

-- 2a. 应交税费-增值税-进项税额 (parent=2221)
INSERT INTO t_subject (id, code, name, parent_id, level, direction, is_leaf, is_active) OVERRIDING SYSTEM VALUE
SELECT 230, '2221.02', '应交税费-应交增值税-进项税额', s.id, 2, 'debit', TRUE, TRUE
FROM t_subject s WHERE s.code = '2221'
  AND NOT EXISTS (SELECT 1 FROM t_subject WHERE code = '2221.02')
LIMIT 1;

-- 2b. 管理费用二级科目 (parent=6602)
INSERT INTO t_subject (id, code, name, parent_id, level, direction, is_leaf, is_active) OVERRIDING SYSTEM VALUE
SELECT 231, '6602.01', '管理费用-办公费',     s.id, 2, 'debit', TRUE, TRUE
FROM t_subject s WHERE s.code = '6602'
  AND NOT EXISTS (SELECT 1 FROM t_subject WHERE code = '6602.01')
LIMIT 1;

INSERT INTO t_subject (id, code, name, parent_id, level, direction, is_leaf, is_active) OVERRIDING SYSTEM VALUE
SELECT 232, '6602.02', '管理费用-差旅费',     s.id, 2, 'debit', TRUE, TRUE
FROM t_subject s WHERE s.code = '6602'
  AND NOT EXISTS (SELECT 1 FROM t_subject WHERE code = '6602.02')
LIMIT 1;

INSERT INTO t_subject (id, code, name, parent_id, level, direction, is_leaf, is_active) OVERRIDING SYSTEM VALUE
SELECT 233, '6602.03', '管理费用-业务招待费', s.id, 2, 'debit', TRUE, TRUE
FROM t_subject s WHERE s.code = '6602'
  AND NOT EXISTS (SELECT 1 FROM t_subject WHERE code = '6602.03')
LIMIT 1;

INSERT INTO t_subject (id, code, name, parent_id, level, direction, is_leaf, is_active) OVERRIDING SYSTEM VALUE
SELECT 234, '6602.04', '管理费用-折旧费',     s.id, 2, 'debit', TRUE, TRUE
FROM t_subject s WHERE s.code = '6602'
  AND NOT EXISTS (SELECT 1 FROM t_subject WHERE code = '6602.04')
LIMIT 1;

INSERT INTO t_subject (id, code, name, parent_id, level, direction, is_leaf, is_active) OVERRIDING SYSTEM VALUE
SELECT 235, '6602.05', '管理费用-工资',       s.id, 2, 'debit', TRUE, TRUE
FROM t_subject s WHERE s.code = '6602'
  AND NOT EXISTS (SELECT 1 FROM t_subject WHERE code = '6602.05')
LIMIT 1;

-- 2c. 财务费用二级科目 (parent=6603)
INSERT INTO t_subject (id, code, name, parent_id, level, direction, is_leaf, is_active) OVERRIDING SYSTEM VALUE
SELECT 236, '6603.01', '财务费用-手续费',     s.id, 2, 'debit', TRUE, TRUE
FROM t_subject s WHERE s.code = '6603'
  AND NOT EXISTS (SELECT 1 FROM t_subject WHERE code = '6603.01')
LIMIT 1;

INSERT INTO t_subject (id, code, name, parent_id, level, direction, is_leaf, is_active) OVERRIDING SYSTEM VALUE
SELECT 237, '6603.02', '财务费用-利息收入',   s.id, 2, 'debit', TRUE, TRUE
FROM t_subject s WHERE s.code = '6603'
  AND NOT EXISTS (SELECT 1 FROM t_subject WHERE code = '6603.02')
LIMIT 1;

-- 2d. 应付职工薪酬二级科目 (parent=2211)
INSERT INTO t_subject (id, code, name, parent_id, level, direction, is_leaf, is_active) OVERRIDING SYSTEM VALUE
SELECT 238, '2211.01', '应付职工薪酬-工资',   s.id, 2, 'credit', TRUE, TRUE
FROM t_subject s WHERE s.code = '2211'
  AND NOT EXISTS (SELECT 1 FROM t_subject WHERE code = '2211.01')
LIMIT 1;

INSERT INTO t_subject (id, code, name, parent_id, level, direction, is_leaf, is_active) OVERRIDING SYSTEM VALUE
SELECT 239, '2211.02', '应付职工薪酬-社保',   s.id, 2, 'credit', TRUE, TRUE
FROM t_subject s WHERE s.code = '2211'
  AND NOT EXISTS (SELECT 1 FROM t_subject WHERE code = '2211.02')
LIMIT 1;

-- 更新父科目为非末级
UPDATE t_subject SET is_leaf = FALSE
WHERE code IN ('6602', '6603', '2211') AND is_leaf = TRUE
  AND EXISTS (SELECT 1 FROM t_subject child WHERE child.parent_id = t_subject.id);

-- ============================================================
-- 段 3: 修复现有模板的分录行（V40/V50 因科目缺失导致行记录为空）
--        此处重新插入缺失的行。每行用 WHERE NOT EXISTS 保证幂等。
-- ============================================================

-- 3a. 销售发票制证 (INVOICE source)
-- 借:应收账款 1122 / 贷:主营业务收入 5001 / 贷:应交增值税-销项税额 2221.01
INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, line_order)
SELECT t.id, s.id, '{{totalAmount}}', '', '{客户名称}销售款', 'debit', 1
FROM t_voucher_template t, t_subject s
WHERE t.source = 'INVOICE' AND t.business_type = 'INVOICE_OUT' AND s.code = '1122'
  AND NOT EXISTS (SELECT 1 FROM t_voucher_template_line l WHERE l.template_id = t.id AND l.line_order = 1);

INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, line_order)
SELECT t.id, s.id, '', '{{amount}}', '{客户名称}销售收入', 'credit', 2
FROM t_voucher_template t, t_subject s
WHERE t.source = 'INVOICE' AND t.business_type = 'INVOICE_OUT' AND s.code = '5001'
  AND NOT EXISTS (SELECT 1 FROM t_voucher_template_line l WHERE l.template_id = t.id AND l.line_order = 2);

INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, line_order)
SELECT t.id, s.id, '', '{{taxAmount}}', '{客户名称}销项税', 'credit', 3
FROM t_voucher_template t, t_subject s
WHERE t.source = 'INVOICE' AND t.business_type = 'INVOICE_OUT' AND s.code = '2221.01'
  AND NOT EXISTS (SELECT 1 FROM t_voucher_template_line l WHERE l.template_id = t.id AND l.line_order = 3);

-- 3b. 收款制证 (BUSINESS_DOC RECEIPT)
-- 借:银行存款 1002 / 贷:应收账款 1122
INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, line_order)
SELECT t.id, s.id, '{{amount}}', '', '收款: {{summary}}', 'debit', 1
FROM t_voucher_template t, t_subject s
WHERE t.source = 'BUSINESS_DOC' AND t.business_type = 'RECEIPT' AND s.code = '1002'
  AND NOT EXISTS (SELECT 1 FROM t_voucher_template_line l WHERE l.template_id = t.id AND l.line_order = 1);

INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, line_order)
SELECT t.id, s.id, '', '{{amount}}', '收款: {{summary}}', 'credit', 2
FROM t_voucher_template t, t_subject s
WHERE t.source = 'BUSINESS_DOC' AND t.business_type = 'RECEIPT' AND s.code = '1122'
  AND NOT EXISTS (SELECT 1 FROM t_voucher_template_line l WHERE l.template_id = t.id AND l.line_order = 2);

-- 3c. 付款制证 (BUSINESS_DOC PAYMENT)
-- 借:应付账款 2202 / 贷:银行存款 1002
INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, line_order)
SELECT t.id, s.id, '{{amount}}', '', '付款: {{summary}}', 'debit', 1
FROM t_voucher_template t, t_subject s
WHERE t.source = 'BUSINESS_DOC' AND t.business_type = 'PAYMENT' AND s.code = '2202'
  AND NOT EXISTS (SELECT 1 FROM t_voucher_template_line l WHERE l.template_id = t.id AND l.line_order = 1);

INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, line_order)
SELECT t.id, s.id, '', '{{amount}}', '付款: {{summary}}', 'credit', 2
FROM t_voucher_template t, t_subject s
WHERE t.source = 'BUSINESS_DOC' AND t.business_type = 'PAYMENT' AND s.code = '1002'
  AND NOT EXISTS (SELECT 1 FROM t_voucher_template_line l WHERE l.template_id = t.id AND l.line_order = 2);

-- 3d. 报销制证 (BUSINESS_DOC EXPENSE)
-- 借:管理费用 6602 / 贷:银行存款 1002
INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, line_order)
SELECT t.id, s.id, '{{amount}}', '', '报销: {{summary}}', 'debit', 1
FROM t_voucher_template t, t_subject s
WHERE t.source = 'BUSINESS_DOC' AND t.business_type = 'EXPENSE' AND s.code = '6602'
  AND NOT EXISTS (SELECT 1 FROM t_voucher_template_line l WHERE l.template_id = t.id AND l.line_order = 1);

INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, line_order)
SELECT t.id, s.id, '', '{{amount}}', '报销: {{summary}}', 'credit', 2
FROM t_voucher_template t, t_subject s
WHERE t.source = 'BUSINESS_DOC' AND t.business_type = 'EXPENSE' AND s.code = '1002'
  AND NOT EXISTS (SELECT 1 FROM t_voucher_template_line l WHERE l.template_id = t.id AND l.line_order = 2);

-- 3e. 采购发票制证 (BUSINESS_DOC INVOICE_IN)
-- 借:在途物资 1403 / 贷:应付账款 2202
INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, line_order)
SELECT t.id, s.id, '{{amount}}', '', '采购: {{summary}}', 'debit', 1
FROM t_voucher_template t, t_subject s
WHERE t.source = 'BUSINESS_DOC' AND t.business_type = 'INVOICE_IN' AND s.code = '1403'
  AND NOT EXISTS (SELECT 1 FROM t_voucher_template_line l WHERE l.template_id = t.id AND l.line_order = 1);

INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, line_order)
SELECT t.id, s.id, '', '{{amount}}', '采购: {{summary}}', 'credit', 2
FROM t_voucher_template t, t_subject s
WHERE t.source = 'BUSINESS_DOC' AND t.business_type = 'INVOICE_IN' AND s.code = '2202'
  AND NOT EXISTS (SELECT 1 FROM t_voucher_template_line l WHERE l.template_id = t.id AND l.line_order = 2);

-- 3f. 销售单据制证 (BUSINESS_DOC INVOICE_OUT)
-- 借:应收账款 1122 / 贷:主营业务收入 6001
INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, line_order)
SELECT t.id, s.id, '{{amount}}', '', '销售: {{summary}}', 'debit', 1
FROM t_voucher_template t, t_subject s
WHERE t.source = 'BUSINESS_DOC' AND t.business_type = 'INVOICE_OUT' AND s.code = '1122'
  AND NOT EXISTS (SELECT 1 FROM t_voucher_template_line l WHERE l.template_id = t.id AND l.line_order = 1);

INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, line_order)
SELECT t.id, s.id, '', '{{amount}}', '销售: {{summary}}', 'credit', 2
FROM t_voucher_template t, t_subject s
WHERE t.source = 'BUSINESS_DOC' AND t.business_type = 'INVOICE_OUT' AND s.code = '6001'
  AND NOT EXISTS (SELECT 1 FROM t_voucher_template_line l WHERE l.template_id = t.id AND l.line_order = 2);

-- 3g. 其他应收制证 (BUSINESS_DOC OTHER_RECEIVABLE)
-- 借:其他应收款 1221 / 贷:银行存款 1002
INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, line_order)
SELECT t.id, s.id, '{{amount}}', '', '其他应收: {{summary}}', 'debit', 1
FROM t_voucher_template t, t_subject s
WHERE t.source = 'BUSINESS_DOC' AND t.business_type = 'OTHER_RECEIVABLE' AND s.code = '1221'
  AND NOT EXISTS (SELECT 1 FROM t_voucher_template_line l WHERE l.template_id = t.id AND l.line_order = 1);

INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, line_order)
SELECT t.id, s.id, '', '{{amount}}', '其他应收: {{summary}}', 'credit', 2
FROM t_voucher_template t, t_subject s
WHERE t.source = 'BUSINESS_DOC' AND t.business_type = 'OTHER_RECEIVABLE' AND s.code = '1002'
  AND NOT EXISTS (SELECT 1 FROM t_voucher_template_line l WHERE l.template_id = t.id AND l.line_order = 2);

-- 3h. 其他应付制证 (BUSINESS_DOC OTHER_PAYABLE)
-- 借:银行存款 1002 / 贷:其他应付款 2241
INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, line_order)
SELECT t.id, s.id, '{{amount}}', '', '其他应付: {{summary}}', 'debit', 1
FROM t_voucher_template t, t_subject s
WHERE t.source = 'BUSINESS_DOC' AND t.business_type = 'OTHER_PAYABLE' AND s.code = '1002'
  AND NOT EXISTS (SELECT 1 FROM t_voucher_template_line l WHERE l.template_id = t.id AND l.line_order = 1);

INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, line_order)
SELECT t.id, s.id, '', '{{amount}}', '其他应付: {{summary}}', 'credit', 2
FROM t_voucher_template t, t_subject s
WHERE t.source = 'BUSINESS_DOC' AND t.business_type = 'OTHER_PAYABLE' AND s.code = '2241'
  AND NOT EXISTS (SELECT 1 FROM t_voucher_template_line l WHERE l.template_id = t.id AND l.line_order = 2);

-- ============================================================
-- 段 4: 修复 V40 核销模板的分录行
-- ============================================================

-- 4a. 收款核销: 借 1002 / 贷 1122
INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, line_order)
SELECT t.id, s.id, '{{amount}}', '', '收款核销: {{summary}}', 'debit', 1
FROM t_voucher_template t, t_subject s
WHERE t.name = '收款核销' AND s.code = '1002'
  AND NOT EXISTS (SELECT 1 FROM t_voucher_template_line WHERE template_id = t.id AND line_order = 1);

INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, line_order)
SELECT t.id, s.id, '', '{{amount}}', '收款核销: {{summary}}', 'credit', 2
FROM t_voucher_template t, t_subject s
WHERE t.name = '收款核销' AND s.code = '1122'
  AND NOT EXISTS (SELECT 1 FROM t_voucher_template_line WHERE template_id = t.id AND line_order = 2);

-- 4b. 付款核销: 借 2202 / 贷 1002
INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, line_order)
SELECT t.id, s.id, '{{amount}}', '', '付款核销: {{summary}}', 'debit', 1
FROM t_voucher_template t, t_subject s
WHERE t.name = '付款核销' AND s.code = '2202'
  AND NOT EXISTS (SELECT 1 FROM t_voucher_template_line WHERE template_id = t.id AND line_order = 1);

INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, line_order)
SELECT t.id, s.id, '', '{{amount}}', '付款核销: {{summary}}', 'credit', 2
FROM t_voucher_template t, t_subject s
WHERE t.name = '付款核销' AND s.code = '1002'
  AND NOT EXISTS (SELECT 1 FROM t_voucher_template_line WHERE template_id = t.id AND line_order = 2);

-- 4c. 预收冲应收: 借 2203 / 贷 1122
INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, line_order)
SELECT t.id, s.id, '{{amount}}', '', '预收冲应收: {{summary}}', 'debit', 1
FROM t_voucher_template t, t_subject s
WHERE t.name = '预收冲应收' AND s.code = '2203'
  AND NOT EXISTS (SELECT 1 FROM t_voucher_template_line WHERE template_id = t.id AND line_order = 1);

INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, line_order)
SELECT t.id, s.id, '', '{{amount}}', '预收冲应收: {{summary}}', 'credit', 2
FROM t_voucher_template t, t_subject s
WHERE t.name = '预收冲应收' AND s.code = '1122'
  AND NOT EXISTS (SELECT 1 FROM t_voucher_template_line WHERE template_id = t.id AND line_order = 2);

-- 4d. 核销差额调整: 借 6603.01(财务费用-手续费) / 贷 1122(应收账款)
INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, line_order)
SELECT t.id, s.id, '{{amount}}', '', '核销差额: {{summary}}', 'debit', 1
FROM t_voucher_template t, t_subject s
WHERE t.name = '核销差额调整' AND s.code = '6603.01'
  AND NOT EXISTS (SELECT 1 FROM t_voucher_template_line WHERE template_id = t.id AND line_order = 1);

INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, line_order)
SELECT t.id, s.id, '', '{{amount}}', '核销差额: {{summary}}', 'credit', 2
FROM t_voucher_template t, t_subject s
WHERE t.name = '核销差额调整' AND s.code = '1122'
  AND NOT EXISTS (SELECT 1 FROM t_voucher_template_line WHERE template_id = t.id AND line_order = 2);

-- ============================================================
-- 段 5: 补充银行流水模板缺失的分录行
-- ============================================================

-- 5a. 银行手续费: 借 6603.01(财务费用-手续费) / 贷 1002(银行存款)
INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, line_order)
SELECT t.id, s.id, '{{amount}}', '', '{{summary}}', 'debit', 1
FROM t_voucher_template t, t_subject s
WHERE t.name = '银行手续费' AND s.code = '6603.01'
  AND NOT EXISTS (SELECT 1 FROM t_voucher_template_line WHERE template_id = t.id AND line_order = 1);

INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, line_order)
SELECT t.id, s.id, '', '{{amount}}', '{{summary}}', 'credit', 2
FROM t_voucher_template t, t_subject s
WHERE t.name = '银行手续费' AND s.code = '1002'
  AND NOT EXISTS (SELECT 1 FROM t_voucher_template_line WHERE template_id = t.id AND line_order = 2);

-- 5b. 存款利息收入: 借 1002(银行存款) / 贷 6603.02(财务费用-利息收入)
INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, line_order)
SELECT t.id, s.id, '{{amount}}', '', '{{summary}}', 'debit', 1
FROM t_voucher_template t, t_subject s
WHERE t.name = '存款利息收入' AND s.code = '1002'
  AND NOT EXISTS (SELECT 1 FROM t_voucher_template_line WHERE template_id = t.id AND line_order = 1);

INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, line_order)
SELECT t.id, s.id, '', '{{amount}}', '{{summary}}', 'credit', 2
FROM t_voucher_template t, t_subject s
WHERE t.name = '存款利息收入' AND s.code = '6603.02'
  AND NOT EXISTS (SELECT 1 FROM t_voucher_template_line WHERE template_id = t.id AND line_order = 2);

-- 5c. 税务缴费: 借 2221(应交税费) / 贷 1002(银行存款)
INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, line_order)
SELECT t.id, s.id, '{{amount}}', '', '税务缴费: {{summary}}', 'debit', 1
FROM t_voucher_template t, t_subject s
WHERE t.name = '税务缴费' AND s.code = '2221'
  AND NOT EXISTS (SELECT 1 FROM t_voucher_template_line WHERE template_id = t.id AND line_order = 1);

INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, line_order)
SELECT t.id, s.id, '', '{{amount}}', '税务缴费: {{summary}}', 'credit', 2
FROM t_voucher_template t, t_subject s
WHERE t.name = '税务缴费' AND s.code = '1002'
  AND NOT EXISTS (SELECT 1 FROM t_voucher_template_line WHERE template_id = t.id AND line_order = 2);

-- 5d. 社保缴费: 借 2211.02(应付职工薪酬-社保) / 贷 1002(银行存款)
INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, line_order)
SELECT t.id, s.id, '{{amount}}', '', '社保缴费: {{summary}}', 'debit', 1
FROM t_voucher_template t, t_subject s
WHERE t.name = '社保缴费' AND s.code = '2211.02'
  AND NOT EXISTS (SELECT 1 FROM t_voucher_template_line WHERE template_id = t.id AND line_order = 1);

INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, line_order)
SELECT t.id, s.id, '', '{{amount}}', '社保缴费: {{summary}}', 'credit', 2
FROM t_voucher_template t, t_subject s
WHERE t.name = '社保缴费' AND s.code = '1002'
  AND NOT EXISTS (SELECT 1 FROM t_voucher_template_line WHERE template_id = t.id AND line_order = 2);

-- 5e. 保险费用: 借 6602(管理费用) / 贷 1002(银行存款)
INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, line_order)
SELECT t.id, s.id, '{{amount}}', '', '保险费用: {{summary}}', 'debit', 1
FROM t_voucher_template t, t_subject s
WHERE t.name = '保险费用' AND s.code = '6602'
  AND NOT EXISTS (SELECT 1 FROM t_voucher_template_line WHERE template_id = t.id AND line_order = 1);

INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, line_order)
SELECT t.id, s.id, '', '{{amount}}', '保险费用: {{summary}}', 'credit', 2
FROM t_voucher_template t, t_subject s
WHERE t.name = '保险费用' AND s.code = '1002'
  AND NOT EXISTS (SELECT 1 FROM t_voucher_template_line WHERE template_id = t.id AND line_order = 2);

-- 5f. 结算-应收: 借 1002(银行存款) / 贷 1122(应收账款)
INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, line_order)
SELECT t.id, s.id, '{{amount}}', '', '结算-应收: {{summary}}', 'debit', 1
FROM t_voucher_template t, t_subject s
WHERE t.name = '结算-应收' AND s.code = '1002'
  AND NOT EXISTS (SELECT 1 FROM t_voucher_template_line WHERE template_id = t.id AND line_order = 1);

INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, line_order)
SELECT t.id, s.id, '', '{{amount}}', '结算-应收: {{summary}}', 'credit', 2
FROM t_voucher_template t, t_subject s
WHERE t.name = '结算-应收' AND s.code = '1122'
  AND NOT EXISTS (SELECT 1 FROM t_voucher_template_line WHERE template_id = t.id AND line_order = 2);

-- 5g. 结算-应付: 借 2202(应付账款) / 贷 1002(银行存款)
INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, line_order)
SELECT t.id, s.id, '{{amount}}', '', '结算-应付: {{summary}}', 'debit', 1
FROM t_voucher_template t, t_subject s
WHERE t.name = '结算-应付' AND s.code = '2202'
  AND NOT EXISTS (SELECT 1 FROM t_voucher_template_line WHERE template_id = t.id AND line_order = 1);

INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, line_order)
SELECT t.id, s.id, '', '{{amount}}', '结算-应付: {{summary}}', 'credit', 2
FROM t_voucher_template t, t_subject s
WHERE t.name = '结算-应付' AND s.code = '1002'
  AND NOT EXISTS (SELECT 1 FROM t_voucher_template_line WHERE template_id = t.id AND line_order = 2);

-- ============================================================
-- 段 6: 新增常用手工凭证模板 (source=NULL, 仅手工选用)
-- ============================================================

-- 6a. 计提工资: 借 管理费用-工资 / 贷 应付职工薪酬-工资
INSERT INTO t_voucher_template (name, description, classification, number_prefix, is_active)
SELECT '计提工资', '计提本月工资: 借 管理费用-工资, 贷 应付职工薪酬-工资', 'salary_accrual', 'JZ', TRUE
WHERE NOT EXISTS (SELECT 1 FROM t_voucher_template WHERE name = '计提工资');

INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, line_order)
SELECT t.id, s.id, '{{amount}}', '', '计提{{month}}月工资', 'debit', 1
FROM t_voucher_template t, t_subject s
WHERE t.name = '计提工资' AND s.code = '6602.05'
  AND NOT EXISTS (SELECT 1 FROM t_voucher_template_line WHERE template_id = t.id AND line_order = 1);

INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, line_order)
SELECT t.id, s.id, '', '{{amount}}', '计提{{month}}月工资', 'credit', 2
FROM t_voucher_template t, t_subject s
WHERE t.name = '计提工资' AND s.code = '2211.01'
  AND NOT EXISTS (SELECT 1 FROM t_voucher_template_line WHERE template_id = t.id AND line_order = 2);

-- 6b. 发放工资: 借 应付职工薪酬-工资 / 贷 银行存款
INSERT INTO t_voucher_template (name, description, classification, number_prefix, is_active)
SELECT '发放工资', '发放本月工资: 借 应付职工薪酬-工资, 贷 银行存款', 'salary_payment', 'JZ', TRUE
WHERE NOT EXISTS (SELECT 1 FROM t_voucher_template WHERE name = '发放工资');

INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, line_order)
SELECT t.id, s.id, '{{amount}}', '', '发放{{month}}月工资', 'debit', 1
FROM t_voucher_template t, t_subject s
WHERE t.name = '发放工资' AND s.code = '2211.01'
  AND NOT EXISTS (SELECT 1 FROM t_voucher_template_line WHERE template_id = t.id AND line_order = 1);

INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, line_order)
SELECT t.id, s.id, '', '{{amount}}', '发放{{month}}月工资', 'credit', 2
FROM t_voucher_template t, t_subject s
WHERE t.name = '发放工资' AND s.code = '1002'
  AND NOT EXISTS (SELECT 1 FROM t_voucher_template_line WHERE template_id = t.id AND line_order = 2);

-- 6c. 计提折旧: 借 管理费用-折旧费 / 贷 累计折旧
INSERT INTO t_voucher_template (name, description, classification, number_prefix, is_active)
SELECT '计提折旧', '计提固定资产折旧: 借 管理费用-折旧费, 贷 累计折旧', 'depreciation', 'JZ', TRUE
WHERE NOT EXISTS (SELECT 1 FROM t_voucher_template WHERE name = '计提折旧');

INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, line_order)
SELECT t.id, s.id, '{{amount}}', '', '计提{{month}}月折旧', 'debit', 1
FROM t_voucher_template t, t_subject s
WHERE t.name = '计提折旧' AND s.code = '6602.04'
  AND NOT EXISTS (SELECT 1 FROM t_voucher_template_line WHERE template_id = t.id AND line_order = 1);

INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, line_order)
SELECT t.id, s.id, '', '{{amount}}', '计提{{month}}月折旧', 'credit', 2
FROM t_voucher_template t, t_subject s
WHERE t.name = '计提折旧' AND s.code = '1602'
  AND NOT EXISTS (SELECT 1 FROM t_voucher_template_line WHERE template_id = t.id AND line_order = 2);

-- 6d. 差旅费报销: 借 管理费用-差旅费 / 贷 银行存款
INSERT INTO t_voucher_template (name, description, classification, number_prefix, is_active)
SELECT '差旅费报销', '差旅费报销: 借 管理费用-差旅费, 贷 银行存款', 'travel_expense', 'JZ', TRUE
WHERE NOT EXISTS (SELECT 1 FROM t_voucher_template WHERE name = '差旅费报销');

INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, line_order)
SELECT t.id, s.id, '{{amount}}', '', '{{summary}}', 'debit', 1
FROM t_voucher_template t, t_subject s
WHERE t.name = '差旅费报销' AND s.code = '6602.02'
  AND NOT EXISTS (SELECT 1 FROM t_voucher_template_line WHERE template_id = t.id AND line_order = 1);

INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, line_order)
SELECT t.id, s.id, '', '{{amount}}', '{{summary}}', 'credit', 2
FROM t_voucher_template t, t_subject s
WHERE t.name = '差旅费报销' AND s.code = '1002'
  AND NOT EXISTS (SELECT 1 FROM t_voucher_template_line WHERE template_id = t.id AND line_order = 2);

-- 6e. 办公费报销: 借 管理费用-办公费 / 贷 银行存款
INSERT INTO t_voucher_template (name, description, classification, number_prefix, is_active)
SELECT '办公费报销', '办公费报销: 借 管理费用-办公费, 贷 银行存款', 'office_expense', 'JZ', TRUE
WHERE NOT EXISTS (SELECT 1 FROM t_voucher_template WHERE name = '办公费报销');

INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, line_order)
SELECT t.id, s.id, '{{amount}}', '', '{{summary}}', 'debit', 1
FROM t_voucher_template t, t_subject s
WHERE t.name = '办公费报销' AND s.code = '6602.01'
  AND NOT EXISTS (SELECT 1 FROM t_voucher_template_line WHERE template_id = t.id AND line_order = 1);

INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, line_order)
SELECT t.id, s.id, '', '{{amount}}', '{{summary}}', 'credit', 2
FROM t_voucher_template t, t_subject s
WHERE t.name = '办公费报销' AND s.code = '1002'
  AND NOT EXISTS (SELECT 1 FROM t_voucher_template_line WHERE template_id = t.id AND line_order = 2);

-- 6f. 业务招待费: 借 管理费用-业务招待费 / 贷 银行存款
INSERT INTO t_voucher_template (name, description, classification, number_prefix, is_active)
SELECT '业务招待费', '业务招待费报销: 借 管理费用-业务招待费, 贷 银行存款', 'entertainment_expense', 'JZ', TRUE
WHERE NOT EXISTS (SELECT 1 FROM t_voucher_template WHERE name = '业务招待费');

INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, line_order)
SELECT t.id, s.id, '{{amount}}', '', '{{summary}}', 'debit', 1
FROM t_voucher_template t, t_subject s
WHERE t.name = '业务招待费' AND s.code = '6602.03'
  AND NOT EXISTS (SELECT 1 FROM t_voucher_template_line WHERE template_id = t.id AND line_order = 1);

INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, line_order)
SELECT t.id, s.id, '', '{{amount}}', '{{summary}}', 'credit', 2
FROM t_voucher_template t, t_subject s
WHERE t.name = '业务招待费' AND s.code = '1002'
  AND NOT EXISTS (SELECT 1 FROM t_voucher_template_line WHERE template_id = t.id AND line_order = 2);

-- 6g. 银行提现: 借 库存现金 / 贷 银行存款
INSERT INTO t_voucher_template (name, description, classification, number_prefix, is_active)
SELECT '银行提现', '银行提取现金: 借 库存现金, 贷 银行存款', 'cash_withdraw', 'JZ', TRUE
WHERE NOT EXISTS (SELECT 1 FROM t_voucher_template WHERE name = '银行提现');

INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, line_order)
SELECT t.id, s.id, '{{amount}}', '', '提取现金备日常使用', 'debit', 1
FROM t_voucher_template t, t_subject s
WHERE t.name = '银行提现' AND s.code = '1001'
  AND NOT EXISTS (SELECT 1 FROM t_voucher_template_line WHERE template_id = t.id AND line_order = 1);

INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, line_order)
SELECT t.id, s.id, '', '{{amount}}', '提取现金备日常使用', 'credit', 2
FROM t_voucher_template t, t_subject s
WHERE t.name = '银行提现' AND s.code = '1002'
  AND NOT EXISTS (SELECT 1 FROM t_voucher_template_line WHERE template_id = t.id AND line_order = 2);

-- 6h. 缴纳税费: 借 应交税费 / 贷 银行存款
INSERT INTO t_voucher_template (name, description, classification, number_prefix, is_active)
SELECT '缴纳税费', '缴纳各项税费: 借 应交税费, 贷 银行存款', 'tax_manual', 'JZ', TRUE
WHERE NOT EXISTS (SELECT 1 FROM t_voucher_template WHERE name = '缴纳税费');

INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, line_order)
SELECT t.id, s.id, '{{amount}}', '', '缴纳{{taxName}}', 'debit', 1
FROM t_voucher_template t, t_subject s
WHERE t.name = '缴纳税费' AND s.code = '2221'
  AND NOT EXISTS (SELECT 1 FROM t_voucher_template_line WHERE template_id = t.id AND line_order = 1);

INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, line_order)
SELECT t.id, s.id, '', '{{amount}}', '缴纳{{taxName}}', 'credit', 2
FROM t_voucher_template t, t_subject s
WHERE t.name = '缴纳税费' AND s.code = '1002'
  AND NOT EXISTS (SELECT 1 FROM t_voucher_template_line WHERE template_id = t.id AND line_order = 2);

-- 6i. 结转销售成本: 借 主营业务成本 / 贷 库存商品
INSERT INTO t_voucher_template (name, description, classification, number_prefix, is_active)
SELECT '结转销售成本', '结转已售商品成本: 借 主营业务成本, 贷 库存商品', 'cogs_transfer', 'JZ', TRUE
WHERE NOT EXISTS (SELECT 1 FROM t_voucher_template WHERE name = '结转销售成本');

INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, line_order)
SELECT t.id, s.id, '{{amount}}', '', '结转{{month}}月销售成本', 'debit', 1
FROM t_voucher_template t, t_subject s
WHERE t.name = '结转销售成本' AND s.code = '6401'
  AND NOT EXISTS (SELECT 1 FROM t_voucher_template_line WHERE template_id = t.id AND line_order = 1);

INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, line_order)
SELECT t.id, s.id, '', '{{amount}}', '结转{{month}}月销售成本', 'credit', 2
FROM t_voucher_template t, t_subject s
WHERE t.name = '结转销售成本' AND s.code = '1405'
  AND NOT EXISTS (SELECT 1 FROM t_voucher_template_line WHERE template_id = t.id AND line_order = 2);

-- 6j. 收到货款: 借 银行存款 / 贷 应收账款
INSERT INTO t_voucher_template (name, description, classification, number_prefix, is_active)
SELECT '收到货款', '收到客户回款: 借 银行存款, 贷 应收账款', 'receipt', 'SK', TRUE
WHERE NOT EXISTS (SELECT 1 FROM t_voucher_template WHERE name = '收到货款');

INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, line_order)
SELECT t.id, s.id, '{{amount}}', '', '收到{{customerName}}货款', 'debit', 1
FROM t_voucher_template t, t_subject s
WHERE t.name = '收到货款' AND s.code = '1002'
  AND NOT EXISTS (SELECT 1 FROM t_voucher_template_line WHERE template_id = t.id AND line_order = 1);

INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, line_order)
SELECT t.id, s.id, '', '{{amount}}', '收到{{customerName}}货款', 'credit', 2
FROM t_voucher_template t, t_subject s
WHERE t.name = '收到货款' AND s.code = '1122'
  AND NOT EXISTS (SELECT 1 FROM t_voucher_template_line WHERE template_id = t.id AND line_order = 2);

-- 6k. 支付货款: 借 应付账款 / 贷 银行存款
INSERT INTO t_voucher_template (name, description, classification, number_prefix, is_active)
SELECT '支付货款', '支付供应商货款: 借 应付账款, 贷 银行存款', 'payment', 'FK', TRUE
WHERE NOT EXISTS (SELECT 1 FROM t_voucher_template WHERE name = '支付货款');

INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, line_order)
SELECT t.id, s.id, '{{amount}}', '', '支付{{vendorName}}货款', 'debit', 1
FROM t_voucher_template t, t_subject s
WHERE t.name = '支付货款' AND s.code = '2202'
  AND NOT EXISTS (SELECT 1 FROM t_voucher_template_line WHERE template_id = t.id AND line_order = 1);

INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, line_order)
SELECT t.id, s.id, '', '{{amount}}', '支付{{vendorName}}货款', 'credit', 2
FROM t_voucher_template t, t_subject s
WHERE t.name = '支付货款' AND s.code = '1002'
  AND NOT EXISTS (SELECT 1 FROM t_voucher_template_line WHERE template_id = t.id AND line_order = 2);

-- 6l. 银行利息收入: 借 银行存款 / 贷 财务费用-利息收入
INSERT INTO t_voucher_template (name, description, classification, number_prefix, is_active)
SELECT '银行利息收入', '银行利息收入入账: 借 银行存款, 贷 财务费用-利息收入', 'interest_manual', 'JZ', TRUE
WHERE NOT EXISTS (SELECT 1 FROM t_voucher_template WHERE name = '银行利息收入');

INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, line_order)
SELECT t.id, s.id, '{{amount}}', '', '收到{{month}}月银行利息', 'debit', 1
FROM t_voucher_template t, t_subject s
WHERE t.name = '银行利息收入' AND s.code = '1002'
  AND NOT EXISTS (SELECT 1 FROM t_voucher_template_line WHERE template_id = t.id AND line_order = 1);

INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, line_order)
SELECT t.id, s.id, '', '{{amount}}', '收到{{month}}月银行利息', 'credit', 2
FROM t_voucher_template t, t_subject s
WHERE t.name = '银行利息收入' AND s.code = '6603.02'
  AND NOT EXISTS (SELECT 1 FROM t_voucher_template_line WHERE template_id = t.id AND line_order = 2);

-- 6m. 银行手续费: 借 财务费用-手续费 / 贷 银行存款
INSERT INTO t_voucher_template (name, description, classification, number_prefix, is_active)
SELECT '支付手续费', '支付银行手续费: 借 财务费用-手续费, 贷 银行存款', 'bank_fee_manual', 'JZ', TRUE
WHERE NOT EXISTS (SELECT 1 FROM t_voucher_template WHERE name = '支付手续费');

INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, line_order)
SELECT t.id, s.id, '{{amount}}', '', '{{summary}}', 'debit', 1
FROM t_voucher_template t, t_subject s
WHERE t.name = '支付手续费' AND s.code = '6603.01'
  AND NOT EXISTS (SELECT 1 FROM t_voucher_template_line WHERE template_id = t.id AND line_order = 1);

INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, line_order)
SELECT t.id, s.id, '', '{{amount}}', '{{summary}}', 'credit', 2
FROM t_voucher_template t, t_subject s
WHERE t.name = '支付手续费' AND s.code = '1002'
  AND NOT EXISTS (SELECT 1 FROM t_voucher_template_line WHERE template_id = t.id AND line_order = 2);

-- 6n. 计提社保: 借 管理费用 / 贷 应付职工薪酬-社保
INSERT INTO t_voucher_template (name, description, classification, number_prefix, is_active)
SELECT '计提社保', '计提单位社保费用: 借 管理费用, 贷 应付职工薪酬-社保', 'social_security_accrual', 'JZ', TRUE
WHERE NOT EXISTS (SELECT 1 FROM t_voucher_template WHERE name = '计提社保');

INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, line_order)
SELECT t.id, s.id, '{{amount}}', '', '计提{{month}}月单位社保', 'debit', 1
FROM t_voucher_template t, t_subject s
WHERE t.name = '计提社保' AND s.code = '6602'
  AND NOT EXISTS (SELECT 1 FROM t_voucher_template_line WHERE template_id = t.id AND line_order = 1);

INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, line_order)
SELECT t.id, s.id, '', '{{amount}}', '计提{{month}}月单位社保', 'credit', 2
FROM t_voucher_template t, t_subject s
WHERE t.name = '计提社保' AND s.code = '2211.02'
  AND NOT EXISTS (SELECT 1 FROM t_voucher_template_line WHERE template_id = t.id AND line_order = 2);

-- 6o. 预付款项: 借 预付账款 / 贷 银行存款
INSERT INTO t_voucher_template (name, description, classification, number_prefix, is_active)
SELECT '预付款项', '预付供应商款项: 借 预付账款, 贷 银行存款', 'prepayment', 'FK', TRUE
WHERE NOT EXISTS (SELECT 1 FROM t_voucher_template WHERE name = '预付款项');

INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, line_order)
SELECT t.id, s.id, '{{amount}}', '', '预付{{vendorName}}货款', 'debit', 1
FROM t_voucher_template t, t_subject s
WHERE t.name = '预付款项' AND s.code = '1123'
  AND NOT EXISTS (SELECT 1 FROM t_voucher_template_line WHERE template_id = t.id AND line_order = 1);

INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, line_order)
SELECT t.id, s.id, '', '{{amount}}', '预付{{vendorName}}货款', 'credit', 2
FROM t_voucher_template t, t_subject s
WHERE t.name = '预付款项' AND s.code = '1002'
  AND NOT EXISTS (SELECT 1 FROM t_voucher_template_line WHERE template_id = t.id AND line_order = 2);

-- 6p. 预收款项: 借 银行存款 / 贷 预收账款
INSERT INTO t_voucher_template (name, description, classification, number_prefix, is_active)
SELECT '预收款项', '预收客户款项: 借 银行存款, 贷 预收账款', 'advance_receipt', 'SK', TRUE
WHERE NOT EXISTS (SELECT 1 FROM t_voucher_template WHERE name = '预收款项');

INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, line_order)
SELECT t.id, s.id, '{{amount}}', '', '预收{{customerName}}款项', 'debit', 1
FROM t_voucher_template t, t_subject s
WHERE t.name = '预收款项' AND s.code = '1002'
  AND NOT EXISTS (SELECT 1 FROM t_voucher_template_line WHERE template_id = t.id AND line_order = 1);

INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, line_order)
SELECT t.id, s.id, '', '{{amount}}', '预收{{customerName}}款项', 'credit', 2
FROM t_voucher_template t, t_subject s
WHERE t.name = '预收款项' AND s.code = '2203'
  AND NOT EXISTS (SELECT 1 FROM t_voucher_template_line WHERE template_id = t.id AND line_order = 2);

COMMIT;
