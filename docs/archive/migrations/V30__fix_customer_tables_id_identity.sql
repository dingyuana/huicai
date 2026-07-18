-- V30__fix_customer_tables_id_identity.sql
-- 修复 t_customer / t_vendor 缺 IDENTITY 的问题
-- 与 V28 完全相同的模式，这两张表在 V7 建表时只用 `id BIGINT PRIMARY KEY`。
-- 发票导入时会自动创建客户，没有 IDENTITY 导致 INSERT 失败。
--
-- 现状 (2026-06-15, max id 查询):
--   t_customer | max(id)=2065637566362628097 (有历史 snowflake 数据)
--   t_vendor   | max(id)=4
--
-- t_customer 的 START WITH = max(id)+1 避免与历史 ID 冲突。
-- 用 DO 块保证幂等: 如果已经是 identity 列则跳过。

DO $$
DECLARE
  max_id bigint;
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_name = 't_customer'
      AND column_name = 'id'
      AND is_identity = 'YES'
  ) THEN
    SELECT COALESCE(MAX(id), 0) + 1 INTO max_id FROM t_customer;
    EXECUTE 'ALTER TABLE t_customer ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (START WITH ' || max_id || ')';
  END IF;
END $$;

DO $$
DECLARE
  max_id bigint;
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_name = 't_vendor'
      AND column_name = 'id'
      AND is_identity = 'YES'
  ) THEN
    SELECT COALESCE(MAX(id), 0) + 1 INTO max_id FROM t_vendor;
    EXECUTE 'ALTER TABLE t_vendor ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (START WITH ' || max_id || ')';
  END IF;
END $$;
