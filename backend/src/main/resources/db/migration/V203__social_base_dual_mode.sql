-- ═══════════════════════════════════════════════════════════════════
-- V203：SPEC-P159——单位社保缴费基数双模式（V1.118 遗留收口，用户 2026-11-09 批准）
--   现状：基数恒为「当期应发合计 clamp 上下限」，与社保局核定基数可能不符（合规口径遗留）。
--   ① t_social_insurance_rate +base_mode：GROSS=当期应发（默认，存量行不下改）/
--      EMP_DECL=按员工社保核定基数（t_employee.social_base_declared）
--   ② t_employee +social_base_declared：员工级年度核定基数（社保局按人核定）
-- RLS：两表均已 ENABLE+FORCE（V198/V173 体系），加列不破坏——新表迁移 checklist 已锁。
-- ═══════════════════════════════════════════════════════════════════

-- ── 1. 比例配置加基数模式（NULL 视同 GROSS，存量子数据零漂移）──
ALTER TABLE t_social_insurance_rate
    ADD COLUMN IF NOT EXISTS base_mode VARCHAR(16) NOT NULL DEFAULT 'GROSS';
COMMENT ON COLUMN t_social_insurance_rate.base_mode IS
    'SPEC-P159 基数口径：GROSS=当期应发合计（默认）| EMP_DECL=员工社保核定基数';
ALTER TABLE t_social_insurance_rate DROP CONSTRAINT IF EXISTS chk_social_rate_base_mode;
ALTER TABLE t_social_insurance_rate ADD CONSTRAINT chk_social_rate_base_mode
    CHECK (base_mode IN ('GROSS', 'EMP_DECL'));

-- ── 2. 员工核定基数列（NULL=未核定；EMP_DECL 模式未核定员工 fail-loud 拒存）──
ALTER TABLE t_employee
    ADD COLUMN IF NOT EXISTS social_base_declared NUMERIC(12, 2);
COMMENT ON COLUMN t_employee.social_base_declared IS
    'SPEC-P159 社保局核定缴费基数（年度申报值），NULL=未核定';

-- ── 3. 自证不变量 ──
DO $$
DECLARE
    v_missing INT;
BEGIN
    SELECT count(*) INTO v_missing FROM information_schema.columns
    WHERE table_name = 't_social_insurance_rate' AND column_name = 'base_mode';
    IF v_missing = 0 THEN
        RAISE EXCEPTION 'V203 自证失败：t_social_insurance_rate.base_mode 缺失';
    END IF;

    SELECT count(*) INTO v_missing FROM information_schema.columns
    WHERE table_name = 't_employee' AND column_name = 'social_base_declared';
    IF v_missing = 0 THEN
        RAISE EXCEPTION 'V203 自证失败：t_employee.social_base_declared 缺失';
    END IF;

    -- 存量行零漂移：默认 GROSS
    IF EXISTS (SELECT 1 FROM t_social_insurance_rate WHERE base_mode IS DISTINCT FROM 'GROSS') THEN
        RAISE EXCEPTION 'V203 自证失败：存量行 base_mode 非默认 GROSS';
    END IF;

    RAISE NOTICE 'V203 自证通过：base_mode 默认 GROSS + 员工核定基数列在位，存量零漂移';
END $$;
