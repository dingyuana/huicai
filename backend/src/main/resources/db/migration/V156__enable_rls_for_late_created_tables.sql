-- =============================================================================
-- V156: 补齐 4 张租户表的行级安全（REQ-2026-107 / SPC-P99）
-- =============================================================================
-- 【缺陷背景】
-- V106__enable_rls_policies.sql 用一个 DO 块遍历「当时已存在」的 t_% 表并批量
-- ENABLE + FORCE ROW LEVEL SECURITY + CREATE POLICY。下列 4 张表由更晚的
-- 版本创建，V106 执行时尚不存在，因此未纳入，出现租户隔离缺口：
--     t_contract              (V107__create_contract_table.sql)
--     t_agency_user_enterprise(V112__create_agency_user_tables.sql)
--     t_service_progress      (V148__t_service_progress.sql)
--     t_close_log             (V151__create_close_log.sql)
-- 四者均含 enterprise_id 列，属**漏网**而非 V106 的有意排除
-- （V106 明确排除的 11 张表为 t_user / t_role / t_user_role / t_menu /
--   t_role_menu / t_agency / t_enterprise / t_agency_enterprise /
--   t_sys_config / t_audit_log / t_dept，本次不动它们）。
--
-- 实测：迁移完成后 83 张表中仅 66 张 relrowsecurity/relforcerowsecurity 为 true。
--
-- 【为什么必须 FORCE】
-- 应用连接用户 huicai 同时是这几张表的 owner。若只 ENABLE 不 FORCE，
-- PostgreSQL 会让 owner 绕过策略，隔离形同虚设。既有 66 张表已经是
-- ENABLE + FORCE 双重状态，本迁移与之保持一致。
--
-- 【策略谓词】
-- 逐字复用 V106 的原文，不另造口径：
--     USING (enterprise_id = current_setting('app.enterprise_id', true)::bigint)
-- current_setting(..., true) 在 GUC 未设置时返回 NULL，此时比较结果为 NULL
-- 即「不返回任何行」——这是既有 66 张表的既有行为，非本次引入。
--
-- 【幂等】
-- 与 V106 一致：先 DROP POLICY IF EXISTS 再 CREATE，重复执行结果相同。
-- =============================================================================

DO $$
DECLARE
    tbl TEXT;
BEGIN
    FOREACH tbl IN ARRAY ARRAY[
        't_contract',
        't_agency_user_enterprise',
        't_service_progress',
        't_close_log'
    ]
    LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', tbl);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', tbl);
        EXECUTE format('DROP POLICY IF EXISTS enterprise_policy ON %I', tbl);
        EXECUTE format(
            'CREATE POLICY enterprise_policy ON %I '
            || 'USING (enterprise_id = current_setting(''app.enterprise_id'', true)::bigint)',
            tbl
        );
    END LOOP;
END;
$$;
