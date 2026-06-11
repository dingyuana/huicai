-- ============================================================
-- V3: M2 基础数据管理 — 菜单种子数据
-- ============================================================

-- 一级菜单: 基础数据
INSERT INTO t_menu (id, name, permission_code, type, parent_id, path, component, icon, sort_order) VALUES
(2, '基础数据', NULL, 'menu', NULL, '/basis', 'Layout', 'DataBoard', 2)
ON CONFLICT (id) DO NOTHING;

-- 二级菜单: 科目管理
INSERT INTO t_menu (id, name, permission_code, type, parent_id, path, component, icon, sort_order) VALUES
(40, '科目管理', 'subjects:manage', 'menu', 2, '/basis/subject', 'system/subject/SubjectList', 'List', 1)
ON CONFLICT (id) DO NOTHING;

-- 科目管理按钮权限
INSERT INTO t_menu (id, name, permission_code, type, parent_id, sort_order) VALUES
(401, '查询科目', 'subjects:query', 'button', 40, 1),
(402, '新增科目', 'subjects:create', 'button', 40, 2),
(403, '修改科目', 'subjects:update', 'button', 40, 3),
(404, '删除科目', 'subjects:delete', 'button', 40, 4)
ON CONFLICT (id) DO NOTHING;

-- 二级菜单: 会计期间
INSERT INTO t_menu (id, name, permission_code, type, parent_id, path, component, icon, sort_order) VALUES
(50, '会计期间', 'periods:manage', 'menu', 2, '/basis/period', 'system/period/PeriodList', 'Calendar', 2)
ON CONFLICT (id) DO NOTHING;

-- 会计期间按钮权限
INSERT INTO t_menu (id, name, permission_code, type, parent_id, sort_order) VALUES
(501, '查询期间', 'periods:query', 'button', 50, 1),
(502, '新增期间', 'periods:create', 'button', 50, 2),
(503, '修改期间', 'periods:update', 'button', 50, 3),
(504, '删除期间', 'periods:delete', 'button', 50, 4)
ON CONFLICT (id) DO NOTHING;

-- 二级菜单: 凭证类型
INSERT INTO t_menu (id, name, permission_code, type, parent_id, path, component, icon, sort_order) VALUES
(60, '凭证类型', 'voucher:type:list', 'menu', 2, '/basis/voucher-type', 'system/voucher-type/VoucherTypeList', 'DocumentCopy', 3)
ON CONFLICT (id) DO NOTHING;

-- 凭证类型按钮权限
INSERT INTO t_menu (id, name, permission_code, type, parent_id, sort_order) VALUES
(601, '查询凭证类型', 'voucher:type:query', 'button', 60, 1),
(602, '新增凭证类型', 'voucher:type:create', 'button', 60, 2),
(603, '修改凭证类型', 'voucher:type:update', 'button', 60, 3),
(604, '删除凭证类型', 'voucher:type:delete', 'button', 60, 4)
ON CONFLICT (id) DO NOTHING;

-- 二级菜单: 常用摘要
INSERT INTO t_menu (id, name, permission_code, type, parent_id, path, component, icon, sort_order) VALUES
(70, '常用摘要', 'summary:lib:list', 'menu', 2, '/basis/summary-lib', 'system/summary-lib/SummaryLibList', 'Notebook', 4)
ON CONFLICT (id) DO NOTHING;

-- 常用摘要按钮权限
INSERT INTO t_menu (id, name, permission_code, type, parent_id, sort_order) VALUES
(701, '查询摘要', 'summary:lib:query', 'button', 70, 1),
(702, '新增摘要', 'summary:lib:create', 'button', 70, 2),
(703, '修改摘要', 'summary:lib:update', 'button', 70, 3),
(704, '删除摘要', 'summary:lib:delete', 'button', 70, 4)
ON CONFLICT (id) DO NOTHING;

-- 二级菜单: 系统参数
INSERT INTO t_menu (id, name, permission_code, type, parent_id, path, component, icon, sort_order) VALUES
(80, '系统参数', 'sys:config:list', 'menu', 2, '/basis/config', 'system/config/SysConfigList', 'Setting', 5)
ON CONFLICT (id) DO NOTHING;

-- 系统参数按钮权限
INSERT INTO t_menu (id, name, permission_code, type, parent_id, sort_order) VALUES
(801, '查询参数', 'sys:config:query', 'button', 80, 1),
(802, '新增参数', 'sys:config:create', 'button', 80, 2),
(803, '修改参数', 'sys:config:update', 'button', 80, 3),
(804, '删除参数', 'sys:config:delete', 'button', 80, 4)
ON CONFLICT (id) DO NOTHING;

-- 关联超级管理员角色到所有新增菜单
INSERT INTO t_role_menu (id, role_id, menu_id)
SELECT id, 1, id FROM t_menu WHERE id IN (2,40,50,60,70,80,401,402,403,404,501,502,503,504,601,602,603,604,701,702,703,704,801,802,803,804)
ON CONFLICT (id) DO NOTHING;
