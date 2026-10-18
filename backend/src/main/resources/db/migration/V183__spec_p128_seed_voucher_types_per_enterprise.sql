-- SPEC-P128：为每个企业补齐凭证类型，并清理 RLS 下永不可见的「平台级」死行
--
-- 背景：t_voucher_type 是租户表（FORCE RLS，谓词 enterprise_id = app.enterprise_id），
--       但历史上只有 enterprise_id = 1 真正种了 4 条；V116 曾把同样的 4 条复制到
--       enterprise_id = 0，意图做「平台级默认」，可 RLS 谓词永远不等于 0
--       ⇒ 那 4 行在任何上下文都读不到，是天生的死数据。
--
-- 后果：非企业 1 的账套制证时，代码用硬编码 id（1~4）去 getById，被 RLS 过滤返 null
--       ⇒ 抛「凭证类型不存在」。本迁移只补数据，代码侧改造见 SPEC-P128 §1②。
--
-- ⚠️ 必须显式写 enterprise_id：本表该列是 NOT NULL DEFAULT 1，漏写会静默落进企业 1
--    （AGENTS §4.5 第 34 条同型）。
--
-- ⚠️ 执行顺序：INSERT 的数据源是 enterprise_id = 1 的行，UPDATE 只动 enterprise_id = 0 的行，
--    两者互不依赖；但仍保持「先补后删」以便留证时先看得到补了什么。

-- 1) 为每个未删除企业补齐 4 种凭证类型（模板取自企业 1，保留 numbering_rule 等属性）
INSERT INTO t_voucher_type
    (enterprise_id, code, name, sort_order, numbering_rule, is_active, remark,
     created_by, created_at, updated_by, updated_at, deleted)
SELECT e.id, vt.code, vt.name, vt.sort_order, vt.numbering_rule, vt.is_active, vt.remark,
       1, NOW(), 1, NOW(), 0
FROM t_enterprise e
CROSS JOIN t_voucher_type vt
WHERE vt.enterprise_id = 1
  AND vt.deleted = 0
  AND e.deleted = 0
ON CONFLICT (code, enterprise_id) DO NOTHING;

-- 2) enterprise_id = 0 的行逻辑删除（铁律 #12：禁止物理删除）
--    留证：删前先记录其分布，便于事后追溯这批死数据的规模
DO $$
DECLARE
    v_dead_rows INTEGER;
BEGIN
    SELECT count(*) INTO v_dead_rows
    FROM t_voucher_type WHERE enterprise_id = 0 AND deleted = 0;

    IF v_dead_rows > 0 THEN
        RAISE NOTICE 'V183: 清理 enterprise_id=0 的平台级死凭证类型 % 条'
            , v_dead_rows;
    END IF;
END $$;

UPDATE t_voucher_type
SET deleted = 1, updated_at = NOW()
WHERE enterprise_id = 0 AND deleted = 0;
