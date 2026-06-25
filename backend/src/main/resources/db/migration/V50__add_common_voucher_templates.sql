-- V50: 常用凭证模板（覆盖发票 + 7 种业务单据）
-- 补充 P26 模板引擎的种子数据，使 INVOICE / BUSINESS_DOC 来源无需降级硬编码
-- 设计依据：
--   TaxServiceImpl.generateVoucherFromInvoice() 硬编码（1122/5001/2221.01）
--   BusinessDocServiceImpl.DOC_VOUCHER_SUBJECTS 硬编码（7 种单据类型）
--
-- 注意：科目引用采用 JOIN 查 code 的方式（与 V23/V40/V42 同模式），
--       若某科目未初始化则对应模板行自动跳过，不影响其他模板的插入。

BEGIN;

-- ============================================================
-- 1. 销售发票模板（INVOICE source）
-- ============================================================
INSERT INTO t_voucher_template (name, description, source, business_type, match_priority, number_prefix, is_active)
SELECT '销售发票制证', '销售发票生成凭证: 借 应收账款 1122, 贷 主营收入 5001 + 销项税 2221.01', 'INVOICE', 'INVOICE_OUT', 0, 'JZ', TRUE
WHERE NOT EXISTS (SELECT 1 FROM t_voucher_template WHERE source = 'INVOICE' AND business_type = 'INVOICE_OUT');

-- 第 1 行：借 1122 应收账款（价税合计）
INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, line_order)
SELECT t.id, s.id, '{{totalAmount}}', '', '{客户名称}', 'debit', 1
FROM t_voucher_template t, t_subject s
WHERE t.source = 'INVOICE' AND t.business_type = 'INVOICE_OUT' AND s.code IN ('1122')
  AND NOT EXISTS (SELECT 1 FROM t_voucher_template_line l WHERE l.template_id = t.id AND l.line_order = 1);

-- 第 2 行：贷 5001 主营业务收入（不含税金额）
INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, line_order)
SELECT t.id, s.id, '', '{{amount}}', '{客户名称}', 'credit', 2
FROM t_voucher_template t, t_subject s
WHERE t.source = 'INVOICE' AND t.business_type = 'INVOICE_OUT' AND s.code IN ('5001')
  AND NOT EXISTS (SELECT 1 FROM t_voucher_template_line l WHERE l.template_id = t.id AND l.line_order = 2);

-- 第 3 行：贷 2221.01 应交税费-销项税（税额）
INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, line_order)
SELECT t.id, s.id, '', '{{taxAmount}}', '{客户名称}', 'credit', 3
FROM t_voucher_template t, t_subject s
WHERE t.source = 'INVOICE' AND t.business_type = 'INVOICE_OUT' AND s.code = '2221.01'
  AND NOT EXISTS (SELECT 1 FROM t_voucher_template_line l WHERE l.template_id = t.id AND l.line_order = 3);

-- ============================================================
-- 2. 业务单据模板（BUSINESS_DOC source）
-- ============================================================

-- 2a. RECEIPT 收款：借 1002 银行存款 / 贷 1122 应收账款
INSERT INTO t_voucher_template (name, description, source, business_type, match_priority, number_prefix, is_active)
SELECT '收款制证', '收款单生成凭证: 借 银行存款 1002, 贷 应收账款 1122', 'BUSINESS_DOC', 'RECEIPT', 0, 'JZ', TRUE
WHERE NOT EXISTS (SELECT 1 FROM t_voucher_template WHERE source = 'BUSINESS_DOC' AND business_type = 'RECEIPT');

INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, line_order)
SELECT t.id, s.id, '{{amount}}', '', '{摘要}', 'debit', 1
FROM t_voucher_template t, t_subject s
WHERE t.source = 'BUSINESS_DOC' AND t.business_type = 'RECEIPT' AND s.code IN ('1002')
  AND NOT EXISTS (SELECT 1 FROM t_voucher_template_line l WHERE l.template_id = t.id AND l.line_order = 1);

INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, line_order)
SELECT t.id, s.id, '', '{{amount}}', '{摘要}', 'credit', 2
FROM t_voucher_template t, t_subject s
WHERE t.source = 'BUSINESS_DOC' AND t.business_type = 'RECEIPT' AND s.code IN ('1122')
  AND NOT EXISTS (SELECT 1 FROM t_voucher_template_line l WHERE l.template_id = t.id AND l.line_order = 2);

-- 2b. PAYMENT 付款：借 2202 应付账款 / 贷 1002 银行存款
INSERT INTO t_voucher_template (name, description, source, business_type, match_priority, number_prefix, is_active)
SELECT '付款制证', '付款单生成凭证: 借 应付账款 2202, 贷 银行存款 1002', 'BUSINESS_DOC', 'PAYMENT', 0, 'JZ', TRUE
WHERE NOT EXISTS (SELECT 1 FROM t_voucher_template WHERE source = 'BUSINESS_DOC' AND business_type = 'PAYMENT');

INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, line_order)
SELECT t.id, s.id, '{{amount}}', '', '{摘要}', 'debit', 1
FROM t_voucher_template t, t_subject s
WHERE t.source = 'BUSINESS_DOC' AND t.business_type = 'PAYMENT' AND s.code IN ('2202')
  AND NOT EXISTS (SELECT 1 FROM t_voucher_template_line l WHERE l.template_id = t.id AND l.line_order = 1);

INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, line_order)
SELECT t.id, s.id, '', '{{amount}}', '{摘要}', 'credit', 2
FROM t_voucher_template t, t_subject s
WHERE t.source = 'BUSINESS_DOC' AND t.business_type = 'PAYMENT' AND s.code IN ('1002')
  AND NOT EXISTS (SELECT 1 FROM t_voucher_template_line l WHERE l.template_id = t.id AND l.line_order = 2);

-- 2c. EXPENSE 报销：借 6602 管理费用 / 贷 1002 银行存款
INSERT INTO t_voucher_template (name, description, source, business_type, match_priority, number_prefix, is_active)
SELECT '报销制证', '报销单生成凭证: 借 管理费用 6602, 贷 银行存款 1002', 'BUSINESS_DOC', 'EXPENSE', 0, 'JZ', TRUE
WHERE NOT EXISTS (SELECT 1 FROM t_voucher_template WHERE source = 'BUSINESS_DOC' AND business_type = 'EXPENSE');

INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, line_order)
SELECT t.id, s.id, '{{amount}}', '', '{摘要}', 'debit', 1
FROM t_voucher_template t, t_subject s
WHERE t.source = 'BUSINESS_DOC' AND t.business_type = 'EXPENSE' AND s.code IN ('6602')
  AND NOT EXISTS (SELECT 1 FROM t_voucher_template_line l WHERE l.template_id = t.id AND l.line_order = 1);

INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, line_order)
SELECT t.id, s.id, '', '{{amount}}', '{摘要}', 'credit', 2
FROM t_voucher_template t, t_subject s
WHERE t.source = 'BUSINESS_DOC' AND t.business_type = 'EXPENSE' AND s.code IN ('1002')
  AND NOT EXISTS (SELECT 1 FROM t_voucher_template_line l WHERE l.template_id = t.id AND l.line_order = 2);

-- 2d. INVOICE_IN 采购发票：借 1403 在途物资 / 贷 2202 应付账款
INSERT INTO t_voucher_template (name, description, source, business_type, match_priority, number_prefix, is_active)
SELECT '采购发票制证', '采购发票生成凭证: 借 在途物资 1403, 贷 应付账款 2202', 'BUSINESS_DOC', 'INVOICE_IN', 0, 'JZ', TRUE
WHERE NOT EXISTS (SELECT 1 FROM t_voucher_template WHERE source = 'BUSINESS_DOC' AND business_type = 'INVOICE_IN');

INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, line_order)
SELECT t.id, s.id, '{{amount}}', '', '{摘要}', 'debit', 1
FROM t_voucher_template t, t_subject s
WHERE t.source = 'BUSINESS_DOC' AND t.business_type = 'INVOICE_IN' AND s.code IN ('1403')
  AND NOT EXISTS (SELECT 1 FROM t_voucher_template_line l WHERE l.template_id = t.id AND l.line_order = 1);

INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, line_order)
SELECT t.id, s.id, '', '{{amount}}', '{摘要}', 'credit', 2
FROM t_voucher_template t, t_subject s
WHERE t.source = 'BUSINESS_DOC' AND t.business_type = 'INVOICE_IN' AND s.code IN ('2202')
  AND NOT EXISTS (SELECT 1 FROM t_voucher_template_line l WHERE l.template_id = t.id AND l.line_order = 2);

-- 2e. INVOICE_OUT 销售发票单据：借 1122 应收账款 / 贷 6001 主营业务收入
INSERT INTO t_voucher_template (name, description, source, business_type, match_priority, number_prefix, is_active)
SELECT '销售单据制证', '销售单据生成凭证: 借 应收账款 1122, 贷 主营业务收入 6001', 'BUSINESS_DOC', 'INVOICE_OUT', 0, 'JZ', TRUE
WHERE NOT EXISTS (SELECT 1 FROM t_voucher_template WHERE source = 'BUSINESS_DOC' AND business_type = 'INVOICE_OUT');

INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, line_order)
SELECT t.id, s.id, '{{amount}}', '', '{摘要}', 'debit', 1
FROM t_voucher_template t, t_subject s
WHERE t.source = 'BUSINESS_DOC' AND t.business_type = 'INVOICE_OUT' AND s.code IN ('1122')
  AND NOT EXISTS (SELECT 1 FROM t_voucher_template_line l WHERE l.template_id = t.id AND l.line_order = 1);

INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, line_order)
SELECT t.id, s.id, '', '{{amount}}', '{摘要}', 'credit', 2
FROM t_voucher_template t, t_subject s
WHERE t.source = 'BUSINESS_DOC' AND t.business_type = 'INVOICE_OUT' AND s.code IN ('6001')
  AND NOT EXISTS (SELECT 1 FROM t_voucher_template_line l WHERE l.template_id = t.id AND l.line_order = 2);

-- 2f. OTHER_RECEIVABLE 其他应收：借 1221 其他应收款 / 贷 1002 银行存款
INSERT INTO t_voucher_template (name, description, source, business_type, match_priority, number_prefix, is_active)
SELECT '其他应收制证', '其他应收单生成凭证: 借 其他应收款 1221, 贷 银行存款 1002', 'BUSINESS_DOC', 'OTHER_RECEIVABLE', 0, 'JZ', TRUE
WHERE NOT EXISTS (SELECT 1 FROM t_voucher_template WHERE source = 'BUSINESS_DOC' AND business_type = 'OTHER_RECEIVABLE');

INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, line_order)
SELECT t.id, s.id, '{{amount}}', '', '{摘要}', 'debit', 1
FROM t_voucher_template t, t_subject s
WHERE t.source = 'BUSINESS_DOC' AND t.business_type = 'OTHER_RECEIVABLE' AND s.code IN ('1221')
  AND NOT EXISTS (SELECT 1 FROM t_voucher_template_line l WHERE l.template_id = t.id AND l.line_order = 1);

INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, line_order)
SELECT t.id, s.id, '', '{{amount}}', '{摘要}', 'credit', 2
FROM t_voucher_template t, t_subject s
WHERE t.source = 'BUSINESS_DOC' AND t.business_type = 'OTHER_RECEIVABLE' AND s.code IN ('1002')
  AND NOT EXISTS (SELECT 1 FROM t_voucher_template_line l WHERE l.template_id = t.id AND l.line_order = 2);

-- 2g. OTHER_PAYABLE 其他应付：借 1002 银行存款 / 贷 2241 其他应付款
INSERT INTO t_voucher_template (name, description, source, business_type, match_priority, number_prefix, is_active)
SELECT '其他应付制证', '其他应付单生成凭证: 借 银行存款 1002, 贷 其他应付款 2241', 'BUSINESS_DOC', 'OTHER_PAYABLE', 0, 'JZ', TRUE
WHERE NOT EXISTS (SELECT 1 FROM t_voucher_template WHERE source = 'BUSINESS_DOC' AND business_type = 'OTHER_PAYABLE');

INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, line_order)
SELECT t.id, s.id, '{{amount}}', '', '{摘要}', 'debit', 1
FROM t_voucher_template t, t_subject s
WHERE t.source = 'BUSINESS_DOC' AND t.business_type = 'OTHER_PAYABLE' AND s.code IN ('1002')
  AND NOT EXISTS (SELECT 1 FROM t_voucher_template_line l WHERE l.template_id = t.id AND l.line_order = 1);

INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, line_order)
SELECT t.id, s.id, '', '{{amount}}', '{摘要}', 'credit', 2
FROM t_voucher_template t, t_subject s
WHERE t.source = 'BUSINESS_DOC' AND t.business_type = 'OTHER_PAYABLE' AND s.code IN ('2241')
  AND NOT EXISTS (SELECT 1 FROM t_voucher_template_line l WHERE l.template_id = t.id AND l.line_order = 2);

COMMIT;