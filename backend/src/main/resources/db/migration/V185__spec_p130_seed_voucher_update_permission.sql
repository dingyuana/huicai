-- SPEC-P130：补齐幽灵权限码 voucher:update（凭证编辑）
--
-- 背景：`finance/voucher/edit` 路由声明了 `meta.permission = 'voucher:update'`，
--       路由守卫据此判定 `hasPermission`；但 t_menu 里**从来不存在**该权限码。
--       ⇒ 除 SUPER_ADMIN（跳过该校验）外，**任何角色都不可能拥有它**，
--         凭证编辑页对所有非超管永久 403。
--       而「凭证详情」用的是 `voucher:list`（存在）⇒ 表现为「详情能看、点编辑就 403」。
--
-- 业务依据：会计的职责是**制单**（P119/S-26：ACCOUNTANT 制单、REVIEWER 审核），
--           必须能编辑自己录入的草稿凭证，否则制单职责被架空。
--
-- ⚠️ 建成 BUTTON 而非 MENU：它是动作权限、不是可导航页面；
--    形态对齐既有 BUTTON 行（path 为空、挂在父菜单下）。
-- ⚠️ 幂等：ON CONFLICT / NOT EXISTS 保证重复执行不产生重名行
--    （AGENTS §4.5：t_menu 存在重名记录，补数据必须自带幂等）。

-- ① 权限码落地
INSERT INTO t_menu (parent_id, menu_name, menu_code, path, component, icon, sort_order,
                    menu_type, is_active, permission, created_by, created_at, updated_by, updated_at, deleted)
SELECT p.id, '编辑凭证', 'voucher_update', NULL, NULL, NULL, 2,
       'BUTTON', true, 'voucher:update', 1, NOW(), 1, NOW(), 0
FROM t_menu p
WHERE p.deleted = 0 AND p.permission = 'voucher:list'
  AND NOT EXISTS (SELECT 1 FROM t_menu m WHERE m.deleted = 0 AND m.permission = 'voucher:update')
LIMIT 1;

-- ② 授予会计与财务主管（按 role_code，不硬编码 role_id —— id 会随种子变动）
INSERT INTO t_role_menu (role_id, menu_id, created_at, created_by)
SELECT r.id, m.id, NOW(), 1
FROM t_role r, t_menu m
WHERE r.deleted = 0
  AND r.role_code IN ('ACCOUNTANT', 'FINANCE_MGR')
  AND m.deleted = 0
  AND m.permission = 'voucher:update'
  AND NOT EXISTS (
      SELECT 1 FROM t_role_menu rm
      WHERE rm.role_id = r.id AND rm.menu_id = m.id
  );