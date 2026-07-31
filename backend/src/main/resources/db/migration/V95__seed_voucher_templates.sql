-- ============================================================
-- V95: 恢复凭证模板种子数据（t_voucher_template + t_voucher_template_line）
-- 说明：重建数据库后模板表为空，此处一次性补建 7 条常用模板及其分录行
-- ============================================================

-- ===================== t_voucher_template =====================
-- 注：id 列为 GENERATED ALWAYS AS IDENTITY，需 OVERRIDING SYSTEM VALUE 显式指定 id
-- entries JSONB 格式: [{summary, debitSubjectCode, creditSubjectCode, amount, lineOrder}, ...]

INSERT INTO t_voucher_template (id, template_code, template_name, doc_type, voucher_type_code, summary, entries, is_active, remark, deleted) OVERRIDING SYSTEM VALUE VALUES
(1, 'TPL_SALES', '销售收款模板', 'OUTPUT_INVOICE', 'SK', '销售收款',
 '[
   {"lineOrder":1, "summary":"收到货款", "debitSubjectCode":"1002", "creditSubjectCode":"6001", "amount": "{{amount}}", "description":"借：银行存款 / 贷：主营业务收入"},
   {"lineOrder":2, "summary":"销项税额", "debitSubjectCode":"1002", "creditSubjectCode":"2221", "amount": "{{taxAmount}}", "description":"借：银行存款 / 贷：应交税费-销项税"}
 ]'::jsonb,
 true, '销售发票收款：借银行存款 贷主营业务收入+应交税费', 0),

(2, 'TPL_PURCHASE', '采购付款模板', 'INPUT_INVOICE', 'FK', '采购付款',
 '[
   {"lineOrder":1, "summary":"采购成本", "debitSubjectCode":"5001", "creditSubjectCode":"2202", "amount": "{{amount}}", "description":"借：生产成本 / 贷：应付账款"},
   {"lineOrder":2, "summary":"进项税额", "debitSubjectCode":"6402", "creditSubjectCode":"1002", "amount": "{{taxAmount}}", "description":"借：其他业务成本-进项税 / 贷：银行存款"}
 ]'::jsonb,
 true, '采购发票付款：借生产成本 贷应付账款+应交税费', 0),

(3, 'TPL_EXPENSE', '费用报销模板', 'EXPENSE_REIMBURSEMENT', 'FK', '报销费用',
 '[
   {"lineOrder":1, "summary":"费用报销", "debitSubjectCode":"6602", "creditSubjectCode":"1002", "amount": "{{amount}}", "description":"借：管理费用 / 贷：银行存款"},
   {"lineOrder":2, "summary":"代扣个税", "debitSubjectCode":"1002", "creditSubjectCode":"2211", "amount": "{{taxAmount}}", "description":"借：银行存款 / 贷：应付职工薪酬-代扣个税"}
 ]'::jsonb,
 true, '费用报销：借管理费用 贷银行存款', 0),

(4, 'TPL_CASH_IN', '现金存行模板', 'INTERNAL_TRANSFER', 'ZZ', '现金存行',
 '[
   {"lineOrder":1, "summary":"现金存入银行", "debitSubjectCode":"1002", "creditSubjectCode":"1001", "amount": "{{amount}}", "description":"借：银行存款 / 贷：库存现金"}
 ]'::jsonb,
 true, '现金存入银行：借银行存款 贷库存现金', 0),

(5, 'TPL_ASSET', '固定资产购入模板', 'ASSET_CARD', 'FK', '购入固定资产',
 '[
   {"lineOrder":1, "summary":"购入固定资产", "debitSubjectCode":"1601", "creditSubjectCode":"2202", "amount": "{{amount}}", "description":"借：固定资产 / 贷：应付账款"},
   {"lineOrder":2, "summary":"进项税额", "debitSubjectCode":"2221", "creditSubjectCode":"1002", "amount": "{{taxAmount}}", "description":"借：应交税费-进项税 / 贷：银行存款"}
 ]'::jsonb,
 true, '固定资产购入：借固定资产 贷应付账款+银行存款', 0),

(6, 'TPL_TAX_PAY', '缴纳税费模板', 'TAX_DECLARATION', 'FK', '缴纳税费',
 '[
   {"lineOrder":1, "summary":"缴纳增值税", "debitSubjectCode":"2221", "creditSubjectCode":"1002", "amount": "{{amount}}", "description":"借：应交税费-增值税 / 贷：银行存款"}
 ]'::jsonb,
 true, '缴纳税费：借应交税费 贷银行存款', 0),

(7, 'TPL_PERIOD_CLOSE', '期末结转模板', 'PERIOD_CLOSE', 'ZZ', '期末结转',
 '[
   {"lineOrder":1, "summary":"结转收入", "debitSubjectCode":"6001", "creditSubjectCode":"4103", "amount": "{{amount}}", "description":"借：主营业务收入 / 贷：本年利润"},
   {"lineOrder":2, "summary":"结转成本", "debitSubjectCode":"4103", "creditSubjectCode":"6401", "amount": "{{amount}}", "description":"借：本年利润 / 贷：主营业务成本"}
 ]'::jsonb,
 true, '期末结转：收入成本结转本年利润', 0);
