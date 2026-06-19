-- V42: 往来核销结算凭证模板
-- 与 V40 核销场景模板配套, 分类名对齐代码中的 settlement_receivable/settlement_payment
-- 依赖科目: 1002(银行存款) / 1122(应收账款) / 2202(应付账款) — 已在 V21/V23 中存在

BEGIN;

-- 1. 结算-应收: 收款冲应收 → 借:银行存款 1002 / 贷:应收账款 1122
INSERT INTO t_voucher_template (name, description, classification, number_prefix, is_active)
SELECT '结算-应收', '往来核销结算(应收): 借 银行存款 1002, 贷 应收账款 1122', 'settlement_receivable', 'JZ', TRUE
WHERE NOT EXISTS (SELECT 1 FROM t_voucher_template WHERE classification = 'settlement_receivable');

INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, line_order)
SELECT t.id, s.id, '{{amount}}', '', '核销结算: {{settlementNo}}', 'debit', 1
FROM t_voucher_template t, t_subject s
WHERE t.classification = 'settlement_receivable' AND s.code = '1002'
  AND NOT EXISTS (SELECT 1 FROM t_voucher_template_line WHERE template_id = t.id);

INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, line_order)
SELECT t.id, s.id, '', '{{amount}}', '核销结算: {{settlementNo}}', 'credit', 2
FROM t_voucher_template t, t_subject s
WHERE t.classification = 'settlement_receivable' AND s.code = '1122'
  AND NOT EXISTS (SELECT 1 FROM t_voucher_template_line WHERE template_id = t.id);

-- 2. 结算-应付: 付款冲应付 → 借:应付账款 2202 / 贷:银行存款 1002
INSERT INTO t_voucher_template (name, description, classification, number_prefix, is_active)
SELECT '结算-应付', '往来核销结算(应付): 借 应付账款 2202, 贷 银行存款 1002', 'settlement_payment', 'JZ', TRUE
WHERE NOT EXISTS (SELECT 1 FROM t_voucher_template WHERE classification = 'settlement_payment');

INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, line_order)
SELECT t.id, s.id, '{{amount}}', '', '核销结算: {{settlementNo}}', 'debit', 1
FROM t_voucher_template t, t_subject s
WHERE t.classification = 'settlement_payment' AND s.code = '2202'
  AND NOT EXISTS (SELECT 1 FROM t_voucher_template_line WHERE template_id = t.id);

INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, line_order)
SELECT t.id, s.id, '', '{{amount}}', '核销结算: {{settlementNo}}', 'credit', 2
FROM t_voucher_template t, t_subject s
WHERE t.classification = 'settlement_payment' AND s.code = '1002'
  AND NOT EXISTS (SELECT 1 FROM t_voucher_template_line WHERE template_id = t.id);

COMMIT;
