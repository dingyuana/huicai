-- =====================================================================
-- V199: SPEC-P155 工资薪酬二期 —— 单位社保(按比例自动算) + 部门费用分摊 + 缴纳社保
--
-- 用户裁定（2026-11-07）：
--   · R1 单位社保金额来源 = **按比例自动算**（非手填）：新增项目类
--     COMPANY_SOCIAL，险种比例配置表 t_social_insurance_rate 挂在
--     t_salary_item 行上（企业自建养老/医疗/失业/工伤/生育/公积金项目行，
--     每行配 company_rate + 基数上下限）；保存工资单时自动算：
--     基数 = 当期应发 clamp [base_floor, base_cap] × company_rate，
--     明细行落 t_salary_bill_item（计算结果落库留痕，非动态算）。
--     🔴 基数口径 = 当期应发合计，与社保合规申报基数可能不同——登记遗留，
--        缴费基数以社保局申报为准。
--   · R2 费用分摊 = **部门→科目映射表** t_dept_subject_map（非员工直挂）：
--     SALC 分录按员工 dept_id 查映射得费用科目，无映射/无部门回落 6602；
--     同科目金额合并一行。
--   · R3 导出（工资条/个税申报表）：纯读端点，本迁移不含表结构。
--   · R4 员工自助查询：后置（独立批次）。
--
-- 分录口径（V197 两阶段扩展为三动作）：
--   · SALC 计提：借 {费用科目:6602/6601...}(应发+单位社保，按部门分摊分组)
--               / 贷 2211 (应发+单位社保)
--   · SALP 发放：借 2211(应发) / 贷 2221(个税)+2241(个人社保)+1002(实发)
--     —— 2211 贷方余额含单位社保，留待缴纳动作冲销，账理自洽；
--   · SALJ 缴纳（新）：借 2211(单位社保) + 借 2241(个人社保代扣) / 贷 1002；
--     幂等：t_salary_bill.social_paid 单标记，仅可缴纳一次；
--     权限复用 salary:pay（发放与缴纳同为资金付出动作，零权限面扩张）。
--
-- 🔴 RLS 三件套与建表同迁移不分离（V1.108 P0 教训：V197 建 4 表漏 RLS，
--    数据层对跨租户读写裸奔，攻击面探针自证用例才抓到）。
-- ⚠️ 幂等；id 生成 GENERATED ALWAYS AS IDENTITY（V93 标准，SchemaValidator
--    Check 1 拦 BIGSERIAL）；permission 列冒号形态（V196 教训）。
-- =====================================================================

-- ── 1. 项目类别放开 COMPANY_SOCIAL ──
ALTER TABLE t_salary_item DROP CONSTRAINT IF EXISTS chk_salary_item_category;
ALTER TABLE t_salary_item ADD CONSTRAINT chk_salary_item_category
    CHECK (category IN ('EARN','SOCIAL_INS','SPECIAL_ADD','OTHER_DEDUCT','COMPANY_SOCIAL'));

-- ── 2. 险种比例配置（挂在公司社保项目行上）──
CREATE TABLE IF NOT EXISTS t_social_insurance_rate (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    enterprise_id BIGINT NOT NULL,
    item_id BIGINT NOT NULL,                -- → t_salary_item(category=COMPANY_SOCIAL)
    company_rate NUMERIC(8,6) NOT NULL,     -- 如 0.160000 = 16%
    base_floor NUMERIC(14,2),               -- 基数下限（NULL=不设下限）
    base_cap NUMERIC(14,2),                 -- 基数上限（NULL=不设上限）
    remark VARCHAR(500),
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_by BIGINT,
    updated_at TIMESTAMP,
    deleted INT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_social_rate_item
    ON t_social_insurance_rate (item_id) WHERE deleted = 0;

-- ── 3. 部门→费用科目映射 ──
CREATE TABLE IF NOT EXISTS t_dept_subject_map (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    enterprise_id BIGINT NOT NULL,
    dept_id BIGINT NOT NULL,
    subject_id BIGINT NOT NULL,             -- 费用科目（6601/6602/…）
    remark VARCHAR(500),
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_by BIGINT,
    updated_at TIMESTAMP,
    deleted INT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_dept_subject_map
    ON t_dept_subject_map (enterprise_id, dept_id) WHERE deleted = 0;

-- ── 4. 工资单缴纳标记 ──
ALTER TABLE t_salary_bill ADD COLUMN IF NOT EXISTS social_paid BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE t_salary_bill ADD COLUMN IF NOT EXISTS social_voucher_id BIGINT;
ALTER TABLE t_salary_bill ADD COLUMN IF NOT EXISTS social_voucher_no VARCHAR(32);
-- 单位社保合计（confirm 回填，SALJ 缴纳分录取数）
ALTER TABLE t_salary_bill ADD COLUMN IF NOT EXISTS total_company_social NUMERIC(14,2) NOT NULL DEFAULT 0;

-- 员工级快照补单位社保列（SALC 分录分摊按「应发+单位社保」合并，台账须逐员工留痕）
ALTER TABLE t_salary_bill_employee ADD COLUMN IF NOT EXISTS company_social NUMERIC(14,2) NOT NULL DEFAULT 0;

-- ── 4.5 补齐费用科目 6601 销售费用（V197 ⑥.5 家法）══════════════
-- R2 部门分摊借方需要 6601；全部迁移中无人种过该科目（V102_5 只种 6602，
-- 注释里「6601→84」是**假映射**——VALUES 里根本没有 id=84 行，L2 全新库实测
-- subjectId("6601") 查空坐实）。动态覆盖全部有基础科目（1122 标志）的企业；
-- NOT EXISTS 守卫不带 deleted=0（uq_subject_code_ent 非部分唯一，V152 教训）；
-- 不硬编码 id，走 IDENTITY 自增。
INSERT INTO t_subject
    (enterprise_id, code, name, parent_id, level, direction, is_leaf, aux_calc_type,
     is_active, remark, account_type, created_by, created_at, updated_by, updated_at, deleted)
SELECT
    e.enterprise_id,
    '6601',
    '销售费用',
    NULL,
    1,
    'debit',
    true,
    NULL,
    true,
    '工资计提销售部门费用分摊科目（SPEC-P155 R2 补齐）',
    'EXPENSE',
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
    WHERE s.code = '6601' AND s.enterprise_id = e.enterprise_id
);

-- ── 5. RLS 三件套（V198 家法，逐字一致）──
ALTER TABLE t_social_insurance_rate ENABLE ROW LEVEL SECURITY;
ALTER TABLE t_social_insurance_rate FORCE  ROW LEVEL SECURITY;
ALTER TABLE t_dept_subject_map      ENABLE ROW LEVEL SECURITY;
ALTER TABLE t_dept_subject_map      FORCE  ROW LEVEL SECURITY;

DROP POLICY IF EXISTS enterprise_policy ON t_social_insurance_rate;
CREATE POLICY enterprise_policy ON t_social_insurance_rate
    FOR ALL
    USING (
        enterprise_id = (NULLIF(current_setting('app.enterprise_id', true), ''))::bigint
    );

DROP POLICY IF EXISTS enterprise_policy ON t_dept_subject_map;
CREATE POLICY enterprise_policy ON t_dept_subject_map
    FOR ALL
    USING (
        enterprise_id = (NULLIF(current_setting('app.enterprise_id', true), ''))::bigint
    );

-- ── 6. 自证不变量（V198 形态：行为断言必须代入非超管应用身份）──
DO $$
DECLARE
    v_tables text[] := ARRAY['t_social_insurance_rate','t_dept_subject_map'];
    t text;
    v_policies int;
    v_enabled bool;
    v_visible_rows int;
    v_probe_role text := 'huicai_app';
    v_has_probe bool;
BEGIN
    SELECT EXISTS (
        SELECT 1 FROM pg_roles
         WHERE rolname = v_probe_role AND NOT rolsuper AND NOT rolbypassrls
    ) INTO v_has_probe;

    FOREACH t IN ARRAY v_tables LOOP
        SELECT count(*) INTO v_policies FROM pg_policies
         WHERE schemaname = 'public' AND tablename = t AND policyname = 'enterprise_policy';
        IF v_policies <> 1 THEN
            RAISE EXCEPTION 'RLS 自证失败：%.enterprise_policy 策略缺失（% 条）', t, v_policies;
        END IF;
        SELECT relrowsecurity AND relforcerowsecurity INTO v_enabled FROM pg_class
         WHERE relname = t;
        IF NOT v_enabled THEN
            RAISE EXCEPTION 'RLS 自证失败：% 未开启 ENABLE+FORCE ROW LEVEL SECURITY', t;
        END IF;
        IF v_has_probe THEN
            EXECUTE format('SET LOCAL ROLE %I', v_probe_role);
            EXECUTE format('SET LOCAL app.enterprise_id = %L', '999999');
            EXECUTE format('SELECT count(*) FROM %I', t) INTO v_visible_rows;
            IF v_visible_rows <> 0 THEN
                RAISE EXCEPTION 'RLS 自证失败：% 在 GUC=999999（% 身份）下仍可见 % 行 —— 谓词未生效',
                    t, v_probe_role, v_visible_rows;
            END IF;
            EXECUTE 'RESET ROLE';
            EXECUTE 'RESET app.enterprise_id';
        ELSE
            RAISE NOTICE 'RLS 行为断言跳过：角色 % 不存在或为超管（超级用户语义绕过 RLS，断言必假）；策略存在性/启用性已由不变量 1/2 锁死，滤空行为由 L2 真库用例覆盖',
                v_probe_role;
        END IF;
    END LOOP;
END $$;
