-- =============================================================================
-- V167: RLS 策略谓词空串硬化（REQ-2026-137 / SPC-P108）
-- =============================================================================
-- 【缺陷背景】（实测，非推演）
-- V106 建立、V156 补齐的 70 张租户表策略谓词逐字为：
--     USING (enterprise_id = current_setting('app.enterprise_id', true)::bigint)
-- 而 PostgreSQL 在「曾执行过 SET LOCAL 的事务」结束后，该 GUC 读回的是
-- **空串**而不是 NULL（全新会话才是 NULL）：
--     begin; set local app.enterprise_id='1'; commit;
--     select current_setting('app.enterprise_id', true);  --> ''
--     select count(*) from t_voucher;
--       --> ERROR: invalid input syntax for type bigint: ""
-- ⇒ 凡「无 EnterpriseContextHolder 的事务」（定时任务 / 系统初始化 / 批处理）
--   复用了刚刚处理过请求的连接池连接，查租户表会**抛 SQL 错（fail-500）**，
--   而不是返 0 行。这与 RLS 的 fail-closed 意图相反。
--   P102-M5b 把应用切到非超级用户后这条路径才真正可达（超级用户绕过 RLS，
--   根本不会执行到谓词）。
--
-- 【修法】
-- 谓词加 NULLIF：空串归 NULL ⇒ 比较结果为 NULL ⇒ 返 0 行（fail-closed 且不报错）。
--     USING (enterprise_id = NULLIF(current_setting('app.enterprise_id', true), '')::bigint)
--
-- 【范围】
--   · 只重写「已有 enterprise_policy 且谓词仍是旧形状」的表；
--     不动 12 张无 RLS 的平台表（含带 enterprise_id 的 t_user /
--     t_agency_enterprise，属既有设计选择，另议）；
--   · 不改任何 Java 代码；不改表的 ENABLE/FORCE 状态；
--   · 保持 `USING` 单表达式形状（未显式 WITH CHECK 时 PostgreSQL 自动复用
--     USING 作为 WITH CHECK，改动后跨租户写入仍被拒）。
--
-- 【幂等】
--   谓词已含 NULLIF 的策略会被跳过，故重复执行结果相同。
--
-- 【时机】
--   Flyway 在应用启动阶段执行（此时无并发读）；70 张表的
--   DROP + CREATE 实测 < 1s。
-- =============================================================================

DO $$
DECLARE
    r RECORD;
    rewritten INT := 0;
BEGIN
    FOR r IN
        SELECT c.relname
          FROM pg_class c
          JOIN pg_namespace n ON n.oid = c.relnamespace
          JOIN pg_policies p
            ON p.schemaname = n.nspname
           AND p.tablename = c.relname
         WHERE n.nspname = 'public'
           AND p.policyname = 'enterprise_policy'
           AND p.qual IS NOT NULL
           AND p.qual LIKE '%current_setting%'
           AND p.qual NOT LIKE '%NULLIF%'
    LOOP
        EXECUTE format('DROP POLICY IF EXISTS enterprise_policy ON %I', r.relname);
        EXECUTE format(
            'CREATE POLICY enterprise_policy ON %I '
            || 'USING (enterprise_id = NULLIF(current_setting(''app.enterprise_id'', true), '''')::bigint)',
            r.relname
        );
        rewritten := rewritten + 1;
    END LOOP;
    RAISE NOTICE 'V167: 已硬化 % 张表的 enterprise_policy 谓词', rewritten;
END;
$$;