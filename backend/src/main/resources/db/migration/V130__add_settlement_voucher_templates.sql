-- ============================================================
-- V130: 新增核销单凭证模板 (settlement_receivable / settlement_payment)
--
-- 背景：ArapSettlementServiceImpl.generateVoucher() 通过
-- VoucherTemplateService.matchByClassification(classification)
-- 按 doc_type 查找模板，其中 classification = "settlement_receivable"
-- 或 "settlement_payment"。缺少模板导致 500 错误。
-- ============================================================

DO $$
DECLARE
    v_tpl_id BIGINT;
BEGIN
    -- ─── 1. settlement_receivable（应收核销）──────────────
    -- 借：银行存款(1002) / 贷：应收账款(1122)
    INSERT INTO t_voucher_template (id, template_code, template_name, doc_type, voucher_type_code, summary, entries, is_active, enterprise_id)
    OVERRIDING SYSTEM VALUE
    VALUES (8, 'TPL_SETTLEMENT_RECEIVABLE', '核销收款模板', 'settlement_receivable', 'SK', '应收核销',
            '[{"amount": "{{amount}}", "summary": "应收核销", "lineOrder": 1, "description": "借：银行存款 / 贷：应收账款", "debitSubjectCode": "1002", "creditSubjectCode": "1122"}]'::jsonb,
            TRUE, 1)
    RETURNING id INTO v_tpl_id;

    INSERT INTO t_voucher_template_line (template_id, subject_id, direction, summary_template, line_order, enterprise_id)
    VALUES (v_tpl_id, (SELECT id FROM t_subject WHERE code = '1002' AND enterprise_id = 1), 'debit',  '应收核销-收款', 1, 1);
    INSERT INTO t_voucher_template_line (template_id, subject_id, direction, summary_template, line_order, enterprise_id)
    VALUES (v_tpl_id, (SELECT id FROM t_subject WHERE code = '1122' AND enterprise_id = 1), 'credit', '应收核销-收款', 1, 1);

    -- ─── 2. settlement_payment（应付核销）────────────────
    -- 借：应付账款(2202) / 贷：银行存款(1002)
    INSERT INTO t_voucher_template (id, template_code, template_name, doc_type, voucher_type_code, summary, entries, is_active, enterprise_id)
    OVERRIDING SYSTEM VALUE
    VALUES (9, 'TPL_SETTLEMENT_PAYMENT', '核销付款模板', 'settlement_payment', 'FK', '应付核销',
            '[{"amount": "{{amount}}", "summary": "应付核销", "lineOrder": 1, "description": "借：应付账款 / 贷：银行存款", "debitSubjectCode": "2202", "creditSubjectCode": "1002"}]'::jsonb,
            TRUE, 1)
    RETURNING id INTO v_tpl_id;

    INSERT INTO t_voucher_template_line (template_id, subject_id, direction, summary_template, line_order, enterprise_id)
    VALUES (v_tpl_id, (SELECT id FROM t_subject WHERE code = '2202' AND enterprise_id = 1), 'debit',  '应付核销-付款', 1, 1);
    INSERT INTO t_voucher_template_line (template_id, subject_id, direction, summary_template, line_order, enterprise_id)
    VALUES (v_tpl_id, (SELECT id FROM t_subject WHERE code = '1002' AND enterprise_id = 1), 'credit', '应付核销-付款', 1, 1);
END $$;