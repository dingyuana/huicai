-- ============================================================
-- V97: 角色权限迁移 — 新增4个业务角色
-- ============================================================
-- 新增角色: FINANCE_MGR (财务主管), ACCOUNTANT (会计),
--           CASHIER (出纳), OPERATOR (业务员)
-- ============================================================

-- 0. 扩展 role_type 检查约束，允许新角色类型
ALTER TABLE t_role DROP CONSTRAINT IF EXISTS chk_role_type;
ALTER TABLE t_role ADD CONSTRAINT chk_role_type CHECK (
    role_type IN ('ADMIN', 'NORMAL', 'CUSTOM', 'FINANCE_MGR', 'ACCOUNTANT', 'CASHIER', 'OPERATOR')
);

-- 1. 新增业务角色
INSERT INTO t_role (role_code, role_name, role_type, is_system, status, sort_order, description)
SELECT * FROM (VALUES
    ('FINANCE_MGR', '财务主管', 'FINANCE_MGR', true, 'ACTIVE', 2, '财务主管：审核、结账、报表全权限'),
    ('ACCOUNTANT',  '会计',     'ACCOUNTANT',  true, 'ACTIVE', 3, '会计：凭证录入、账簿查询、资产管理'),
    ('CASHIER',     '出纳',     'CASHIER',     true, 'ACTIVE', 4, '出纳：银行日记账、现金日记账、票据管理'),
    ('OPERATOR',    '业务员',   'OPERATOR',    true, 'ACTIVE', 5, '业务员：业务单据录入、发票导入')
) AS v
WHERE NOT EXISTS (SELECT 1 FROM t_role WHERE role_code = v.column1);

-- 2. 为每个角色分配菜单权限
-- 菜单 ID 参考 V96 结构:
--   DIR: 1(首页), 2(基础数据), 3(财务核心), 10(业务单据), 21(税务发票), 31(固定资产), 41(报表中心)
--   MENU: 22-27(基础数据), 32-36(财务核心), 42-49(业务单据), 51-53(税务发票), 61-65(固定资产), 71-74(报表中心)

-- 财务主管 (FINANCE_MGR) — 全部菜单
INSERT INTO t_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM t_role r, t_menu m
WHERE r.role_code = 'FINANCE_MGR'
  AND m.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM t_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id)
  AND m.menu_type IN ('DIR', 'MENU');

-- 会计 (ACCOUNTANT) — 基础数据/财务核心/固定资产/报表中心
INSERT INTO t_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM t_role r, t_menu m
WHERE r.role_code = 'ACCOUNTANT'
  AND m.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM t_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id)
  AND (
    m.id IN (2, 22, 23, 24, 25, 26, 27)  -- 基础数据
    OR m.id IN (3, 32, 33, 34, 35, 36)     -- 财务核心
    OR m.id IN (31, 61, 62, 63, 64, 65)    -- 固定资产
    OR m.id IN (41, 71, 72, 73, 74)         -- 报表中心
    OR m.id = 1                              -- 首页
  );

-- 出纳 (CASHIER) — 首页/业务单据(银行/现金/票据)
INSERT INTO t_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM t_role r, t_menu m
WHERE r.role_code = 'CASHIER'
  AND m.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM t_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id)
  AND (
    m.id = 1                                 -- 首页
    OR m.id IN (10, 43, 44, 45, 46, 47)      -- 业务单据(银行/现金/票据)
    OR m.id = 3                               -- 财务核心(凭证查看)
  );

-- 业务员 (OPERATOR) — 首页/基础数据/业务单据/税务发票
INSERT INTO t_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM t_role r, t_menu m
WHERE r.role_code = 'OPERATOR'
  AND m.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM t_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id)
  AND (
    m.id = 1                                 -- 首页
    OR m.id IN (2, 22, 23, 24, 25)           -- 基础数据(部分)
    OR m.id IN (10, 42, 43, 48, 49)          -- 业务单据(业务单据/银行/核销/报销)
    OR m.id IN (21, 51, 52)                   -- 税务发票
  );