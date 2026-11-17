-- ═══════════════════════════════════════════════════════════════════
-- SPEC-P156：P1-1 税务申报收尾（REQ-2026-162）
--   ① t_tax_burden_config 税负预警阈值（D-3：每企业每税种一行，默认 3%）
--   ② RLS 三件套同迁移不分离（V1.108 P0 家法：新表迁移 checklist 必含）
--   ③ 权限面零扩张：阈值写端点复用 tax:output:list（申报 CRUD 同码）
-- ①申报表导出 ②企业所得税预缴为纯 Java/查询实现，无 schema 变更。
-- ═════════════════════════════════════════缺省 3% 为系统默认，企业未配置时生效═══

-- ── 1. 税负预警阈值配置表（D-3）──
CREATE TABLE IF NOT EXISTS t_tax_burden_config (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    enterprise_id BIGINT NOT NULL,
    tax_type VARCHAR(50) NOT NULL DEFAULT 'VAT',   -- 税种（VAT=增值税，扩展位）
    threshold_rate NUMERIC(8,6) NOT NULL,          -- 预警阈值，如 0.030000 = 3%
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    remark VARCHAR(500),
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_by BIGINT,
    updated_at TIMESTAMP,
    deleted INT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_tax_burden_config_ent_type
    ON t_tax_burden_config (enterprise_id, tax_type) WHERE deleted = 0;

-- ── 2. RLS 三件套（V198/V199 家法逐字）──
ALTER TABLE t_tax_burden_config ENABLE ROW LEVEL SECURITY;
ALTER TABLE t_tax_burden_config FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS enterprise_policy ON t_tax_burden_config;
CREATE POLICY enterprise_policy ON t_tax_burden_config
    FOR ALL
    USING (
        enterprise_id = (NULLIF(current_setting('app.enterprise_id', true), ''))::bigint
    );

-- ── 3. 自证不变量 ──
DO $$
DECLARE
    v_rls_missing INT;
BEGIN
    -- 不变量 1：RLS 必须启用且强制
    SELECT count(*) INTO v_rls_missing
    FROM pg_class
    WHERE relname = 't_tax_burden_config'
      AND (relrowsecurity = false OR relforcerowsecurity = false);
    IF v_rls_missing > 0 THEN
        RAISE EXCEPTION 'P156 自证失败：t_tax_burden_config RLS 未启用/未强制';
    END IF;

    -- 不变量 2：策略必须存在
    IF NOT EXISTS (
        SELECT 1 FROM pg_policies
        WHERE tablename = 't_tax_burden_config' AND policyname = 'enterprise_policy'
    ) THEN
        RAISE EXCEPTION 'P156 自证失败：t_tax_burden_config enterprise_policy 缺失';
    END IF;

    RAISE NOTICE 'P156 自证通过：t_tax_burden_config RLS 启用+强制+策略在位';
END $$;
