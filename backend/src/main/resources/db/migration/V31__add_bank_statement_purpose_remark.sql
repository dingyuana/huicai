-- 银行流水增加用途和附言字段, 用于分类匹配 (4 个字段联合判断)
-- 2026-06-15

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name='t_bank_statement' AND column_name='purpose') THEN
        ALTER TABLE t_bank_statement ADD COLUMN purpose VARCHAR(500);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name='t_bank_statement' AND column_name='transaction_remark') THEN
        ALTER TABLE t_bank_statement ADD COLUMN transaction_remark VARCHAR(500);
    END IF;
END $$;
