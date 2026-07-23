-- ============================================================
-- V106: S-26 Agency 分支 — PostgreSQL RLS 策略
-- 关联架构: 多租户架构设计.md §3.3
-- 说明: 对所有业务表启用行级安全，作为第三层防线兜底
-- ============================================================

DO $$
DECLARE
    tbl TEXT;
BEGIN
    FOR tbl IN
        SELECT tablename FROM pg_tables
        WHERE schemaname = 'public'
          AND tablename LIKE 't_%'
          AND tablename NOT IN (
              't_user', 't_role', 't_user_role', 't_menu', 't_role_menu',
              't_agency', 't_enterprise', 't_agency_enterprise',
              't_sys_config', 't_audit_log', 't_dept'
          )
    LOOP
        -- 启用 RLS
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', tbl);
        -- 强制 RLS（表 owner 也受限制）
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', tbl);
        -- 创建策略：仅返回当前 enterprise_id 的数据（先删除再创建，避免重复）
        EXECUTE format('DROP POLICY IF EXISTS enterprise_policy ON %I', tbl);
        EXECUTE format(
            'CREATE POLICY enterprise_policy ON %I '
            || 'USING (enterprise_id = current_setting(''app.enterprise_id'', true)::bigint)',
            tbl
        );
    END LOOP;
END;
$$;
