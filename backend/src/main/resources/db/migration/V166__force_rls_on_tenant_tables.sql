-- ============================================================
-- P102 / REQ-2026-129 M5b：为 RLS 第三层防线补齐「表属主」这一重绕过
--
-- 实测结论（2026-10-01，开发库）：
--   ① 应用角色 huicai 同时具备 rolsuper=t 与 rolbypassrls=t；
--   ② 83/83 张表的 owner 都是 huicai，而**表属主默认也绕过 RLS**；
--   ③ 实测「只加 FORCE 而不降权」完全无效 ——
--      设 app.enterprise_id=1 与 =2 返回同样的 44 行；
--   ④ 改用 NOSUPERUSER NOBYPASSRLS 的探针角色后，同一查询 44 行 → 1 行，
--      证明策略谓词本身有效，缺的只是角色降权 + FORCE。
--
-- 因此本迁移**只做可回滚的一半**：给已启用 RLS 的 70 张表补 FORCE。
-- 角色降权（ALTER ROLE huicai NOSUPERUSER NOBYPASSRLS）**不在本迁移内**，
-- 原因见 docs/development/plans 的 M5b 操作手册：它不可逆、且一旦
-- 任何代码路径漏设 app.enterprise_id 就会导致全站查询返 0 行。
--
-- 安全性：FORCE ROW LEVEL SECURITY 本身不改变当前行为 ——
-- 在角色仍为 superuser/bypassrls/属主三者之一时，RLS 依旧被绕过。
-- 故本迁移对现有应用**零影响**，可在无风险前提下先行落地。
-- ============================================================

DO $$
DECLARE
  r record;
  v_count integer := 0;
BEGIN
  FOR r IN
    SELECT c.relname AS tbl
    FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = 'public'
      AND c.relkind = 'r'
      AND c.relrowsecurity          -- 只处理已启用 RLS 的表
      AND NOT c.relforcerowsecurity -- 幂等：已 FORCE 的跳过
  LOOP
    EXECUTE format('ALTER TABLE public.%I FORCE ROW LEVEL SECURITY', r.tbl);
    v_count := v_count + 1;
  END LOOP;
  RAISE NOTICE 'FORCE ROW LEVEL SECURITY applied to % tables', v_count;
END $$;