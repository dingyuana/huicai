-- ============================================================
-- V86: 新增坏账科目 + NOTE_RECEIVABLE doc_type + 凭证模板
-- ============================================================
BEGIN;

-- 1. 科目 1231 坏账准备（资产类备抵，贷方余额）
INSERT INTO t_subject (code, name, parent_id, level, direction, is_leaf, is_active)
SELECT '1231', '坏账准备', NULL, 1, 'credit', TRUE, TRUE
WHERE NOT EXISTS (SELECT 1 FROM t_subject WHERE code = '1231');

-- 科目 6701 信用减值损失（损益类，借方余额）
INSERT INTO t_subject (code, name, parent_id, level, direction, is_leaf, is_active)
SELECT '6701', '信用减值损失', NULL, 1, 'debit', TRUE, TRUE
WHERE NOT EXISTS (SELECT 1 FROM t_subject WHERE code = '6701');

-- 2. 扩展 chk_doc_type 约束，添加 NOTE_RECEIVABLE
ALTER TABLE t_business_doc DROP CONSTRAINT IF EXISTS chk_doc_type;
ALTER TABLE t_business_doc ADD CONSTRAINT chk_doc_type CHECK (
    doc_type IN ('RECEIPT','PAYMENT','EXPENSE','INVOICE_IN','INVOICE_OUT',
                 'OTHER_RECEIVABLE','OTHER_PAYABLE','NOTE_RECEIVABLE')
);

-- 3. NOTE_RECEIVABLE 凭证模板
INSERT INTO t_voucher_template (name, description, source, business_type, match_priority, number_prefix, is_active)
SELECT '应收票据制证', '应收票据生成凭证: 借 应收票据, 贷 银行存款', 'BUSINESS_DOC', 'NOTE_RECEIVABLE', 0, 'JZ', TRUE
WHERE NOT EXISTS (SELECT 1 FROM t_voucher_template WHERE source = 'BUSINESS_DOC' AND business_type = 'NOTE_RECEIVABLE');

COMMIT;
