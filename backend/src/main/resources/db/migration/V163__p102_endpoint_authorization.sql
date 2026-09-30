-- ============================================================
-- P102 / REQ-2026-129：清库与反结账端点鉴权所需的权限码
--
-- 实施前核查结论（2026-09-30 实测，修正 SPEC 原有描述）：
--   1. 权限列名为 t_menu.permission（**不是** permission_code），
--      MenuEntity 已正确映射 @TableField("permission")，且 43/43 行已回填；
--      故 SPEC 中「当前无此列、V160 补列并回填」不成立，**无需加列**。
--   2. 真正缺的是 system:clear / period:reopen 两个权限码本身。
--   3. 全库 @PreAuthorize 为 0 处；@EnableMethodSecurity 已在
--      SecurityConfig:24 开启，故补注解即真实生效。
--
-- 写法沿用 V110 的既有约定：t_menu 为 GENERATED ALWAYS AS IDENTITY，
-- 显式插 id 必须 OVERRIDING SYSTEM VALUE；t_role_menu 则交由序列分配。
-- ============================================================

-- 1. 清库操作权限码（parent = 用户管理菜单 200）
INSERT INTO t_menu (id, parent_id, menu_name, menu_code, permission, menu_type, sort_order, deleted)
OVERRIDING SYSTEM VALUE
SELECT 201, 200, '清库操作', 'system_clear', 'system:clear', 'BUTTON', 900, 0
WHERE NOT EXISTS (SELECT 1 FROM t_menu WHERE permission = 'system:clear');

-- 2. 反结账权限码（parent = 期末结账菜单 34）
INSERT INTO t_menu (id, parent_id, menu_name, menu_code, permission, menu_type, sort_order, deleted)
OVERRIDING SYSTEM VALUE
SELECT 202, 34, '反结账', 'period_reopen', 'period:reopen', 'BUTTON', 901, 0
WHERE NOT EXISTS (SELECT 1 FROM t_menu WHERE permission = 'period:reopen');

-- 3. 角色授权：system:clear 仅 ADMIN；period:reopen 给 ADMIN 与 FINANCE_MGR
INSERT INTO t_role_menu (role_id, menu_id)
SELECT g.role_id, m.id
FROM t_menu m
JOIN (VALUES
        (1, 'system:clear'),
        (1, 'period:reopen'),
        (2, 'period:reopen')
     ) AS g(role_id, permission) ON g.permission = m.permission
WHERE NOT EXISTS (
    SELECT 1 FROM t_role_menu rm WHERE rm.role_id = g.role_id AND rm.menu_id = m.id
);
