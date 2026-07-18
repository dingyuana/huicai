-- ============================================================
-- V76: 迁移银行流水旧分类值到新8类体系（补充V75未运行的数据迁移）
-- 
-- 旧分类: bank_fee → bank_interest_fee
--         interest_income → bank_interest_fee
--         tax_payment → tax_withholding
--         social_security → salary_social
--         insurance_fee → salary_social
--         salary_payment → salary_social
--         pending → other_unknown
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
