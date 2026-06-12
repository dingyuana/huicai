-- ============================================================
-- V13: 添加部门管理和操作日志菜单权限
-- ============================================================

-- 部门管理
INSERT INTO t_menu (id, name, permission_code, type, parent_id, path, component, icon, sort_order) VALUES
(90, '部门管理', 'system:dept:list', 'menu', 1, '/system/dept', 'system/dept/DeptList', 'OfficeBuilding', 4)
ON CONFLICT (id) DO NOTHING;

INSERT INTO t_menu (id, name, permission_code, type, parent_id, sort_order) VALUES
(91, '查询部门', 'system:dept:query', 'button', 90, 1),
(92, '新增部门', 'system:dept:create', 'button', 90, 2),
(93, '修改部门', 'system:dept:update', 'button', 90, 3),
(94, '删除部门', 'system:dept:delete', 'button', 90, 4)
ON CONFLICT (id) DO NOTHING;

-- 操作日志
INSERT INTO t_menu (id, name, permission_code, type, parent_id, path, component, icon, sort_order) VALUES
(95, '操作日志', 'system:audit:list', 'menu', 1, '/system/audit-log', 'system/audit-log/AuditLogList', 'List', 5)
ON CONFLICT (id) DO NOTHING;

INSERT INTO t_menu (id, name, permission_code, type, parent_id, sort_order) VALUES
(96, '查询日志', 'system:audit:query', 'button', 95, 1)
ON CONFLICT (id) DO NOTHING;

-- 关联超级管理员角色到所有新增菜单
INSERT INTO t_role_menu (id, role_id, menu_id)
SELECT id, 1, id FROM t_menu WHERE id IN (90,91,92,93,94,95,96)
ON CONFLICT (id) DO NOTHING;
