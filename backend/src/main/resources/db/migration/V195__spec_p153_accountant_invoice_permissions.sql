-- SPEC-P153 发票权限种子收口（用户报障：王会计 ACCOUNTANT 登录看不到发票、
-- 「导入销售发票」无入口；取证三层实测：路由/页面/菜单行俱在，缺的是角色授权）。
--
-- 根因（真库 t_role_menu 实测）：
-- ① ACCOUNTANT 无任何 tax:input:*/tax:output:* 码 —— 但它持有 tax:voucher:generate
--    （V190 生成记账凭证，业务上只对发票有意义）：「能从发票生成凭证」却
--    「看不到发票列表/不能导入发票」自相矛盾。发票录入/导入是会计日常
--    （金蝶精斗云/畅捷通好会计标配），V96 菜单精简 + V188/V190 种子沿袭遗漏。
-- ② tax:input:preview / tax:output:preview 两个按钮码 V190 建了菜单行却
--    **没有任何角色持有**（含 ADMIN）⇒ 前端「导入发票 → 下一步: 预览」调
--    POST /{sales|input}-invoices/preview 对全员 403，导入流程全库无人走得通。
-- ③ OPERATOR 授权了子菜单 51/52 却没有父目录 5（税务发票）——前端侧栏按
--    权限码渲染不受影响，但后端 getRoutesByUserId 的 buildTree 从 parentId==null
--    起步，该角色的路由树恒为空壳（名实不符的授权行）。
--
-- 授权口径（沿用 V190 的分权裁定，不静默扩权）：
-- * ACCOUNTANT 只给「编制族」：list/create/import/preview + vat 查看。
--   审批族（review/confirm/certify/void/reverse/reject/vouchered）继续不给 ——
--   D-8 编制权与审批权分离，会计自编自批是被明令禁止的。
-- * preview 授予所有 import 持有者：预览是导入流程的内部步骤（前端
--   OutputInvoiceList.vue 导入对话框「下一步: 预览」），import 与 preview
--   必须同进同退，否则导入功能对该角色形同虚设。
-- * OPERATOR 补父目录 5：「给子必须可见父」。
--
-- ⚠️ t_role_menu 无 deleted 列，是硬授权表；全部幂等。

-- ═══ ① ACCOUNTANT 发票编制族（菜单行 V96/V190 已存在，只补授权行）═══
INSERT INTO t_role_menu (role_id, menu_id, created_by, created_at, updated_by)
SELECT r.id, m.id, 1, NOW(), 1
FROM (VALUES
    ('ACCOUNTANT', 'tax:input:list'),
    ('ACCOUNTANT', 'tax:input:create'),
    ('ACCOUNTANT', 'tax:input:import'),
    ('ACCOUNTANT', 'tax:input:preview'),
    ('ACCOUNTANT', 'tax:output:list'),
    ('ACCOUNTANT', 'tax:output:create'),
    ('ACCOUNTANT', 'tax:output:import'),
    ('ACCOUNTANT', 'tax:output:preview'),
    ('ACCOUNTANT', 'tax:vat:view')
) AS v(role_code, perm)
JOIN t_role r ON r.role_code = v.role_code AND r.deleted = 0
JOIN t_menu m ON m.permission = v.perm AND m.deleted = 0
WHERE NOT EXISTS (
    SELECT 1 FROM t_role_menu x WHERE x.role_id = r.id AND x.menu_id = m.id
);

-- ═══ ② preview 码补授权：import 持有者全员必须同时持有 preview ═══
-- （V190 建了按钮行漏发授权 ⇒ 导入预览全员 403）
INSERT INTO t_role_menu (role_id, menu_id, created_by, created_at, updated_by)
SELECT DISTINCT rm.role_id, pm.id, 1, NOW(), 1
FROM t_role_menu rm
JOIN t_menu im ON im.id = rm.menu_id
             AND im.deleted = 0
             AND im.permission IN ('tax:input:import', 'tax:output:import')
JOIN t_menu pm ON pm.deleted = 0
             AND pm.permission = replace(im.permission, ':import', ':preview')
WHERE NOT EXISTS (
    SELECT 1 FROM t_role_menu x WHERE x.role_id = rm.role_id AND x.menu_id = pm.id
);

-- ═══ ③ OPERATOR 补父目录「税务发票」(id=5)：已授子菜单 51/52 的角色必须可见父 ═══
INSERT INTO t_role_menu (role_id, menu_id, created_by, created_at, updated_by)
SELECT DISTINCT rm.role_id, 5, 1, NOW(), 1
FROM t_role_menu rm
WHERE rm.menu_id IN (51, 52)
  AND NOT EXISTS (
      SELECT 1 FROM t_role_menu x WHERE x.role_id = rm.role_id AND x.menu_id = 5
  );

-- ═══ ④ 数据一致性巡检（本迁移的自证前提，出错即人工介入）═══
DO $$
DECLARE
    bad INTEGER;
BEGIN
    -- 任何持有 51/52 子菜单的角色必须同时持有父目录 5
    SELECT count(*) INTO bad
    FROM t_role_menu rm
    WHERE rm.menu_id IN (51, 52)
      AND NOT EXISTS (SELECT 1 FROM t_role_menu x WHERE x.role_id = rm.role_id AND x.menu_id = 5);
    IF bad > 0 THEN
        RAISE EXCEPTION 'SPEC-P153 迁移后仍存在 % 条「授子未授父」的发票菜单授权，请排查', bad;
    END IF;

    -- 任何持有 import 的角色必须同时持有同侧 preview
    SELECT count(*) INTO bad
    FROM t_role_menu rm
    JOIN t_menu im ON im.id = rm.menu_id AND im.permission IN ('tax:input:import','tax:output:import')
    JOIN t_menu pm ON pm.permission = replace(im.permission, ':import', ':preview')
    WHERE NOT EXISTS (SELECT 1 FROM t_role_menu x WHERE x.role_id = rm.role_id AND x.menu_id = pm.id);
    IF bad > 0 THEN
        RAISE EXCEPTION 'SPEC-P153 迁移后仍存在 % 条「有 import 无 preview」授权，导入流程将 403', bad;
    END IF;
END $$;
