-- ============================================================
-- V110: 补充系统管理菜单 + 授权 admin 角色
-- 目的: 系统管理模块对所有企业用户可见，补充用户管理菜单并授权
-- ============================================================

-- 1. 恢复被逻辑删除的系统管理菜单
UPDATE t_menu SET deleted = 0 WHERE permission IN ('system:role:list', 'system:menu:list', 'system:dept:list', 'system:audit:list') AND deleted = 1;

-- 2. 添加用户管理菜单（如果不存在，使用高 ID 避免与 V96 冲突）
INSERT INTO t_menu (id, parent_id, menu_name, menu_code, permission, menu_type, path, sort_order, deleted)
OVERRIDING SYSTEM VALUE
SELECT 200, 1, '用户管理', 'system_user', 'system:user:list', 'MENU', '/system/user', 0, 0
WHERE NOT EXISTS (SELECT 1 FROM t_menu WHERE permission = 'system:user:list');

-- 3. 给 admin 角色（role_id=1）授权所有系统管理菜单
INSERT INTO t_role_menu (role_id, menu_id)
SELECT 1, m.id FROM t_menu m
WHERE m.permission IN ('system:user:list', 'system:role:list', 'system:menu:list', 'system:dept:list', 'system:audit:list')
  AND NOT EXISTS (SELECT 1 FROM t_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = m.id);
