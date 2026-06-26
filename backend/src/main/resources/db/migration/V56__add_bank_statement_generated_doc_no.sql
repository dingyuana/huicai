-- V56: 补 t_bank_statement generated_doc_no / generated_voucher_no 列
-- 2026-06-26
-- 背景: BankStatementEntity.generatedDocNo/generatedVoucherNo 字段已在 Entity 定义,
-- BankStatementServiceImpl 已写入这两个字段, 但 PG 表缺少对应列(影子字段).
-- 影响: 写入的值实际落不到数据库, 业务逻辑等于白写.

ALTER TABLE t_bank_statement
    ADD COLUMN IF NOT EXISTS generated_doc_no VARCHAR(32),
    ADD COLUMN IF NOT EXISTS generated_voucher_no VARCHAR(32);

COMMENT ON COLUMN t_bank_statement.generated_doc_no IS '生成的单据编号';
COMMENT ON COLUMN t_bank_statement.generated_voucher_no IS '生成的凭证编号';
