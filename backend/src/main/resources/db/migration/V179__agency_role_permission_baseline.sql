-- =============================================================================
-- V179: 为代理端用户绑定平台角色（用户↔角色可开页面的权限）
-- 关联：REQ-2026-144 / SPEC-P121
-- =============================================================================
-- 【缺陷：代理端用户点不动任何菜单】
--
-- 实测三层断裂（2026-10-12）：
--   ① 侧边栏 58 个菜单项全部无条件渲染，只按 userType 分组；
--   ② 路由守卫用 meta.permission 判定（finance/voucher => 'voucher:list'）；
--   ③ 前端 hasPermission() 查后端返回的 permissions，
--      而 permissions 由 t_user_role -> t_role_menu 汇总。
--   ⇒ 5 个代理端用户的 t_user_role 全为 0 行 ⇒ permissions = []
--      ⇒ hasPermission() 恒 false ⇒ 点任何菜单都被守卫送进 /403，
--         用户感知为「点了没反应」。
--
-- 【为什么是「执行缺失」而非「设计缺失」】
-- t_role 里的 ACCOUNTANT 角色**早已配好 24 个菜单权限**（is_system=true），
-- 覆盖凭证/账簿/报表/资产 —— 设计侧本就有，只是没有任何绑定把它接到人身上。
-- 因此本迁移只补绑定，不改角色定义。
--
-- 【口径：独立 + 一次性（老丁裁定）】
--   agency_role（t_agency_user）：「在代理公司里是什么职位」
--   t_user_role（平台角色）      ：「能打开哪些页面」
-- 两者**正交**，不级联同步 ——
--   ① 级联会让改 agency_role 时自动改 t_user_role，
--      等于引入**第二处角色写入方**，正是 P115 刚消除的分叉；
--   ② 「后期可调整」不需新增代码：管理员在既有 /system/user 增删角色、
--      在既有 /system/role 配菜单，两处都已存在。
--
-- 【映射表（一次性数据修复，不进业务代码）】
--   ACCOUNTANT  -> ACCOUNTANT (24 菜单) 设计侧已配好，直接对接
--   REVIEWER    -> FINANCE_MGR (42 菜单) 审核需跨账套复核视野
--   ASSISTANT   -> CASHIER     (8 菜单)  助理做票据/银行辅助
--   AGENCY_ADMIN-> ADMIN       (44 菜单) 经理需全量
--
-- ⚠️ 映射刻意【只写入不删除】：管理员后续手工调整过的绑定不会被本迁移覆盖，
--    因为 NOT EXISTS 只补缺失项。
-- =============================================================================

DO $$
DECLARE
    -- 找不到目标平台角色时直接跳过并告警，不静默跳过
    v_missing text;
BEGIN
    SELECT string_agg(DISTINCT m.role_code, ', ') INTO v_missing
    FROM (VALUES ('ACCOUNTANT'), ('FINANCE_MGR'), ('CASHIER'), ('ADMIN')) AS m(role_code)
    WHERE NOT EXISTS (SELECT 1 FROM t_role r
                      WHERE r.role_code = m.role_code AND r.deleted = 0);

    IF v_missing IS NOT NULL THEN
        RAISE WARNING 'V179: 以下平台角色不存在，已跳过对应绑定: %', v_missing;
    END IF;

    -- 幂等：仅补缺失的 (user_id, role_id) 绑定
    INSERT INTO t_user_role (user_id, role_id)
    SELECT DISTINCT
           u.id,
           r.id
    FROM t_user            u
    JOIN t_agency_user     au ON au.user_id = u.id AND au.deleted = 0
    JOIN t_role            r  ON r.role_code = CASE au.agency_role
                                                WHEN 'ACCOUNTANT'  THEN 'ACCOUNTANT'
                                                WHEN 'REVIEWER'    THEN 'FINANCE_MGR'
                                                WHEN 'ASSISTANT'   THEN 'CASHIER'
                                                WHEN 'AGENCY_ADMIN'THEN 'ADMIN'
                                            END
                           AND r.deleted = 0
    WHERE u.user_type = 'AGENCY'
      AND u.deleted = 0
      AND au.status = 'ACTIVE'
      AND NOT EXISTS (
            SELECT 1 FROM t_user_role ur
            WHERE ur.user_id = u.id AND ur.role_id = r.id
      );

    RAISE NOTICE 'V179: 新增代理端用户↔平台角色绑定 % 条',
        (SELECT count(*) FROM t_user_role);
END $$;