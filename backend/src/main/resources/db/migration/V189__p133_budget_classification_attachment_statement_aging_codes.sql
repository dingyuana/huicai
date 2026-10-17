-- SPEC-P133 D-10 第 4~6 条：为 22 个写端点新增权限码
--
-- 覆盖：预算 6 / 流水分类规则 6 / 附件 3 / 客户对账单 4 / 账龄告警 3 = 22
-- ⚠️ **不含 AI 端点 6 个**：按裁定，AI 的风险是**成本与配额**而非数据越权
--    （铁律 #2「AI 输出 = 建议」），单独治理，不混入业务权限码体系。
--
-- 角色分配原则（沿用 V186/V188 的口径，**可审计的默认值，非业务终裁**）：
--   · 编制族（create/submit/adjust/generate/reorder/seed/manage/upload）
--       ← 取同模块既有页面码的角色集合
--   · **审批族（approve/confirm/send/dismiss/activate）一律不给 ACCOUNTANT**，
--     只给 ADMIN + FINANCE_MGR —— 与 V188 的 D-8 分权保持一致：
--     **编制权与审批权分离**，否则编制者可自编自批。
--   · 驳回/忽略类（dispute/dismiss）同审批族：它们是「推翻他人成果」的动作。
--
-- ⚠️ 若某码确应给更多角色，请在 SPEC-P133 §8 写明理由后再改，
--    **不要在迁移里静默扩权**。
-- ⚠️ 全部幂等；`t_role_menu` **没有 updated_at 列**，`t_menu.menu_code` 必填（实测 schema）。

-- ══════════════ ① 预算模块 ══════════════
-- 参照 subjects:manage（ADMIN/FINANCE_MGR/OPERATOR）作为编制族默认
INSERT INTO t_menu (parent_id, menu_name, menu_code, path, component, icon, sort_order,
                    menu_type, is_active, permission, created_by, created_at, updated_by, updated_at, deleted)
SELECT p.id, v.mname, v.mcode, NULL, NULL, NULL, v.sort,
       'BUTTON', true, v.perm, 1, NOW(), 1, NOW(), 0
FROM (VALUES
    ('预算管理',     'btn-budget-list',    'budget:list',     1),
    ('新建预算',     'btn-budget-create',  'budget:create',   2),
    ('提交预算',     'btn-budget-submit',  'budget:submit',   3),
    ('审批预算',     'btn-budget-approve', 'budget:approve',  4),
    ('启用预算',     'btn-budget-activate','budget:activate', 5),
    ('预算调整',     'btn-budget-adjust',  'budget:adjust',   6)
) AS v(mname, mcode, perm, sort)
CROSS JOIN (SELECT id FROM t_menu WHERE deleted = 0 AND permission = 'subjects:manage' LIMIT 1) p
WHERE p.id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM t_menu WHERE permission = v.perm);

-- ══════════════ ② 流水分类规则 ══════════════
-- 参照 bank:reconciliation:list（ACCOUNTANT/ADMIN/CASHIER/FINANCE_MGR）——
-- 规则直接驱动银行流水自动匹配，**与对账岗同权**
INSERT INTO t_menu (parent_id, menu_name, menu_code, path, component, icon, sort_order,
                    menu_type, is_active, permission, created_by, created_at, updated_by, updated_at, deleted)
SELECT p.id, v.mname, v.mcode, NULL, NULL, NULL, v.sort,
       'BUTTON', true, v.perm, 1, NOW(), 1, NOW(), 0
FROM (VALUES
    ('流水分类规则', 'btn-bank-classification-manage', 'bank:classification:manage', 1)
) AS v(mname, mcode, perm, sort)
CROSS JOIN (SELECT id FROM t_menu WHERE deleted = 0 AND permission = 'bank:reconciliation:list' LIMIT 1) p
WHERE p.id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM t_menu WHERE permission = v.perm);

-- ══════════════ ③ 附件 ══════════════
-- 参照 doc:list（ACCOUNTANT/ADMIN/FINANCE_MGR）
INSERT INTO t_menu (parent_id, menu_name, menu_code, path, component, icon, sort_order,
                    menu_type, is_active, permission, created_by, created_at, updated_by, updated_at, deleted)
SELECT p.id, '附件管理', 'btn-file-attachment-manage', NULL, NULL, NULL, 1,
       'BUTTON', true, 'file:attachment:manage', 1, NOW(), 1, NOW(), 0
FROM (SELECT id FROM t_menu WHERE deleted = 0 AND permission = 'doc:list' LIMIT 1) p
WHERE p.id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM t_menu WHERE permission = 'file:attachment:manage');

-- ══════════════ ④ 客户对账单 ══════════════
-- 参照 arap:settlement:list（ADMIN/FINANCE_MGR/OPERATOR）作为编制族默认
INSERT INTO t_menu (parent_id, menu_name, menu_code, path, component, icon, sort_order,
                    menu_type, is_active, permission, created_by, created_at, updated_by, updated_at, deleted)
SELECT p.id, v.mname, v.mcode, NULL, NULL, NULL, v.sort,
       'BUTTON', true, v.perm, 1, NOW(), 1, NOW(), 0
FROM (VALUES
    ('生成对账单',     'btn-stmt-generate', 'arap:statement:generate', 1),
    ('确认对账单',     'btn-stmt-confirm',  'arap:statement:confirm',  2),
    ('处理客户异议',   'btn-stmt-dispute',  'arap:statement:dispute',  3),
    ('发送对账单',     'btn-stmt-send',     'arap:statement:send',     4)
) AS v(mname, mcode, perm, sort)
CROSS JOIN (SELECT id FROM t_menu WHERE deleted = 0 AND permission = 'arap:settlement:list' LIMIT 1) p
WHERE p.id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM t_menu WHERE permission = v.perm);

-- ══════════════ ⑤ 账龄告警 ══════════════
INSERT INTO t_menu (parent_id, menu_name, menu_code, path, component, icon, sort_order,
                    menu_type, is_active, permission, created_by, created_at, updated_by, updated_at, deleted)
SELECT p.id, v.mname, v.mcode, NULL, NULL, NULL, v.sort,
       'BUTTON', true, v.perm, 1, NOW(), 1, NOW(), 0
FROM (VALUES
    ('生成账龄告警', 'btn-aging-generate', 'arap:aging:generate', 1),
    ('忽略账龄告警', 'btn-aging-dismiss',  'arap:aging:dismiss',  2),
    ('处理账龄告警', 'btn-aging-resolve',  'arap:aging:resolve',  3)
) AS v(mname, mcode, perm, sort)
CROSS JOIN (SELECT id FROM t_menu WHERE deleted = 0 AND permission = 'arap:settlement:list' LIMIT 1) p
WHERE p.id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM t_menu WHERE permission = v.perm);

-- ══════════════ 角色授权：编制族 3 角色 ══════════════
INSERT INTO t_role_menu (role_id, menu_id, created_by, created_at, updated_by)
SELECT r.id, m.id, 1, NOW(), 1
FROM (VALUES
    -- 预算编制族（参照 subjects:manage：ADMIN/FINANCE_MGR/OPERATOR）
    ('ADMIN',       'budget:list'),     ('FINANCE_MGR', 'budget:list'),
    ('OPERATOR',    'budget:list'),
    ('ADMIN',       'budget:create'),   ('FINANCE_MGR', 'budget:create'),
    ('OPERATOR',    'budget:create'),
    ('ADMIN',       'budget:submit'),   ('FINANCE_MGR', 'budget:submit'),
    ('OPERATOR',    'budget:submit'),
    ('ADMIN',       'budget:adjust'),   ('FINANCE_MGR', 'budget:adjust'),
    ('OPERATOR',    'budget:adjust'),
    -- 流水分类规则（参照 bank:reconciliation:list：ACCOUNTANT/ADMIN/CASHIER/FINANCE_MGR）
    ('ACCOUNTANT',  'bank:classification:manage'),
    ('ADMIN',       'bank:classification:manage'),
    ('CASHIER',     'bank:classification:manage'),
    ('FINANCE_MGR', 'bank:classification:manage'),
    -- 附件（参照 doc:list：ACCOUNTANT/ADMIN/FINANCE_MGR）
    ('ACCOUNTANT',  'file:attachment:manage'),
    ('ADMIN',       'file:attachment:manage'),
    ('FINANCE_MGR', 'file:attachment:manage'),
    -- 对账单编制族（参照 arap:settlement:list：ADMIN/FINANCE_MGR/OPERATOR）
    ('ADMIN',       'arap:statement:generate'), ('FINANCE_MGR', 'arap:statement:generate'),
    ('OPERATOR',    'arap:statement:generate'),
    -- 账龄告警生成（与编制族同）
    ('ADMIN',       'arap:aging:generate'), ('FINANCE_MGR', 'arap:aging:generate'),
    ('OPERATOR',    'arap:aging:generate')
) AS v(role_code, perm)
JOIN t_role r ON r.role_code = v.role_code AND r.deleted = 0
JOIN t_menu m ON m.permission = v.perm AND m.deleted = 0
WHERE NOT EXISTS (
    SELECT 1 FROM t_role_menu x WHERE x.role_id = r.id AND x.menu_id = m.id
);

-- ══════════════ 角色授权：审批族 2 角色（D-8 分权，刻意不给 ACCOUNTANT/OPERATOR）═══
INSERT INTO t_role_menu (role_id, menu_id, created_by, created_at, updated_by)
SELECT r.id, m.id, 1, NOW(), 1
FROM (VALUES
    ('FINANCE_MGR', 'budget:approve'),     ('ADMIN', 'budget:approve'),
    ('FINANCE_MGR', 'budget:activate'),    ('ADMIN', 'budget:activate'),
    ('FINANCE_MGR', 'arap:statement:confirm'), ('ADMIN', 'arap:statement:confirm'),
    ('FINANCE_MGR', 'arap:statement:dispute'), ('ADMIN', 'arap:statement:dispute'),
    ('FINANCE_MGR', 'arap:statement:send'),    ('ADMIN', 'arap:statement:send'),
    -- dismiss 是「忽略风险告警」，与审批同族：能忽略告警的人必须是有权处置的人
    ('FINANCE_MGR', 'arap:aging:dismiss'),  ('ADMIN', 'arap:aging:dismiss'),
    ('FINANCE_MGR', 'arap:aging:resolve'),  ('ADMIN', 'arap:aging:resolve')
) AS v(role_code, perm)
JOIN t_role r ON r.role_code = v.role_code AND r.deleted = 0
JOIN t_menu m ON m.permission = v.perm AND m.deleted = 0
WHERE NOT EXISTS (
    SELECT 1 FROM t_role_menu x WHERE x.role_id = r.id AND x.menu_id = m.id
);

DO $$
DECLARE n int; a int;
BEGIN
    SELECT count(*) INTO n FROM t_menu
    WHERE permission LIKE 'budget:%' OR permission LIKE 'arap:statement:%'
       OR permission LIKE 'arap:aging:%' OR permission LIKE 'bank:classification:%'
       OR permission = 'file:attachment:manage';
    SELECT count(*) INTO a FROM t_role_menu rm JOIN t_menu m ON m.id = rm.menu_id
    WHERE m.permission LIKE 'budget:%' OR m.permission LIKE 'arap:statement:%'
       OR m.permission LIKE 'arap:aging:%' OR m.permission LIKE 'bank:classification:%'
       OR m.permission = 'file:attachment:manage';
    RAISE NOTICE 'V189 新增权限码 % 个，授权关系 % 条', n, a;
END $$;