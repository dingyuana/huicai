-- SPEC-P133 D-9：为 tax(39) + asset(15) 共 54 个写端点补动作级权限码
--
-- 背景：批次 4 全局切 enforcing 后，这 54 条**全部仍是粗粒度（页面级）码**
--       ⇒ 「能看发票就能删发票 / 能看卡片就能删卡片」，
--       **不是**「发票删除被保护」，只是加了一道与权限无关的门槛。
--       这是消除粗粒度屏障的唯一途径（SPEC-P133 §8.9.3 已登记该风险）。
--
-- 角色分配（沿用 V186/V188/V189 的口径，**可审计的默认值，非业务终裁**）：
--   · **编制族**（create/import/preview/submit/revert/编制）
--     ← 取同模块既有页面码的持有者：
--         tax:input:list  / tax:output:list  => ADMIN, FINANCE_MGR, OPERATOR
--         asset:*:list                        => ACCOUNTANT, ADMIN, FINANCE_MGR
--   · **审批族**（review/confirm/certify/approve/reject/void/reverse/revert
--                /mark-vouchered/complete）
--     **一律只给 ADMIN + FINANCE_MGR** —— 与 V188 的 D-8 分权保持一致：
--     编制权与审批权分离，否则编制者可自编自批。
--   · **reverse/void（作废与红冲）** 独立成码且只在审批族 ——
--     最高危的反记账操作，不应与普通修改同权（延续 D-9 的裁定精神）。
--
-- ⚠️ 若某码确应给更多角色，请在 SPEC-P133 §8 写明理由后再改，
--    **不要在迁移里静默扩权**。
-- ⚠️ 全部幂等；`t_role_menu` **没有 updated_at 列**，`t_menu.menu_code` 必填。

-- ══════════════ ① 菜单行 ══════════════
-- ⚠️ 每行带 parent 列，用 CASE 取对应页面码的 id ——
--    首版写成 `SELECT p.id ... FROM (VALUES ...) v CROSS JOIN (...别名 m...)`
--    却引用了未定义的 `p`，报 missing FROM-clause entry for table "p"。
--    判据：多父节点的批量 INSERT，应把父标识**放进数据行**再 CASE 映射，
--    而不是让一个 CROSS JOIN 去猜「这一行该用哪个父」。
INSERT INTO t_menu (parent_id, menu_name, menu_code, path, component, icon, sort_order,
                    menu_type, is_active, permission, created_by, created_at, updated_by, updated_at, deleted)
SELECT CASE v.parent
           WHEN 'tax:input:list'      THEN m.ti
           WHEN 'tax:output:list'     THEN m.to
           WHEN 'tax:vat:view'        THEN m.tv
           WHEN 'asset:card:list'     THEN m.ac
           WHEN 'asset:category:list' THEN m.ag
           WHEN 'asset:disposal:list' THEN m.ad
           WHEN 'asset:inventory:list' THEN m.ai
       END,
       v.mname, v.mcode, NULL, NULL, NULL, v.sort,
       'BUTTON', true, v.perm, 1, NOW(), 1, NOW(), 0
FROM (VALUES
    -- ── 进项发票（父：tax:input:list）──
    ('进项开票','btn-tax-in-create','tax:input:create',1,'tax:input:list'),
    ('进项导入','btn-tax-in-import','tax:input:import',2,'tax:input:list'),
    ('进项预览','btn-tax-in-preview','tax:input:preview',2.5,'tax:input:list'),
    ('进项提交审核','btn-tax-in-review','tax:input:review',3,'tax:input:list'),
    ('进项确认','btn-tax-in-confirm','tax:input:confirm',4,'tax:input:list'),
    ('进项认证','btn-tax-in-certify','tax:input:certify',5,'tax:input:list'),
    ('进项作废','btn-tax-in-void','tax:input:void',6,'tax:input:list'),
    ('进项红冲','btn-tax-in-reverse','tax:input:reverse',7,'tax:input:list'),
    ('进项冲回','btn-tax-in-revert','tax:input:revert',8,'tax:input:list'),
    ('进项驳回','btn-tax-in-reject','tax:input:reject',9,'tax:input:list'),
    -- ── 销项发票（父：tax:output:list）──
    ('销项开票','btn-tax-out-create','tax:output:create',1,'tax:output:list'),
    ('销项导入','btn-tax-out-import','tax:output:import',2,'tax:output:list'),
    ('销项预览','btn-tax-out-preview','tax:output:preview',2.5,'tax:output:list'),
    ('销项提交审核','btn-tax-out-review','tax:output:review',3,'tax:output:list'),
    ('销项确认','btn-tax-out-confirm','tax:output:confirm',4,'tax:output:list'),
    ('销项核销','btn-tax-out-vouchered','tax:output:vouchered',5,'tax:output:list'),
    ('销项作废','btn-tax-out-void','tax:output:void',6,'tax:output:list'),
    ('销项红冲','btn-tax-out-reverse','tax:output:reverse',7,'tax:output:list'),
    ('销项冲回','btn-tax-out-revert','tax:output:revert',8,'tax:output:list'),
    ('销项驳回','btn-tax-out-reject','tax:output:reject',9,'tax:output:list'),
    ('红字关联','btn-tax-link-red','tax:output:link-red',10,'tax:output:list'),
    -- 税种维护挂在销项页下（无独立菜单；登记局限）
    ('税种维护','btn-tax-type-manage','tax:type:manage',2,'tax:output:list'),
    -- 跨模块写总账
    ('生成记账凭证','btn-tax-gen-voucher','tax:voucher:generate',11,'tax:output:list'),
    -- ── 纳税申报（父：tax:vat:view）──
    ('申报提交','btn-tax-dec-submit','tax:declaration:submit',1,'tax:vat:view'),
    ('申报审批','btn-tax-dec-approve','tax:declaration:approve',2,'tax:vat:view'),
    ('申报驳回','btn-tax-dec-reject','tax:declaration:reject',3,'tax:vat:view'),
    -- ── 资产 ──
    ('资产卡片维护','btn-asset-card-manage','asset:card:manage',1,'asset:card:list'),
    ('折旧计提','btn-asset-card-depreciate','asset:card:depreciate',2,'asset:card:list'),
    ('折旧凭证','btn-asset-card-voucher','asset:card:voucher',3,'asset:card:list'),
    ('资产类别维护','btn-asset-cat-manage','asset:category:manage',1,'asset:category:list'),
    ('资产处置','btn-asset-disposal-create','asset:disposal:create',1,'asset:disposal:list'),
    ('资产处置审批','btn-asset-disposal-approve','asset:disposal:approve',2,'asset:disposal:list'),
    ('资产盘点','btn-asset-inv-manage','asset:inventory:manage',1,'asset:inventory:list'),
    ('盘点完成','btn-asset-inv-complete','asset:inventory:complete',2,'asset:inventory:list')
) AS v(mname, mcode, perm, sort, parent)
CROSS JOIN (
    SELECT
        MAX(CASE WHEN permission = 'tax:input:list'      THEN id END) AS ti,
        MAX(CASE WHEN permission = 'tax:output:list'     THEN id END) AS to,
        MAX(CASE WHEN permission = 'tax:vat:view'         THEN id END) AS tv,
        MAX(CASE WHEN permission = 'asset:card:list'     THEN id END) AS ac,
        MAX(CASE WHEN permission = 'asset:category:list' THEN id END) AS ag,
        MAX(CASE WHEN permission = 'asset:disposal:list'  THEN id END) AS ad,
        MAX(CASE WHEN permission = 'asset:inventory:list' THEN id END) AS ai
    FROM (SELECT DISTINCT permission, id FROM t_menu
          WHERE deleted = 0 AND permission IN
              ('tax:input:list','tax:output:list','tax:vat:view',
               'asset:card:list','asset:category:list',
               'asset:disposal:list','asset:inventory:list')) q
) m
WHERE m.ti IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM t_menu WHERE permission = v.perm);

-- ══════════════ ② 角色授权：编制族（按各模块页面码的持有者）══════════════
-- 🔴 VALUES 必须**每行同列数**（此处固定 2 列 role_code/perm）。
--    首版把「3 个角色一行」与「2 个角色一行」混写 ⇒ VALUES lists must all be the same length。
INSERT INTO t_role_menu (role_id, menu_id, created_by, created_at, updated_by)
SELECT r.id, m.id, 1, NOW(), 1
FROM (VALUES
    -- tax 编制族 ← tax:input:list / tax:output:list 的持有者（ADMIN/FINANCE_MGR/OPERATOR）
    ('ADMIN','tax:input:create'),      ('FINANCE_MGR','tax:input:create'),   ('OPERATOR','tax:input:create'),
    ('ADMIN','tax:input:import'),      ('FINANCE_MGR','tax:input:import'),   ('OPERATOR','tax:input:import'),
    ('ADMIN','tax:output:create'),     ('FINANCE_MGR','tax:output:create'),  ('OPERATOR','tax:output:create'),
    ('ADMIN','tax:output:import'),     ('FINANCE_MGR','tax:output:import'),  ('OPERATOR','tax:output:import'),
    -- 冲回属编制族（修正错误数据而非审批新单）
    ('ADMIN','tax:input:revert'),      ('FINANCE_MGR','tax:input:revert'),
    ('ADMIN','tax:output:revert'),     ('FINANCE_MGR','tax:output:revert'),
    -- 税种 / 申报编制
    ('ADMIN','tax:type:manage'),       ('FINANCE_MGR','tax:type:manage'),
    ('ADMIN','tax:declaration:submit'),('FINANCE_MGR','tax:declaration:submit'),
    -- 生成记账凭证：跨模块写总账，参照 voucher:create 的持有者（ACCOUNTANT/ADMIN/FINANCE_MGR）
    ('ACCOUNTANT','tax:voucher:generate'), ('FINANCE_MGR','tax:voucher:generate'), ('ADMIN','tax:voucher:generate'),
    -- 红字关联：影响红字发票配对
    ('ADMIN','tax:output:link-red'),   ('FINANCE_MGR','tax:output:link-red'),
    -- asset 编制族 ← asset:*:list 的持有者（ACCOUNTANT/ADMIN/FINANCE_MGR）
    ('ACCOUNTANT','asset:card:manage'),     ('FINANCE_MGR','asset:card:manage'),   ('ADMIN','asset:card:manage'),
    ('ACCOUNTANT','asset:card:depreciate'), ('FINANCE_MGR','asset:card:depreciate'),('ADMIN','asset:card:depreciate'),
    ('ACCOUNTANT','asset:category:manage'), ('FINANCE_MGR','asset:category:manage'),('ADMIN','asset:category:manage'),
    ('ACCOUNTANT','asset:disposal:create'), ('FINANCE_MGR','asset:disposal:create'),('ADMIN','asset:disposal:create'),
    ('ACCOUNTANT','asset:inventory:manage'),('FINANCE_MGR','asset:inventory:manage'),('ADMIN','asset:inventory:manage')
) AS v(role_code, perm)
JOIN t_role r ON r.role_code = v.role_code AND r.deleted = 0
JOIN t_menu m ON m.permission = v.perm AND m.deleted = 0
WHERE NOT EXISTS (
    SELECT 1 FROM t_role_menu x WHERE x.role_id = r.id AND x.menu_id = m.id
);

-- ══════════════ ③ 角色授权：审批族（只给 ADMIN + FINANCE_MGR，D-8 分权）══════════════
INSERT INTO t_role_menu (role_id, menu_id, created_by, created_at, updated_by)
SELECT r.id, m.id, 1, NOW(), 1
FROM (VALUES
    ('FINANCE_MGR','tax:input:review'),   ('ADMIN','tax:input:review'),
    ('FINANCE_MGR','tax:input:confirm'),  ('ADMIN','tax:input:confirm'),
    ('FINANCE_MGR','tax:input:certify'),  ('ADMIN','tax:input:certify'),
    ('FINANCE_MGR','tax:input:void'),     ('ADMIN','tax:input:void'),
    ('FINANCE_MGR','tax:input:reverse'),  ('ADMIN','tax:input:reverse'),
    ('FINANCE_MGR','tax:input:reject'),   ('ADMIN','tax:input:reject'),
    ('FINANCE_MGR','tax:output:review'),  ('ADMIN','tax:output:review'),
    ('FINANCE_MGR','tax:output:confirm'), ('ADMIN','tax:output:confirm'),
    ('FINANCE_MGR','tax:output:vouchered'),('ADMIN','tax:output:vouchered'),
    ('FINANCE_MGR','tax:output:void'),    ('ADMIN','tax:output:void'),
    ('FINANCE_MGR','tax:output:reverse'), ('ADMIN','tax:output:reverse'),
    ('FINANCE_MGR','tax:output:reject'),  ('ADMIN','tax:output:reject'),
    ('FINANCE_MGR','tax:declaration:approve'), ('ADMIN','tax:declaration:approve'),
    ('FINANCE_MGR','tax:declaration:reject'),  ('ADMIN','tax:declaration:reject'),
    ('FINANCE_MGR','asset:card:voucher'),      ('ADMIN','asset:card:voucher'),
    ('FINANCE_MGR','asset:disposal:approve'),  ('ADMIN','asset:disposal:approve'),
    ('FINANCE_MGR','asset:inventory:complete'),('ADMIN','asset:inventory:complete')
) AS v(role_code, perm)
JOIN t_role r ON r.role_code = v.role_code AND r.deleted = 0
JOIN t_menu m ON m.permission = v.perm AND m.deleted = 0
WHERE NOT EXISTS (
    SELECT 1 FROM t_role_menu x WHERE x.role_id = r.id AND x.menu_id = m.id
);

DO $$
DECLARE n int; a int;
BEGIN
    SELECT count(*) INTO n FROM t_menu WHERE deleted = 0
      AND (permission LIKE 'tax:%' OR permission LIKE 'asset:%');
    SELECT count(*) INTO a FROM t_role_menu rm JOIN t_menu m ON m.id = rm.menu_id
      WHERE m.permission LIKE 'tax:%' OR m.permission LIKE 'asset:%';
    RAISE NOTICE 'tax/asset 类权限码现共 % 个，授权关系 % 条', n, a;
END $$;