-- SPEC-P133 D-7/D-8/D-9：新增凭证动作级权限码（8 个）
--
-- 发现方式：批次 3 模块 2 登记总账/凭证端点时撞上 ——
--          t_menu 里凭证类只有 voucher:list / voucher:update / voucher:type:list，
--          **没有**任何动作码，而审核/过账/红冲属人审铁律 #1 与铁律 #3。
--
-- 🔴 为什么不能拿 voucher:list 顶上（SPEC §8.4 的核心判据）：
--    warn-only 下登记它毫无拦截效果，但批次 4 切 fail-closed 那一刻
--    它就是「能看凭证就能审核凭证」的静默漏洞，而清单/G1/G2/G3 **全绿**。
--    **权限码的语义错配属于「不会自己暴露的缺陷」—— 它不会让守卫变红，
--    只在守卫全绿时悄悄放行越权。**
--
-- 角色分配（老丁 2026-10-16 裁定 D-7/D-8/D-9）：
--   D-7 新增动作码，角色分配属业务裁定 —— 本迁移给出**可审计的默认值**：
--        制单族（create/submit）= voucher:list 的持有者 ACCOUNTANT/ADMIN/FINANCE_MGR
--   D-8 🔴 审核与过账**必须分权**：制单人不能自审自过（铁律 #1）。
--        故 audit / reject / post / unpost / close / reverse **一律不给 ACCOUNTANT**，
--        只给 ADMIN + FINANCE_MGR。
--        ⚠️ 这是默认值而非业务终裁：若会计岗确实需要审核权，请在 SPEC-P133 §8
--           写明理由后再改，不要在迁移里静默扩权。
--   D-9 reverse（红冲）**独立成码**，不与普通修改同权 —— 最高危的反记账操作。
--
-- ⚠️ 全部幂等：t_menu 已知存在重名记录（AGENTS §4.5 第 12 条），补数据必须自带幂等。

-- ① 菜单行：挂在「凭证管理」（permission='voucher:list'）之下，作为 BUTTON
INSERT INTO t_menu (parent_id, menu_name, menu_code, path, component, icon, sort_order,
                    menu_type, is_active, permission, created_by, created_at, updated_by, updated_at, deleted)
SELECT p.id, v.mname, v.mcode, NULL, NULL, NULL, v.sort,
       'BUTTON', true, v.perm, 1, NOW(), 1, NOW(), 0
FROM (VALUES
    ('新增凭证',   'btn-voucher-create',  'voucher:create',  1),
    ('提交凭证',   'btn-voucher-submit',  'voucher:submit',  2),
    ('审核凭证',   'btn-voucher-audit',   'voucher:audit',   3),
    ('驳回凭证',   'btn-voucher-reject',  'voucher:reject',  4),
    ('凭证过账',   'btn-voucher-post',    'voucher:post',    5),
    ('取消过账',   'btn-voucher-unpost',  'voucher:unpost',  6),
    ('凭证红冲',   'btn-voucher-reverse', 'voucher:reverse', 7),
    ('凭证关闭',   'btn-voucher-close',   'voucher:close',   8)
) AS v(mname, mcode, perm, sort)
CROSS JOIN (SELECT id FROM t_menu
            WHERE deleted = 0 AND permission = 'voucher:list' LIMIT 1) p
WHERE p.id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM t_menu WHERE permission = v.perm);

-- ② 角色授权：制单族给 3 角色，审核/过账/红冲族只给 2 角色（D-8 分权）
--    ⚠️ t_role_menu **没有 updated_at 列**（实测 information_schema），t_menu.menu_code 必填。
INSERT INTO t_role_menu (role_id, menu_id, created_by, created_at, updated_by)
SELECT r.id, m.id, 1, NOW(), 1
FROM (VALUES
    ('ACCOUNTANT',  'voucher:create'),
    ('FINANCE_MGR', 'voucher:create'),
    ('ADMIN',       'voucher:create'),
    ('ACCOUNTANT',  'voucher:submit'),
    ('FINANCE_MGR', 'voucher:submit'),
    ('ADMIN',       'voucher:submit'),
    -- ↓ 以下为「审核与记账族」，按 D-8 **刻意不给 ACCOUNTANT**
    ('FINANCE_MGR', 'voucher:audit'),
    ('ADMIN',       'voucher:audit'),
    ('FINANCE_MGR', 'voucher:reject'),
    ('ADMIN',       'voucher:reject'),
    ('FINANCE_MGR', 'voucher:post'),
    ('ADMIN',       'voucher:post'),
    ('FINANCE_MGR', 'voucher:unpost'),
    ('ADMIN',       'voucher:unpost'),
    ('FINANCE_MGR', 'voucher:reverse'),
    ('ADMIN',       'voucher:reverse'),
    ('FINANCE_MGR', 'voucher:close'),
    ('ADMIN',       'voucher:close')
) AS v(role_code, perm)
JOIN t_role r ON r.role_code = v.role_code AND r.deleted = 0
JOIN t_menu m ON m.permission = v.perm AND m.deleted = 0
WHERE NOT EXISTS (
    SELECT 1 FROM t_role_menu x WHERE x.role_id = r.id AND x.menu_id = m.id
);

DO $$
DECLARE n int;
BEGIN
    SELECT count(*) INTO n FROM t_menu WHERE permission LIKE 'voucher:%' AND deleted = 0;
    RAISE NOTICE '凭证类权限码现共 % 个', n;
END $$;