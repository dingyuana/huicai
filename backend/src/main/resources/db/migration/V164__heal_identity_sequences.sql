-- P102 / REQ-2026-129 前置修复：治愈全库 identity 序列落后
--
-- 缺陷现象：POST /api/v1/subjects/import-standard 100% 返回 500，
--   ERROR: duplicate key value violates unique constraint "t_subject_pkey"
--
-- 根因：种子 migration 普遍用**显式 id** 插入基础数据却从不调用 nextval，
--   故 identity 序列仍停在初始值。实测落后 5 张表：
--     t_menu(seq=1,max=200) / t_role(1,5) / t_subject(64,102)
--     t_sys_config(1,5) / t_voucher_template(1,18)
--   任何依赖数据库自增 id 的插入都会取到已被种子占用的 id → 必然撞码。
--
-- 为何长期未被发现：测试侧 AbstractMapperTest#alignIdentitySequencesOnce
--   已在每个 JVM 启动时做同样的 setval，**恰好把生产缺陷掩盖了**。
--   本迁移把该修复下沉到 schema 层，使新建环境（CI 容器 / 新部署）同样受益。
--
-- 安全性：setval 只推进序列、不触碰任何数据行，最坏情况是 id 出现空洞（无害）。

DO $$
DECLARE
  r record;
  v_fixed integer := 0;
BEGIN
  FOR r IN
    SELECT s.relname AS seq, t.relname AS tbl
    FROM pg_class s
    JOIN pg_namespace n ON n.oid = s.relnamespace
    JOIN pg_depend d ON d.objid = s.oid AND d.deptype = 'i'
    JOIN pg_class t ON t.oid = d.refobjid
    JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum = d.refobjsubid
    WHERE n.nspname = 'public' AND a.attname = 'id' AND s.relkind = 'S'
  LOOP
    EXECUTE format(
      'SELECT setval(%L, GREATEST((SELECT COALESCE(MAX(id), 1) FROM %I), 1))', r.seq, r.tbl);
    v_fixed := v_fixed + 1;
  END LOOP;
  RAISE NOTICE 'aligned % identity sequences', v_fixed;
END $$;
