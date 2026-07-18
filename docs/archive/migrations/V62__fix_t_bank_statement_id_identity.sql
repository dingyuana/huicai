-- V62: 修复 t_bank_statement.id 缺 IDENTITY
-- 2026-06-27 SchemaValidator 启动检查发现
--
-- 背景: V54 注释声明 t_bank_statement 已修, 但实际未修复.
--       Entity 使用 @TableId(type = IdType.AUTO), 缺 IDENTITY 时 INSERT 会报 not-null.
--
-- 修复: ALTER 为 GENERATED ALWAYS AS IDENTITY (START WITH 1).

DO $$
DECLARE
  v_already boolean;
BEGIN
  SELECT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 't_bank_statement'
      AND column_name = 'id' AND is_identity = 'YES'
  ) INTO v_already;
  IF NOT v_already THEN
    BEGIN
      EXECUTE format('ALTER TABLE t_bank_statement ALTER COLUMN id DROP DEFAULT');
    EXCEPTION WHEN OTHERS THEN
      NULL;
    END;
    EXECUTE format('ALTER TABLE t_bank_statement ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (START WITH 1)');
  END IF;
END;
$$;
