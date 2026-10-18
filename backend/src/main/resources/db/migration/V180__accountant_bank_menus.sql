-- =====================================================================
-- SPEC-P125：补齐代理端「会计」角色的银行/现金菜单，并补出纳的银行账户
--
-- 背景（2026-10-13 实测）：
--   角色菜单绑定数统计（银行/现金类）：
--     ADMIN=6/44  FINANCE_MGR=6/42  CASHIER=5/8  OPERATOR=2/14  ACCOUNTANT=0/24
--   即 ACCOUNTANT 绑定 **0 个**银行/现金菜单，而权限全部给了出纳 ——
--   角色名与职责正好对调。侧边栏「资金管理」分组因 hasVisible() 全为 false 而整组不渲染，
--   会计登录后看不到任何银行入口。
--
--   另一处：CASHIER 缺「银行账户」，而出纳做银行/现金记账必须能看到对应账户。
--
-- 裁定（老丁，2026-10-13）：
--   - 会计  → 银行日记账、银行对账单、银行对账、现金日记账、票据管理
--              （**不给**「银行账户」：属账户档案配置，保留给管理员/主管维护）
--   - 出纳  → 追加「银行账户」
--
-- 说明：
--   1) 本迁移只写 t_role_menu（关联表），不改 t_menu、不改任何角色定义。
--   2) 按 **path** 定位菜单而非硬编码 menu_id —— 菜单 id 在不同环境可能不同，
--      path 是语义稳定的键（与 AppSidebar.vue 的 index 一致）。
--   3) 幂等：NOT EXISTS 保证重复执行不产生重复行。
--   4) 「待处理流水」/finance/pending-pool 无独立菜单行，它与「银行对账单」
--      共用权限码 bank:statement:list，故随对账单一并生效，无需单独处理。
-- =====================================================================

DO $$
DECLARE
    v_added int;
BEGIN
    INSERT INTO t_role_menu (role_id, menu_id)
    SELECT r.id, m.id
    FROM t_role r
    CROSS JOIN t_menu m
    WHERE r.deleted = 0
      AND m.deleted = 0
      AND (
            -- 会计：银行四项 + 票据管理（不含银行账户）
            (r.role_code = 'ACCOUNTANT'
             AND m.path IN ('/finance/bank-journal',
                            '/finance/bank-statement',
                            '/finance/bank-reconciliation',
                            '/finance/cash-journal',
                            '/finance/ticket'))
         OR -- 出纳：补银行账户（记账时必须能看到对应账户）
            (r.role_code = 'CASHIER'
             AND m.path = '/finance/bank-account')
      )
      AND NOT EXISTS (
            SELECT 1 FROM t_role_menu rm
            WHERE rm.role_id = r.id AND rm.menu_id = m.id
      );

    GET DIAGNOSTICS v_added = ROW_COUNT;
    RAISE NOTICE 'V180: 新增角色↔菜单绑定 % 条', v_added;
END $$;

-- 留证：打印补齐后的绑定情况，便于事后核对（AGENTS：迁移要可自证）
DO $$
DECLARE
    r record;
BEGIN
    FOR r IN
        SELECT role.role_code,
               count(*) FILTER (WHERE m.path LIKE '/finance/bank%'
                                  OR m.path LIKE '/finance/cash%'
                                  OR m.path LIKE '/finance/ticket%') AS bank_cnt,
               count(*) AS total_cnt
        FROM t_role_menu rm
        JOIN t_role role ON role.id = rm.role_id
        JOIN t_menu m     ON m.id = rm.menu_id
        WHERE role.role_code IN ('ACCOUNTANT', 'CASHIER')
        GROUP BY role.role_code
        ORDER BY role.role_code
    LOOP
        RAISE NOTICE 'V180: % 银行/现金菜单 % 个，总菜单 % 个', r.role_code, r.bank_cnt, r.total_cnt;
    END LOOP;
END $$;