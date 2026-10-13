-- =====================================================================
-- SPEC-P126（续）：为「一条科目都没有」的客户账套补齐科目
--
-- 为什么不用 SeedDataService 走播种：
--   该机制以应用连接（huicai_app，NOSUPERUSER + FORCE RLS）执行
--   `... FROM t_subject WHERE enterprise_id = 0`，而 t_subject 的策略是
--   `enterprise_id = current_setting('app.enterprise_id')`
--   ⇒ 无 GUC 时模板行被 RLS 全部过滤，克隆恒为 0 行且**不报错**。
--   （实测：huicai_app 可见 0 条 / 超管可见 92 条）
--   故播种机制在 RLS 之下从来不可能成功，需另行立项修复。
--
-- 本迁移由 Flyway 以迁移身份执行，**不受 RLS 影响**，一次补齐存量缺口。
--
-- 刻意**不硬编码企业 id**：改用「客户账套 且 当前一条科目都没有」作为筛选条件，
-- 使其在其它环境（含全新库、生产）要么生效、要么自然空转。
--
-- 幂等：ON CONFLICT (code, enterprise_id) DO NOTHING。
-- =====================================================================

DO $$
DECLARE
    v_inserted int := 0;
    v_relinked int := 0;
BEGIN
    -- ── ① 为「零科目客户账套」插入模板科目，parent_id 先留空 ──
    INSERT INTO t_subject (code, name, parent_id, level, direction, is_leaf, aux_calc_type,
                           is_active, remark, created_by, created_at, updated_by, updated_at,
                           deleted, enterprise_id, accounting_standard, account_type)
    SELECT s.code, s.name, NULL, s.level, s.direction, s.is_leaf, s.aux_calc_type,
           s.is_active, s.remark, NULL, NOW(), NULL, NOW(),
           0, e.id, s.accounting_standard, s.account_type
    FROM t_subject s
    CROSS JOIN (
        SELECT t_enterprise.id
        FROM t_enterprise
        WHERE t_enterprise.deleted = 0
          AND t_enterprise.mode = 'AGENCY_CLIENT'
          AND NOT EXISTS (
                SELECT 1 FROM t_subject x
                WHERE x.enterprise_id = t_enterprise.id AND x.deleted = 0
          )
    ) e
    WHERE s.enterprise_id = 0
      AND s.deleted = 0
    ON CONFLICT (code, enterprise_id) DO NOTHING;

    GET DIAGNOSTICS v_inserted = ROW_COUNT;

    -- ── ② 回填 parent_id（按 code 在模板企业与目标企业之间映射）──
    --
    -- ⚠️ 必须用 EXISTS 而非在 FROM 的 JOIN 条件里引用目标表别名：
    --    PostgreSQL 不允许 `UPDATE t SET ... FROM a JOIN b ON ... = t.别名`
    --    （42P01 invalid reference to FROM-clause entry）。
    --    首版即栽在此处，导致 Flyway 失败、**应用起不来**。
    UPDATE t_subject c
    SET parent_id = p.id
    FROM t_subject p
    WHERE c.parent_id IS NULL
      AND c.enterprise_id <> 0
      AND c.deleted = 0
      AND p.enterprise_id = c.enterprise_id
      AND p.deleted = 0
      AND EXISTS (
            SELECT 1
            FROM t_subject s
            WHERE s.enterprise_id = 0
              AND s.deleted = 0
              AND s.code = c.code
              AND s.parent_id IS NOT NULL
              AND (SELECT sc.code FROM t_subject sc
                   WHERE sc.id = s.parent_id AND sc.enterprise_id = 0) = p.code
      );

    GET DIAGNOSTICS v_relinked = ROW_COUNT;

    RAISE NOTICE 'V182: 补齐客户账套科目 % 条，重连父子关系 % 条', v_inserted, v_relinked;

    -- ── ③ 留证：各企业科目数与跨企业父节点数 ──
    RAISE NOTICE 'V182: 各企业科目数 = %',
        (SELECT string_agg(enterprise_id || ':' || cnt, ' ')
         FROM (SELECT enterprise_id, count(*) cnt FROM t_subject
               WHERE deleted = 0 GROUP BY enterprise_id ORDER BY enterprise_id) x);
    RAISE NOTICE 'V182: 跨企业父节点数（应为 0）= %',
        (SELECT count(*) FROM t_subject c JOIN t_subject p ON p.id = c.parent_id
         WHERE c.deleted = 0 AND p.enterprise_id <> c.enterprise_id);
END $$;