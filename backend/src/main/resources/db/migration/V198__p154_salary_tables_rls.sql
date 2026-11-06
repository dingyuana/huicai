-- =====================================================================
-- V198: SPEC-P154 切片一 —— 补齐 RLS：工资薪酬 4 表（V197 遗漏）
--
-- 【背景】多租户攻击面负向探针（TenantIsolationAttackSurfaceRealDBTest）
-- 首跑自证用例抓出：V197 建的 t_salary_bill / t_salary_item /
-- t_salary_bill_item / t_salary_bill_employee 四表 **均未开启 RLS**
-- （生产 pg_class.relrowsecurity = f，pg_policies 零行）——
-- 工资数据（含个税快照）在数据层对跨租户读写完全裸奔，
-- 仅靠功能层权限码（V197 授 3 角色）掩盖：正常用户撞不上，
-- 任何越权/恶意请求（如换 id 直读别家工资单详情）直接穿透。
--
-- 【为何此前全绿】功能权限（salary:* 仅 3 角色持有）+ 前端不传他租户 id
-- ⇒ 正常路径无暴露面；探针的自证前提用例（pg_policies 动态断言）是
-- 第一道能照见「策略缺失」的检查 —— v4.0 §8：隔离失败多始于一个缺失的
-- tenant_id 兜底，且被正常功能测试掩盖。
--
-- 【前提已实测】BaseEntity.enterpriseId 带 fill=INSERT（MyMetaObjectHandler
-- 无条件覆盖为上下文企业，不产生 NULL）；生产 4 表 enterprise_id NULL 行 = 0。
--
-- 【谓词与既有策略逐字一致】V173 家法：NULLIF(...,'') 硬化（V167 教训，
-- 防「曾 SET LOCAL 的会话」空串 ::bigint 抛 invalid input syntax）。
--
-- 【幂等】DROP POLICY IF EXISTS + CREATE POLICY，可重跑。
-- 【自证】DO 块：4 表策略齐 + relrowsecurity 全真 + 模拟跨租户 GUC 查询滤空。
-- =====================================================================

-- ── 1. 开启并强制 RLS（FORCE：连表属主也受策略约束）──
ALTER TABLE t_salary_bill           ENABLE ROW LEVEL SECURITY;
ALTER TABLE t_salary_bill           FORCE  ROW LEVEL SECURITY;
ALTER TABLE t_salary_item           ENABLE ROW LEVEL SECURITY;
ALTER TABLE t_salary_item           FORCE  ROW LEVEL SECURITY;
ALTER TABLE t_salary_bill_item      ENABLE ROW LEVEL SECURITY;
ALTER TABLE t_salary_bill_item      FORCE  ROW LEVEL SECURITY;
ALTER TABLE t_salary_bill_employee  ENABLE ROW LEVEL SECURITY;
ALTER TABLE t_salary_bill_employee  FORCE  ROW LEVEL SECURITY;

-- ── 2. 企业隔离策略（与 t_subject / t_bank_reconciliation_log 等逐字一致）──
DROP POLICY IF EXISTS enterprise_policy ON t_salary_bill;
CREATE POLICY enterprise_policy ON t_salary_bill
    FOR ALL
    USING (
        enterprise_id = (NULLIF(current_setting('app.enterprise_id', true), ''))::bigint
    );

DROP POLICY IF EXISTS enterprise_policy ON t_salary_item;
CREATE POLICY enterprise_policy ON t_salary_item
    FOR ALL
    USING (
        enterprise_id = (NULLIF(current_setting('app.enterprise_id', true), ''))::bigint
    );

DROP POLICY IF EXISTS enterprise_policy ON t_salary_bill_item;
CREATE POLICY enterprise_policy ON t_salary_bill_item
    FOR ALL
    USING (
        enterprise_id = (NULLIF(current_setting('app.enterprise_id', true), ''))::bigint
    );

DROP POLICY IF EXISTS enterprise_policy ON t_salary_bill_employee;
CREATE POLICY enterprise_policy ON t_salary_bill_employee
    FOR ALL
    USING (
        enterprise_id = (NULLIF(current_setting('app.enterprise_id', true), ''))::bigint
    );

-- ── 3. 自证不变量（策略缺失回归 = 迁移失败，禁止静默）──
DO $$
DECLARE
    v_tables text[] := ARRAY['t_salary_bill','t_salary_item','t_salary_bill_item','t_salary_bill_employee'];
    t text;
    v_policies int;
    v_enabled bool;
    v_visible_rows int;
BEGIN
    FOREACH t IN ARRAY v_tables LOOP
        -- 不变量 1：策略存在
        SELECT count(*) INTO v_policies FROM pg_policies
         WHERE schemaname = 'public' AND tablename = t AND policyname = 'enterprise_policy';
        IF v_policies <> 1 THEN
            RAISE EXCEPTION 'RLS 自证失败：%.enterprise_policy 策略缺失（% 条）', t, v_policies;
        END IF;
        -- 不变量 2：RLS 已启用且 FORCE
        SELECT relrowsecurity AND relforcerowsecurity INTO v_enabled FROM pg_class
         WHERE relname = t;
        IF NOT v_enabled THEN
            RAISE EXCEPTION 'RLS 自证失败：% 未开启 ENABLE+FORCE ROW LEVEL SECURITY', t;
        END IF;
        -- 不变量 3：跨租户 GUC 查询必须滤空（模拟攻击者 GUC=999999 扫全表 → 0 行）
        EXECUTE format('SET LOCAL app.enterprise_id = ''999999''');
        EXECUTE format('SELECT count(*) FROM %I', t) INTO v_visible_rows;
        IF v_visible_rows <> 0 THEN
            RAISE EXCEPTION 'RLS 自证失败：% 在 GUC=999999 下仍可见 % 行 —— 谓词未生效', t, v_visible_rows;
        END IF;
        EXECUTE format('RESET app.enterprise_id');
    END LOOP;
END $$;
