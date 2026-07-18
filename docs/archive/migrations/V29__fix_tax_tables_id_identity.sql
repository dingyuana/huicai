-- V29__fix_tax_tables_id_identity.sql
-- 修复 t_output_invoice / t_input_invoice 缺 IDENTITY 的问题
-- 问题: V8 建表时只用 `id BIGINT PRIMARY KEY` (无 IDENTITY, 无 DEFAULT),
--       但 Entity 用 IdType.AUTO, 导致 INSERT 报 not-null 约束错误。
-- 与 V28 完全相同的模式，V28 只修了 4 张主表，漏了这两张。
--
-- 现状:
--   t_output_invoice | 0 行 | max(id)=null
--   t_input_invoice  | 0 行 | max(id)=null
--
-- 两张表均为空表，START WITH 1 即可。
-- 用 DO 块保证幂等: 如果已经是 identity 列则跳过。

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_name = 't_output_invoice'
      AND column_name = 'id'
      AND is_identity = 'YES'
  ) THEN
    ALTER TABLE t_output_invoice
      ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (START WITH 1);
  END IF;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_name = 't_input_invoice'
      AND column_name = 'id'
      AND is_identity = 'YES'
  ) THEN
    ALTER TABLE t_input_invoice
      ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (START WITH 1);
  END IF;
END $$;
