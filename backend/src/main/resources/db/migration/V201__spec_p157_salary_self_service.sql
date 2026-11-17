-- ═══════════════════════════════════════════════════════════════════
-- SPEC-P157：R4 员工自助查询（REQ-2026-160 后置项，用户裁定 2026-11-09）
--   ① t_employee +user_id（员工↔登录账号绑定，一员工至多一账号）
--   ② EMPLOYEE 角色（员工自助族，纯只读）
--   ③ salary:self:view 权限码（挂 menu-salary 下 BUTTON）
-- 权限面改动已经用户裁定批准。
-- 数据可见性：员工账号登录（user_type=ENTERPRISE + t_user.enterprise_id）→
-- JWT 企业上下文 → RLS 天然隔离；单据面按 user_id 反查绑定员工过滤。
-- ═══════════════════════════════════════════════════════════════════

-- ── 1. t_employee 补绑定列 ──
ALTER TABLE t_employee ADD COLUMN IF NOT EXISTS user_id BIGINT;
COMMENT ON COLUMN t_employee.user_id IS 'SPEC-P157：绑定的登录账号（t_user.id），NULL=未绑定自助；一员工至多一账号';

-- 唯一性双向守卫：一账号至多绑一员工（同企业内部分索引可全库唯一化——user_id 全局唯一即可）
CREATE UNIQUE INDEX IF NOT EXISTS uq_employee_user
    ON t_employee (user_id) WHERE user_id IS NOT NULL AND deleted = 0;

-- ── 2. EMPLOYEE 角色（role_type 受 chk_role_type 约束：合法值 ADMIN/NORMAL/CUSTOM/…，取 NORMAL）──
INSERT INTO t_role (role_code, role_name, role_type, is_system, status, remark,
                    created_by, created_at, updated_by, updated_at, deleted)
SELECT 'EMPLOYEE', '员工自助', 'NORMAL', false, 'ACTIVE',
       'SPEC-P157 员工自助查询：仅 salary:self:view（我的工资单只读）',
       1, NOW(), 1, NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM t_role x WHERE x.role_code = 'EMPLOYEE' AND x.deleted = 0);

-- ── 3. 权限码菜单行（挂 menu-salary 下 BUTTON；页面入口由前端静态侧边栏 + 路由 meta 承载）──
INSERT INTO t_menu (parent_id, menu_name, menu_code, path, component, icon, sort_order,
                    menu_type, is_active, permission, created_by, created_at, updated_by, updated_at, deleted)
SELECT (SELECT id FROM t_menu WHERE menu_code = 'menu-salary' AND deleted = 0),
       '我的工资单', 'btn-salary-self-view', NULL, NULL, NULL, 9,
       'BUTTON', true, 'salary:self:view', 1, NOW(), 1, NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM t_menu x WHERE x.menu_code = 'btn-salary-self-view');

-- ── 4. 授权：EMPLOYEE 持有 salary:self:view（V190 VALUES 家法：permission 列口径）──
INSERT INTO t_role_menu (role_id, menu_id, created_by, created_at, updated_by)
SELECT r.id, m.id, 1, NOW(), 1
FROM (VALUES ('EMPLOYEE', 'salary:self:view')) AS v(role_code, perm)
JOIN t_role r ON r.role_code = v.role_code AND r.deleted = 0
JOIN t_menu m ON m.permission = v.perm AND m.deleted = 0
WHERE NOT EXISTS (
    SELECT 1 FROM t_role_menu x WHERE x.role_id = r.id AND x.menu_id = m.id);

-- ── 5. 自证不变量 ──
DO $$
DECLARE
    v_missing INT;
BEGIN
    -- 不变量 1：绑定列与唯一索引在位
    SELECT count(*) INTO v_missing FROM information_schema.columns
    WHERE table_name = 't_employee' AND column_name = 'user_id';
    IF v_missing = 0 THEN
        RAISE EXCEPTION 'P157 自证失败：t_employee.user_id 缺失';
    END IF;

    -- 不变量 2：EMPLOYEE 角色存在且持有 salary:self:view（授权行数≥1 防空转，V196 教训）
    IF NOT EXISTS (
        SELECT 1 FROM t_role_menu rm
        JOIN t_role r ON r.id = rm.role_id AND r.deleted = 0
        JOIN t_menu m ON m.id = rm.menu_id AND m.deleted = 0
        WHERE r.role_code = 'EMPLOYEE' AND m.permission = 'salary:self:view'
    ) THEN
        RAISE EXCEPTION 'P157 自证失败：EMPLOYEE 未持有 salary:self:view';
    END IF;

    -- 不变量 3：权限行存在（G11 前提：清单引用码必须有持有者，此处持有者已验）
    IF NOT EXISTS (
        SELECT 1 FROM t_menu WHERE permission = 'salary:self:view' AND deleted = 0
    ) THEN
        RAISE EXCEPTION 'P157 自证失败：salary:self:view 菜单行缺失';
    END IF;

    RAISE NOTICE 'P157 自证通过：user_id 列 + 唯一索引 + EMPLOYEE 角色 + salary:self:view 授权全在位';
END $$;
