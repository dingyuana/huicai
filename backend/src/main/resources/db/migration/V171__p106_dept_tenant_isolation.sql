-- =====================================================================
-- V171: P106 批次 1b' —— t_dept 按企业隔离（加列 + 唯一约束分段 + RLS）
-- 关联：docs/specs/P106-multi-book-account-set.md §0.1.1 第 7 行（D-2b 裁定「可排期」）、
--       §0.1.4 的同类判据（「豁免理由成立 ≠ 不需要隔离」的另一面）
-- 守卫：DeptTenantIsolationRealDBTest（5 例）
-- =====================================================================
--
-- 【原缺陷（三层防线全空）】
-- ① t_dept 无 enterprise_id、无 tenant_id、无 RLS；
-- ② 在 EnterpriseDataPermissionInterceptor.SHARED_TABLES 内 ⇒ 应用层不注入企业条件；
-- ③ uq_dept_code 是**全局唯一** ⇒ 两个企业不可能有同名部门编码（核心危害）。
-- 实测：DeptServiceImpl 的 tree/get/create/update/delete 五个方法均不带企业维度。
--
-- 【为什么现在可以改】
-- D-2a（SPEC V1.8）已把 t_dept 定性为「企业内主数据 + 真隔离缺口」，
-- 老丁裁定 D-2b 明确「t_dept 可排期」。本迁移即该排期的落地。
--
-- 【关于 NOT NULL DEFAULT 1 的风险 —— 必须先读】
-- 本仓 V102~V105 给业务表补列时统一用 `enterprise_id BIGINT NOT NULL DEFAULT 1`。
-- 对 t_dept 这样**存量可能为 0 行**的表，该写法会把历史行归到企业 1。
-- 本迁移**先做归因查询**：若存量非 0，RAISE NOTICE 会打出真实行数；
-- 实测 V1 baseline 未种任何 t_dept 数据（`rg 'INSERT INTO t_dept'` 仅命中建表语句），
-- 故默认不存在误归属风险。若将来在别的库上跑本迁移且存量非 0，
-- 应先按业务归属回填再收紧约束 —— 故此处用 IF NOT EXISTS 保持可重跑。

-- ---------- ① 加隔离列 ----------
ALTER TABLE t_dept ADD COLUMN IF NOT EXISTS enterprise_id BIGINT NOT NULL DEFAULT 1;

COMMENT ON COLUMN t_dept.enterprise_id IS
    'P106 批次 1b''：租户隔离列，与其余业务表同口径（RLS 谓词读它）';

-- ---------- ② 唯一约束按企业分段 ----------
-- 原 uq_dept_code UNIQUE(dept_code) 是全局唯一 ⇒ 两个企业无法有同名部门。
-- 改为 (dept_code, enterprise_id) 后：跨企业可同名，同企业内仍唯一（守卫第 ② 条锁住后者）。
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'public.t_dept'::regclass AND conname = 'uq_dept_code'
    ) THEN
        ALTER TABLE t_dept DROP CONSTRAINT uq_dept_code;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'public.t_dept'::regclass AND conname = 'uq_dept_code_ent'
    ) THEN
        ALTER TABLE t_dept
            ADD CONSTRAINT uq_dept_code_ent UNIQUE (dept_code, enterprise_id);
    END IF;
END $$;

COMMENT ON CONSTRAINT uq_dept_code_ent ON t_dept IS
    'P106 批次 1b''：原 uq_dept_code 为全局唯一，导致两个企业不可能有同名部门编码；现按企业分段';

-- ---------- ③ 留证：存量行数与归属 ----------
-- 必须放在改约束之前，且只读不阻塞：告诉运维「历史行是否会被默认归到企业 1」。
DO $$
DECLARE
    total_rows BIGINT;
BEGIN
    EXECUTE 'SELECT count(*) FROM t_dept' INTO total_rows;
    RAISE NOTICE 'V171: t_dept 存量行数 = %（>0 时历史行已按 DEFAULT 1 归到企业 1，需人工复核归属）', total_rows;
END $$;

-- ---------- ④ 第三层防线：RLS ----------
-- 谓词沿用 V167 硬化后的口径（NULLIF(..., '')::bigint）：
-- 「曾执行过 SET LOCAL 的事务」结束后 current_setting 读回空串，
-- 直接 ::bigint 会抛 invalid input syntax，把 fail-closed 变成 fail-500（§4.5 第 28 条）。
ALTER TABLE t_dept ENABLE ROW LEVEL SECURITY;
ALTER TABLE t_dept FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS enterprise_policy ON t_dept;
CREATE POLICY enterprise_policy ON t_dept
    USING (enterprise_id = NULLIF(current_setting('app.enterprise_id', true), '')::bigint);

COMMENT ON TABLE t_dept IS
    '部门表；P106 批次 1b'' 起按 enterprise_id 隔离（unique 已按企业分段），t_dept 不再是全局共享表';

-- ⚠️ 本迁移**不做**的事：改 DeptServiceImpl / DeptEntity —— 那是应用层改动，
--    与 DDL 分开提交，便于归因与回滚（若应用层出问题可单独回退，不必回退 DDL）。