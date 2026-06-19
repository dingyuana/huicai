-- V40: 核销场景凭证模板
-- 覆盖 P0 需求: 收款核销、付款核销、预收冲应收
-- 注意: 模板依赖科目 1002(银行存款) / 1122(应收账款) / 2202(应付账款) / 2203(预收账款)
--       这些科目在 V21/V23 中已存在

BEGIN;

-- 1. 收款核销: 银行回款 → 应收
--    借:银行存款 1002 / 贷:应收账款 1122
INSERT INTO t_voucher_template (name, description, classification, number_prefix, is_active)
SELECT '收款核销', '银行收款冲销应收: 借 银行存款 1002, 贷 应收账款 1122', 'reconciliation_receipt', 'JZ', TRUE
WHERE NOT EXISTS (SELECT 1 FROM t_voucher_template WHERE name = '收款核销');

INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, line_order)
SELECT t.id, s.id, '{{amount}}', '', '收款核销: {{summary}}', 'debit', 1
FROM t_voucher_template t, t_subject s
WHERE t.name = '收款核销' AND s.code = '1002'
  AND NOT EXISTS (SELECT 1 FROM t_voucher_template_line WHERE template_id = t.id);

INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, line_order)
SELECT t.id, s.id, '', '{{amount}}', '收款核销: {{summary}}', 'credit', 2
FROM t_voucher_template t, t_subject s
WHERE t.name = '收款核销' AND s.code = '1122'
  AND NOT EXISTS (SELECT 1 FROM t_voucher_template_line WHERE template_id = t.id);

-- 2. 付款核销: 应付 → 银行付款
--    借:应付账款 2202 / 贷:银行存款 1002
INSERT INTO t_voucher_template (name, description, classification, number_prefix, is_active)
SELECT '付款核销', '银行付款冲销应付: 借 应付账款 2202, 贷 银行存款 1002', 'reconciliation_payment', 'JZ', TRUE
WHERE NOT EXISTS (SELECT 1 FROM t_voucher_template WHERE name = '付款核销');

INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, line_order)
SELECT t.id, s.id, '{{amount}}', '', '付款核销: {{summary}}', 'debit', 1
FROM t_voucher_template t, t_subject s
WHERE t.name = '付款核销' AND s.code = '2202'
  AND NOT EXISTS (SELECT 1 FROM t_voucher_template_line WHERE template_id = t.id);

INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, line_order)
SELECT t.id, s.id, '', '{{amount}}', '付款核销: {{summary}}', 'credit', 2
FROM t_voucher_template t, t_subject s
WHERE t.name = '付款核销' AND s.code = '1002'
  AND NOT EXISTS (SELECT 1 FROM t_voucher_template_line WHERE template_id = t.id);

-- 3. 预收冲应收: 预收账款 → 应收账款
--    借:预收账款 2203 / 贷:应收账款 1122
INSERT INTO t_voucher_template (name, description, classification, number_prefix, is_active)
SELECT '预收冲应收', '预收账款冲销应收: 借 预收账款 2203, 贷 应收账款 1122', 'prepayment_offset', 'JZ', TRUE
WHERE NOT EXISTS (SELECT 1 FROM t_voucher_template WHERE name = '预收冲应收');

INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, line_order)
SELECT t.id, s.id, '{{amount}}', '', '预收冲应收: {{summary}}', 'debit', 1
FROM t_voucher_template t, t_subject s
WHERE t.name = '预收冲应收' AND s.code = '2203'
  AND NOT EXISTS (SELECT 1 FROM t_voucher_template_line WHERE template_id = t.id);

INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, line_order)
SELECT t.id, s.id, '', '{{amount}}', '预收冲应收: {{summary}}', 'credit', 2
FROM t_voucher_template t, t_subject s
WHERE t.name = '预收冲应收' AND s.code = '1122'
  AND NOT EXISTS (SELECT 1 FROM t_voucher_template_line WHERE template_id = t.id);

-- 4. 核销差额调整: 用于尾差/手续费/折扣调整
--    借:财务费用-手续费/尾差 / 贷:应收账款 (收款调整)
--    或 借:应付账款 / 贷:财务费用-手续费/尾差 (付款调整)
--    使用通用 6602 (财务费用) 科目
INSERT INTO t_voucher_template (name, description, classification, number_prefix, is_active)
SELECT '核销差额调整', '核销尾差/折扣调整: 差额计入财务费用', 'reconciliation_adjustment', 'JZ', TRUE
WHERE NOT EXISTS (SELECT 1 FROM t_voucher_template WHERE name = '核销差额调整');

INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, line_order)
SELECT t.id, s.id, '{{amount}}', '', '核销差额: {{summary}}', 'debit', 1
FROM t_voucher_template t, t_subject s
WHERE t.name = '核销差额调整' AND s.code = '6602'
  AND NOT EXISTS (SELECT 1 FROM t_voucher_template_line WHERE template_id = t.id);

INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, line_order)
SELECT t.id, s.id, '', '{{amount}}', '核销差额: {{summary}}', 'credit', 2
FROM t_voucher_template t, t_subject s
WHERE t.name = '核销差额调整' AND s.code = '1122'
  AND NOT EXISTS (SELECT 1 FROM t_voucher_template_line WHERE template_id = t.id);

COMMIT;
