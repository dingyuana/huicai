-- V61: 修复 t_business_doc_entry.id 缺 IDENTITY
-- 2026-06-27 P31 销售发票审核(confirm)时抛出 null value in column "id"
--
-- 背景: V5 建表时 id 为 bigint NOT NULL, 后续 V28/V54 批量修复遗漏了此表.
--       导致 confirm(审核通过) 内 createBusinessDocFromInvoice 调用
--       docEntryMapper.insert(entry) 失败 (Entity 用 IdType.AUTO 期望自增).
--       外层事务回滚, 前端收到"系统繁忙，请稍后重试".
--
-- 修复: ALTER 为 GENERATED ALWAYS AS IDENTITY.
--       表目前无历史数据, START WITH 1 即可.

DO $$
DECLARE
  v_already boolean;
BEGIN
  SELECT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 't_business_doc_entry'
      AND column_name = 'id' AND is_identity = 'YES'
  ) INTO v_already;
  IF NOT v_already THEN
    BEGIN
      EXECUTE format('ALTER TABLE t_business_doc_entry ALTER COLUMN id DROP DEFAULT');
    EXCEPTION WHEN OTHERS THEN
      NULL;
    END;
    EXECUTE format('ALTER TABLE t_business_doc_entry ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (START WITH 1)');
  END IF;
END;
$$;
