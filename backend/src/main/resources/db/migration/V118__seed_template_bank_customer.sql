-- V118: 修复银行账户和客户唯一约束（支持多租户），并补充模板企业种子数据

-- 1. 修复唯一约束为复合约束（支持不同企业有相同 account_no/code）
ALTER TABLE t_bank_account DROP CONSTRAINT IF EXISTS uq_bank_account_no;
ALTER TABLE t_bank_account ADD CONSTRAINT uq_bank_account_no_enterprise UNIQUE (account_no, enterprise_id);

ALTER TABLE t_customer DROP CONSTRAINT IF EXISTS uq_customer_code;
ALTER TABLE t_customer ADD CONSTRAINT uq_customer_code_enterprise UNIQUE (code, enterprise_id);

-- 2. 银行账户模板数据
INSERT INTO t_bank_account (enterprise_id, account_no, account_name, bank_name, currency,
    subject_id, balance, is_active, remark, created_at, updated_at, deleted)
SELECT 0, account_no, account_name, bank_name, currency,
    subject_id, balance, is_active, remark, NOW(), NOW(), 0
FROM t_bank_account WHERE enterprise_id = 1 AND deleted = 0
ON CONFLICT (account_no, enterprise_id) DO NOTHING;

-- 3. 客户模板数据
INSERT INTO t_customer (enterprise_id, code, name, contact_person, phone, email, address,
    tax_no, bank_name, bank_account, credit_limit, credit_days, subject_id, is_active, remark,
    created_at, updated_at, deleted)
SELECT 0, code, name, contact_person, phone, email, address,
    tax_no, bank_name, bank_account, credit_limit, credit_days, subject_id, is_active, remark,
    NOW(), NOW(), 0
FROM t_customer WHERE enterprise_id = 1 AND deleted = 0
ON CONFLICT (code, enterprise_id) DO NOTHING;