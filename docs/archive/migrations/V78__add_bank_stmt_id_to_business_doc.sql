-- ============================================================
-- V78: 为 BusinessDoc 添加银行流水反向追溯字段
--
-- 问题: BusinessDoc 可通过 source="FROM_BANK_TXN" 知道来自银行流水,
-- 但无法追溯到具体的 BankStatement（缺少 bank_stmt_id 字段）。
--
-- 修复: 添加 bank_stmt_id FK + 回填历史数据
-- ============================================================

-- 1. 添加 bank_stmt_id 列（允许 NULL，草稿单据可能无来源）
ALTER TABLE t_business_doc ADD COLUMN IF NOT EXISTS bank_stmt_id BIGINT REFERENCES t_bank_statement(id);
COMMENT ON COLUMN t_business_doc.bank_stmt_id IS '来源银行流水ID（P38-F6: 反向追溯银行流水→单据）';
CREATE INDEX IF NOT EXISTS idx_t_business_doc_bank_stmt_id ON t_business_doc(bank_stmt_id);

-- 2. 补全历史数据：从 AutoGenerationService 生成的 BusinessDoc 追溯 bank_stmt_id
UPDATE t_business_doc d
SET bank_stmt_id = s.id
FROM t_bank_statement s
WHERE s.generated_doc_id = d.id
  AND d.bank_stmt_id IS NULL;