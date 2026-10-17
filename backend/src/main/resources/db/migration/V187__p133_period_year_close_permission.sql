-- SPEC-P133 批次 1 附带修复：period:year-close 是幽灵权限码 ⇒ 年度结账对所有人 403
--
-- 发现方式：新增的 G1 守卫（PermissionManifestGuardRealDBTest#manifestPermissionsExistInMenu）
--          比对「清单里的权限码」与「t_menu.permission」，第一轮就报了它。
--          **与 REQ-2026-146（voucher:update）、REQ-2026-148（其余 10 个幽灵码）同型。**
--
-- 症状：`POST /api/base/voucher/v1/period-close/year-close` 上写着
--       @PreAuthorize("hasAuthority('period:year-close')")，而 t_menu **无此权限码**
--       ⇒ 无任何角色持有 ⇒ **执行年度结账对所有角色（含 ADMIN）永久 403**，
--       且报错文案是「权限不足」，看起来像正常鉴权、实则该功能从未被任何角色用过。
--
-- 角色分配原则（沿用 V186 的口径，**可审计的默认，非业务裁定**）：
--   年度结账是**高危反记账操作**（锁定全年 + 生成次年期间 + 期初结转），
--   其角色集合与「反结账 period:reopen」语义对齐 = ADMIN + FINANCE_MGR。
--   ⚠️ 不给 ACCOUNTANT：会计能结账但不能反结账本就自相矛盾，
--      而给反结账权就必须给年结权（否则只能进不能退）。
--
-- ⚠️ 全部幂等：t_menu 已知存在重名记录（AGENTS §4.5 第 12 条），补数据必须自带幂等。

-- ① 菜单行：挂在「期末结账」（id=34，permission='period:close'）之下，作为 BUTTON
INSERT INTO t_menu (parent_id, menu_name, menu_code, path, component, icon, sort_order,
                    menu_type, is_active, permission, created_by, created_at, updated_by, updated_at, deleted)
SELECT p.id, '年度结账', 'btn-period-year-close', NULL, NULL, NULL, 2,
       'BUTTON', true, 'period:year-close', 1, NOW(), 1, NOW(), 0
FROM (SELECT id FROM t_menu
      WHERE deleted = 0 AND permission = 'period:close' LIMIT 1) p
WHERE p.id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM t_menu WHERE permission = 'period:year-close');

-- ② 角色授权：继承「反结账 period:reopen」的角色集合（ADMIN + FINANCE_MGR）
-- ⚠️ t_role_menu **没有 updated_at 列**（实测 information_schema：只有 updated_by），
--    凭印象写会报 column does not exist。t_menu 则**必须**给 menu_code（NOT NULL 无默认值）。
--   ④🔴 首版写成 `FROM t_role_menu rm JOIN t_menu m ON m.permission='period:reopen'`，
--     那是把**已存在的那批授权行原样重插** ⇒ 必然撞 uq_role_menu。
--     正确形状是：先取出「持有者角色**集合**」，再与新菜单**配对**插入。
--     判据：复制授权关系时，要复制的是**角色集合**，不是**关系行本身**。
INSERT INTO t_role_menu (role_id, menu_id, created_by, created_at, updated_by)
SELECT holders.role_id, m.id, 1, NOW(), 1
FROM (
    SELECT DISTINCT rm.role_id
    FROM t_role_menu rm
    JOIN t_menu src ON src.id = rm.menu_id AND src.deleted = 0
    WHERE src.permission = 'period:reopen'
) holders
CROSS JOIN (
    SELECT id FROM t_menu
    WHERE permission = 'period:year-close' AND deleted = 0
    LIMIT 1
) m
WHERE NOT EXISTS (
    SELECT 1 FROM t_role_menu x WHERE x.role_id = holders.role_id AND x.menu_id = m.id
);

-- 🔴 踩坑留证：`RAISE NOTICE` 是 **PL/pgSQL 语法，不能写在裸 SQL 迁移里**
--    （首版直接写在文件末尾 ⇒ Flyway 报 "migration with version" 失败）。
--    必须包在 DO 块中。同型教训见 AGENTS §4.2 第 19 条（DDL 执行顺序/语法约束）。
DO $$
DECLARE n bigint;
BEGIN
    SELECT count(*) INTO n
    FROM t_role_menu rm JOIN t_menu m ON m.id = rm.menu_id
    WHERE m.permission = 'period:year-close';
    RAISE NOTICE 'period:year-close 已补齐，授权角色数=%', n;
END $$;