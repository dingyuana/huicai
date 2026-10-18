-- SPEC-P131：消除其余 10 个幽灵权限码
--
-- 背景：`RoutePermissionGhostCodeRealDBTest` 全量比对「前端路由 meta.permission」
--       与 t_menu，发现 11 个幽灵码；其中 voucher:update 已由 V185 修掉，本迁移处理其余 10 个。
--
-- 幽灵码 ⇒ 无菜单行 ⇒ 无角色菜单关联 ⇒ hasPermission 恒 false
--        ⇒ 该页面对**所有非超管角色（含 ADMIN）永久 403**，且症状形似正常权限不足。
--
-- 角色分配原则（不猜业务意图，取「同页面/同模块」既有权限码的角色集合作为默认值）：
--   · system:role/menu/dept/audit:list  ← 继承 system:user:list（系统管理同组，ADMIN）
--   · arap:expense:create               ← 继承 arap:expense:list（费用报销同模块）
--   · arap:settlement:list              ← 继承 arap:reconciliation:workbench（往来核销同模块）
-- ⚠️ 该原则是**可审计的默认**，不是业务裁定：若某码确应给更多角色，
--    应在 SPEC-P131 §7 写明理由后再改，而不是在迁移里静默扩权。
--
-- ⚠️ 全部幂等：t_menu 已知存在重名记录（AGENTS §4.5），补数据必须自带幂等。

-- ① 四个系统管理页面菜单（此前连菜单行都没有 ⇒ 侧边栏对所有人都不显示）
INSERT INTO t_menu (parent_id, menu_name, menu_code, path, component, icon, sort_order,
                    menu_type, is_active, permission, created_by, created_at, updated_by, updated_at, deleted)
SELECT p.id, v.mname, v.mcode, v.mpath, NULL, NULL, v.sort,
       'MENU', true, v.perm, 1, NOW(), 1, NOW(), 0
FROM (VALUES
    ('用户管理', 'menu-system-user',     '/system/user',      'system:user:list', 1),
    ('角色管理', 'menu-system-role',     '/system/role',      'system:role:list', 2),
    ('菜单管理', 'menu-system-menu',     '/system/menu',      'system:menu:list', 3),
    ('部门管理', 'menu-system-dept',     '/system/dept',      'system:dept:list', 4),
    ('审计日志', 'menu-system-audit-log','/system/audit-log', 'system:audit:list',5)
) AS v(mname, mcode, mpath, perm, sort)
CROSS JOIN LATERAL (
    SELECT id FROM t_menu
    WHERE deleted = 0 AND permission = 'system:user:list'
    LIMIT 1
) p
WHERE NOT EXISTS (SELECT 1 FROM t_menu m WHERE m.deleted = 0 AND m.permission = v.perm);

-- ② arap:settlement:list —— 作为 MENU 权限码挂在往来核销菜单下
INSERT INTO t_menu (parent_id, menu_name, menu_code, path, component, icon, sort_order,
                    menu_type, is_active, permission, created_by, created_at, updated_by, updated_at, deleted)
SELECT p.id, '核销往来', 'menu-arap-reconciliation', '/arap/reconciliation', NULL, NULL, 1,
       'MENU', true, 'arap:settlement:list', 1, NOW(), 1, NOW(), 0
FROM (SELECT id FROM t_menu WHERE deleted = 0 AND permission = 'arap:reconciliation:workbench' LIMIT 1) p
WHERE NOT EXISTS (SELECT 1 FROM t_menu m WHERE m.deleted = 0 AND m.permission = 'arap:settlement:list');

-- ③ arap:expense:create —— BUTTON 挂在费用报销菜单下（动作权限，非页面）
INSERT INTO t_menu (parent_id, menu_name, menu_code, path, component, icon, sort_order,
                    menu_type, is_active, permission, created_by, created_at, updated_by, updated_at, deleted)
SELECT p.id, '编辑报销单', 'arap_expense_create', NULL, NULL, NULL, 2,
       'BUTTON', true, 'arap:expense:create', 1, NOW(), 1, NOW(), 0
FROM (SELECT id FROM t_menu WHERE deleted = 0 AND permission = 'arap:expense:list' LIMIT 1) p
WHERE NOT EXISTS (SELECT 1 FROM t_menu m WHERE m.deleted = 0 AND m.permission = 'arap:expense:create');

-- ④ 按「继承同组既有权限码的角色集合」授权
INSERT INTO t_role_menu (role_id, menu_id, created_at, created_by)
SELECT src_rm.role_id, tgt.id, NOW(), 1
FROM (VALUES
    ('system:user:list',            'system:role:list'),
    ('system:user:list',            'system:menu:list'),
    ('system:user:list',            'system:dept:list'),
    ('system:user:list',            'system:audit:list'),
    ('arap:expense:list',           'arap:expense:create'),
    ('arap:reconciliation:workbench','arap:settlement:list')
) AS v(src_perm, tgt_perm)
JOIN t_menu src ON src.deleted = 0 AND src.permission = v.src_perm
JOIN t_role_menu src_rm ON src_rm.menu_id = src.id
JOIN t_menu tgt ON tgt.deleted = 0 AND tgt.permission = v.tgt_perm
WHERE NOT EXISTS (
    SELECT 1 FROM t_role_menu ex
    WHERE ex.role_id = src_rm.role_id AND ex.menu_id = tgt.id
);