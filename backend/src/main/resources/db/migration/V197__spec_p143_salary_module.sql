-- SPEC-P143（REQ-2026-160 / roadmap P0-1）：工资薪酬模块 —— 表结构 + 权限码
--
-- 设计口径（方案 A 裁定：全量核心一轮到位；D-3 员工自助查询后置）：
--   · D-4 薪资项目可配置 ⇒ 明细走**纵表**（bill_item 每员工每项目一行），
--     项目类别四类：EARN(收入) / SOCIAL_INS(个人社保公积金=专项扣除) /
--     SPECIAL_ADD(专项附加扣除，只进个税公式不扣钱) / OTHER_DEDUCT(其他扣款)；
--   · D-1 个税累计预扣法：按员工逐月累计（本年已确认工资单即台账，
--     不另设冗余台账表）；7 档税率表 + 速算扣除数在 Java 侧
--     {@code IndividualIncomeTaxCalculator}（纯函数 + 守卫测试）；
--   · D-2 工资凭证走现有凭证流程，两阶段：
--       确认（计提）凭证 = 借 6602 管理费用(应发) / 贷 2211 应付职工薪酬(应发)
--       发放凭证         = 借 2211(应发) / 贷 2221 应交税费(个税)
--                           + 贷 2241 其他应付款(社保个人) + 贷 1002 银行存款(实发)
--     科目 fail-loud 固定（P85 先例）；**单位部分社保公积金本轮不做（登记遗留）**；
--   · 状态机 DRAFT --confirm--> CONFIRMED --pay--> PAID（chk 约束兜底）；
--   · 凭证号 = SALC-{billId} / SALP-{billId}（ADJ- 同族，不能用期间幂等式——
--     允许同期间多张工资单，如在职/离职分单）。
--
-- ⚠️ 全部幂等；菜单码 btn-* 形态、permission 列冒号形态（V196 教训：authorities
--    加载链读 t_menu.permission，授权推导一律按 permission 列）。

-- ══════════════ ① 薪资项目（D-4 可配置）══════════════
CREATE TABLE IF NOT EXISTS t_salary_item (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    enterprise_id BIGINT NOT NULL,
    item_code VARCHAR(50) NOT NULL,
    item_name VARCHAR(100) NOT NULL,
    category VARCHAR(30) NOT NULL,
    default_amount NUMERIC(14,2) NOT NULL DEFAULT 0,
    is_taxable BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INT NOT NULL DEFAULT 0,
    remark VARCHAR(500),
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_by BIGINT,
    updated_at TIMESTAMP,
    deleted INT NOT NULL DEFAULT 0
);
ALTER TABLE t_salary_item DROP CONSTRAINT IF EXISTS chk_salary_item_category;
ALTER TABLE t_salary_item ADD CONSTRAINT chk_salary_item_category
    CHECK (category IN ('EARN','SOCIAL_INS','SPECIAL_ADD','OTHER_DEDUCT'));
CREATE UNIQUE INDEX IF NOT EXISTS uq_salary_item_code
    ON t_salary_item (enterprise_id, item_code) WHERE deleted = 0;

-- ══════════════ ② 工资单主表 ══════════════
CREATE TABLE IF NOT EXISTS t_salary_bill (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    enterprise_id BIGINT NOT NULL,
    period VARCHAR(6) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    total_gross NUMERIC(14,2) NOT NULL DEFAULT 0,
    total_social NUMERIC(14,2) NOT NULL DEFAULT 0,
    total_special_add NUMERIC(14,2) NOT NULL DEFAULT 0,
    total_other_deduct NUMERIC(14,2) NOT NULL DEFAULT 0,
    total_tax NUMERIC(14,2) NOT NULL DEFAULT 0,
    total_net NUMERIC(14,2) NOT NULL DEFAULT 0,
    voucher_id BIGINT,
    pay_voucher_id BIGINT,
    remark VARCHAR(500),
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_by BIGINT,
    updated_at TIMESTAMP,
    deleted INT NOT NULL DEFAULT 0
);
ALTER TABLE t_salary_bill DROP CONSTRAINT IF EXISTS chk_salary_bill_status;
ALTER TABLE t_salary_bill ADD CONSTRAINT chk_salary_bill_status
    CHECK (status IN ('DRAFT','CONFIRMED','PAID'));
-- 同期间一张工资单（简化口径：按月发薪；多单需求出现再放宽）
CREATE UNIQUE INDEX IF NOT EXISTS uq_salary_bill_period
    ON t_salary_bill (enterprise_id, period) WHERE deleted = 0;

-- ══════════════ ③ 工资单明细（纵表）+ ④ 员工级汇总/个税快照 ══════════════
CREATE TABLE IF NOT EXISTS t_salary_bill_item (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    enterprise_id BIGINT NOT NULL,
    bill_id BIGINT NOT NULL,
    employee_id BIGINT NOT NULL,
    item_id BIGINT NOT NULL,
    amount NUMERIC(14,2) NOT NULL DEFAULT 0,
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_by BIGINT,
    updated_at TIMESTAMP,
    deleted INT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_salary_bill_item
    ON t_salary_bill_item (bill_id, employee_id, item_id) WHERE deleted = 0;

CREATE TABLE IF NOT EXISTS t_salary_bill_employee (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    enterprise_id BIGINT NOT NULL,
    bill_id BIGINT NOT NULL,
    employee_id BIGINT NOT NULL,
    gross NUMERIC(14,2) NOT NULL DEFAULT 0,
    taxable_gross NUMERIC(14,2) NOT NULL DEFAULT 0,
    social NUMERIC(14,2) NOT NULL DEFAULT 0,
    special_add NUMERIC(14,2) NOT NULL DEFAULT 0,
    other_deduct NUMERIC(14,2) NOT NULL DEFAULT 0,
    tax NUMERIC(14,2) NOT NULL DEFAULT 0,
    net NUMERIC(14,2) NOT NULL DEFAULT 0,
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_by BIGINT,
    updated_at TIMESTAMP,
    deleted INT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_salary_bill_employee
    ON t_salary_bill_employee (bill_id, employee_id) WHERE deleted = 0;

-- ══════════════ ⑤ 菜单行（目录 + 页面 + 按钮）══════════════
INSERT INTO t_menu (parent_id, menu_name, menu_code, path, component, icon, sort_order,
                    menu_type, is_active, permission, created_by, created_at, updated_by, updated_at, deleted)
SELECT NULL, '工资薪酬', 'menu-salary', NULL, NULL, NULL, 7,
       'DIR', true, NULL, 1, NOW(), 1, NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM t_menu x WHERE x.menu_code = 'menu-salary');

INSERT INTO t_menu (parent_id, menu_name, menu_code, path, component, icon, sort_order,
                    menu_type, is_active, permission, created_by, created_at, updated_by, updated_at, deleted)
SELECT (SELECT id FROM t_menu WHERE menu_code = 'menu-salary'), '工资单', 'menu-salary-bill',
       '/salary/bill', NULL, NULL, 1,
       'MENU', true, 'salary:list', 1, NOW(), 1, NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM t_menu x WHERE x.menu_code = 'menu-salary-bill');

INSERT INTO t_menu (parent_id, menu_name, menu_code, path, component, icon, sort_order,
                    menu_type, is_active, permission, created_by, created_at, updated_by, updated_at, deleted)
SELECT (SELECT id FROM t_menu WHERE menu_code = 'menu-salary-bill'), v.name, v.code,
       NULL, NULL, NULL, v.sort,
       'BUTTON', true, v.perm, 1, NOW(), 1, NOW(), 0
FROM (VALUES
    ('btn-salary-save',    '编辑工资单', 1, 'salary:save'),
    ('btn-salary-confirm', '确认计提',   2, 'salary:confirm'),
    ('btn-salary-pay',     '发放',       3, 'salary:pay')
) AS v(code, name, sort, perm)
WHERE NOT EXISTS (SELECT 1 FROM t_menu x WHERE x.menu_code = v.code);

-- ══════════════ ⑥ 授权（编制族：ACCOUNTANT/ADMIN/FINANCE_MGR，V190 家法）══════════════
INSERT INTO t_role_menu (role_id, menu_id, created_by, created_at, updated_by)
SELECT r.id, m.id, 1, NOW(), 1
FROM (VALUES
    ('ACCOUNTANT','salary:list'), ('ADMIN','salary:list'), ('FINANCE_MGR','salary:list'),
    ('ACCOUNTANT','salary:save'), ('ADMIN','salary:save'), ('FINANCE_MGR','salary:save'),
    ('ACCOUNTANT','salary:confirm'), ('ADMIN','salary:confirm'), ('FINANCE_MGR','salary:confirm'),
    ('ACCOUNTANT','salary:pay'), ('ADMIN','salary:pay'), ('FINANCE_MGR','salary:pay')
) AS v(role_code, perm)
JOIN t_role r ON r.role_code = v.role_code AND r.deleted = 0
JOIN t_menu m ON m.permission = v.perm AND m.deleted = 0
WHERE NOT EXISTS (
    SELECT 1 FROM t_role_menu x WHERE x.role_id = r.id AND x.menu_id = m.id);

-- ══════════════ ⑥.5 补齐工资科目 2241 其他应付款（V159 家法）══════════════
-- 发放凭证贷方需要 2241；全部迁移中无人种过该科目（生产存量行是 V95 时代手工遗留），
-- 全新库/新企业 pay 必然 fail-loud「未配置科目 2241」（L2 SalaryBillRealDBTest 实测坐实）。
-- 动态覆盖全部有基础科目（1122 标志）的企业，不硬编码 enterprise_id；
-- NOT EXISTS 守卫不带 deleted=0（uq_subject_code_ent 非部分唯一，V152 教训）；
-- 不硬编码 id，走 IDENTITY 自增。
INSERT INTO t_subject
    (enterprise_id, code, name, parent_id, level, direction, is_leaf, aux_calc_type,
     is_active, remark, account_type, created_by, created_at, updated_by, updated_at, deleted)
SELECT
    e.enterprise_id,
    '2241',
    '其他应付款',
    NULL,
    1,
    'credit',
    true,
    NULL,
    true,
    '工资发放社保个人部分贷方科目（SPEC-P143 补齐）',
    'CURRENT_LIABILITY',
    1,
    NOW(),
    1,
    NOW(),
    0
FROM (
    SELECT DISTINCT enterprise_id FROM t_subject WHERE code = '1122' AND deleted = 0
) e
WHERE NOT EXISTS (
    SELECT 1 FROM t_subject s
    WHERE s.code = '2241' AND s.enterprise_id = e.enterprise_id
);

-- ══════════════ ⑦ 自证不变量 ══════════════
DO $$
DECLARE
    v_menu_missing INTEGER;
    v_grant_count INTEGER;
    v_missing_2241 INTEGER;
BEGIN
    SELECT count(*) INTO v_menu_missing FROM t_menu
    WHERE menu_code IN ('menu-salary','menu-salary-bill','btn-salary-save','btn-salary-confirm','btn-salary-pay')
      AND deleted = 0;
    IF v_menu_missing <> 5 THEN
        RAISE EXCEPTION 'V197: 菜单行缺失（期望 5 行，实际 %）', v_menu_missing;
    END IF;

    SELECT count(*) INTO v_grant_count
    FROM t_role_menu rm JOIN t_menu m ON m.id = rm.menu_id
    WHERE m.permission IN ('salary:list','salary:save','salary:confirm','salary:pay');
    IF v_grant_count < 12 THEN
        RAISE EXCEPTION 'V197: 授权行不足（期望 3 角色 × 4 码 = 12，实际 %）', v_grant_count;
    END IF;

    -- 工资凭证五个科目在有基础科目的企业必须齐（2241 由本迁移 ⑥.5 动态补齐）
    SELECT count(*) INTO v_missing_2241 FROM (
        SELECT e.enterprise_id FROM t_subject e
        WHERE e.code = '1122' AND e.deleted = 0
          AND NOT EXISTS (
            SELECT 1 FROM t_subject s
            WHERE s.code = '2241' AND s.enterprise_id = e.enterprise_id)
    ) x;
    IF v_missing_2241 <> 0 THEN
        RAISE EXCEPTION 'V197: 仍有 % 个企业缺 2241 其他应付款（发放凭证必需）', v_missing_2241;
    END IF;

    RAISE NOTICE 'V197: 工资薪酬 4 表 + 菜单 + 授权 + 自证全部通过';
END $$;
