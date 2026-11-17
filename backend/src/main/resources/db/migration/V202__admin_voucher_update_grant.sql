-- ═══════════════════════════════════════════════════════════════════
-- V202：SPEC-P158 收尾批次①——ADMIN 角色补授 voucher:update
--   V1.115 勘误后保留待裁定项（用户 2026-11-09 批准）：
--   voucher:update 持有面仅 ACCOUNTANT/FINANCE_MGR，
--   admin 主体编辑/删除凭证直接 403（校验面正确、授权面缺位）。
--   权限码/菜单行 V185 起就对（G11 已锁），此处只补一条授权行。
-- ═══════════════════════════════════════════════════════════════════

-- ── 授权（V190 VALUES 家法：permission 列口径；NOT EXISTS 幂等）──
INSERT INTO t_role_menu (role_id, menu_id, created_by, created_at, updated_by)
SELECT r.id, m.id, 1, NOW(), 1
FROM (VALUES ('ADMIN', 'voucher:update')) AS v(role_code, perm)
JOIN t_role r ON r.role_code = v.role_code AND r.deleted = 0
JOIN t_menu m ON m.permission = v.perm AND m.deleted = 0
WHERE NOT EXISTS (
    SELECT 1 FROM t_role_menu x WHERE x.role_id = r.id AND x.menu_id = m.id);

-- ── 自证不变量（V196 教训：授权行数≥1 防空转）──
DO $$
DECLARE
    v_holders INT;
BEGIN
    SELECT count(*) INTO v_holders
    FROM t_role_menu rm
    JOIN t_role r ON r.id = rm.role_id AND r.deleted = 0
    JOIN t_menu m ON m.id = rm.menu_id AND m.deleted = 0
    WHERE r.role_code = 'ADMIN' AND m.permission = 'voucher:update';
    IF v_holders < 1 THEN
        RAISE EXCEPTION 'V202 自证失败：ADMIN 未持有 voucher:update（授权行 % 条）', v_holders;
    END IF;
    RAISE NOTICE 'V202 自证通过：ADMIN 持有 voucher:update（共 % 条授权行）', v_holders;
END $$;
