-- SPEC-P144（REQ-2026-161 / roadmap P0-2 D-4）：资产变动（调整）权限码落地
--
-- 背景：AssetChangeEntity + AssetChangeMapper 建于 V94，建后零引用、零行数据 ——
--       本轮（V1.106）补全服务层与端点。新写端点权限码 asset:change:create
--       必须同步补角色授权，否则复刻「voucher:update 全库无人持有 ⇒ 端点全员 403」
--       的 V1.105 遗留（V195 前 tax:*:preview 同族教训第 3 次）。
--
-- 角色口径（V190 D-9 既定）：编制族 = 同模块页面码持有者
--   asset:card:list 真库持有者实测 = ACCOUNTANT / ADMIN / FINANCE_MGR ⇒ 同授。
-- 读端点复用 asset:card:list，无需新授权。
--
-- 🔴 列口径（V190 家法，勿再踩）：t_menu.menu_code 是 btn-* 下划线/连字符形态的
--    「菜单标识」；**authorities 加载链读的是 t_menu.permission 列**（冒号形态，
--    MenuServiceImpl#getPermissionCode）——「谁持有某权限码」一律按
--    `m.permission = 'xxx:yyy:zzz'` 查，**不是** menu_code（V196 首版查错列 ⇒
--    零授权 + DO 不变量空转，voucher:update 漂移同族）。
--
-- ⚠️ 全部幂等；`t_role_menu` 没有 updated_at 列。

-- ══════════════ ① 菜单按钮行（父：资产卡片 62，同 258-260 形态）══════════════
INSERT INTO t_menu (parent_id, menu_name, menu_code, path, component, icon, sort_order,
                    menu_type, is_active, permission, created_by, created_at, updated_by, updated_at, deleted)
SELECT 62, '资产变动调整', 'btn-asset-change-create', NULL, NULL, NULL, 4,
       'BUTTON', true, 'asset:change:create', 1, NOW(), 1, NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM t_menu x WHERE x.menu_code = 'btn-asset-change-create');

-- ══════════════ ② 角色授权：编制族（V190 VALUES 家法）══════════════
INSERT INTO t_role_menu (role_id, menu_id, created_by, created_at, updated_by)
SELECT r.id, m.id, 1, NOW(), 1
FROM (VALUES
    ('ACCOUNTANT', 'asset:change:create'),
    ('FINANCE_MGR','asset:change:create'),
    ('ADMIN',      'asset:change:create')
) AS v(role_code, perm)
JOIN t_role r ON r.role_code = v.role_code AND r.deleted = 0
JOIN t_menu m ON m.permission = v.perm AND m.deleted = 0
WHERE NOT EXISTS (
    SELECT 1 FROM t_role_menu x WHERE x.role_id = r.id AND x.menu_id = m.id);

-- ══════════════ ③ 自证不变量（V195 先例：迁移尾部 DO 块）══════════════
DO $$
DECLARE
    v_no_menu INTEGER;
    v_no_grant INTEGER;
    v_holder_missing INTEGER;
BEGIN
    -- 不变量 1：按钮行存在且 permission 列承载冒号形态权限码
    SELECT count(*) INTO v_no_menu FROM t_menu
    WHERE menu_code = 'btn-asset-change-create'
      AND permission = 'asset:change:create' AND deleted = 0;
    IF v_no_menu <> 1 THEN
        RAISE EXCEPTION 'V196: 菜单行 btn-asset-change-create 缺失或 permission 列口径错误（count=%）', v_no_menu;
    END IF;

    -- 不变量 2：本迁移必须至少落 1 条授权行（防 VALUES 笔误/JOIN 失配静默 no-op）
    SELECT count(*) INTO v_no_grant
    FROM t_role_menu rm
    JOIN t_menu m ON m.id = rm.menu_id
    WHERE m.permission = 'asset:change:create';
    IF v_no_grant < 1 THEN
        RAISE EXCEPTION 'V196: asset:change:create 零授权行（VALUES/JOIN 失配）';
    END IF;

    -- 不变量 3：持有 asset:card:list 的角色必须全部持有 asset:change:create
    --          （授页面必授动作 —— 按 permission 列口径推导持有者）
    SELECT count(*) INTO v_holder_missing
    FROM t_role r
    WHERE EXISTS (SELECT 1 FROM t_role_menu rm2 JOIN t_menu m2 ON m2.id = rm2.menu_id
                  WHERE rm2.role_id = r.id AND m2.permission = 'asset:card:list')
      AND NOT EXISTS (SELECT 1 FROM t_role_menu rm3 JOIN t_menu m3 ON m3.id = rm3.menu_id
                  WHERE rm3.role_id = r.id AND m3.permission = 'asset:change:create');
    IF v_holder_missing > 0 THEN
        RAISE EXCEPTION 'V196: % 个持有 asset:card:list 的角色未获得 asset:change:create 授权', v_holder_missing;
    END IF;

    RAISE NOTICE 'V196: asset:change:create 菜单行 + 编制族授权 + 自证全部通过';
END $$;
