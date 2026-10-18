-- =====================================================================
-- SPEC-P126：补齐模板企业的科目集，使播种机制能产出完整科目树
--
-- 背景（2026-10-13 实测）：
--   ① SeedDataServiceImpl.cloneTable() 的模板来源硬编码为 `WHERE enterprise_id = 0`，
--      即「企业号 0」是模板企业；
--   ② 实测模板企业只有 21 条科目，而企业 1 有 92 条 —— 模板缺 71 条；
--   ③ 代理端客户企业（2/3/4）一条科目都没有，却带着 seed_data_done = true，
--      导致 GET /api/v1/subjects/tree 返回空，期初建账页的科目下拉为空。
--
-- 本迁移只做一件事：**把模板企业补齐到与企业 1 一致**。
-- 之后新建客户企业走播种机制即可获得完整科目，无需在迁移里硬编码企业 id
-- （硬编码 2/3/4 在全新库/生产上要么无效、要么误伤 —— 迁移不该依赖环境数据）。
--
-- 关键点：必须**重映射 parent_id**。直接复制 parent_id 会让模板科目树的父节点
-- 指向企业 1 的行，克隆到目标企业后父子关系断裂（同一类「引用了别的企业的数据」缺陷）。
--
-- 幂等：ON CONFLICT (code, enterprise_id) DO NOTHING + NOT EXISTS 双重保险。
-- =====================================================================

DO $$
DECLARE
    v_inserted int := 0;
    v_relinked int := 0;
BEGIN
    -- ── ① 插入模板缺失的科目，parent_id 先留空 ──
    INSERT INTO t_subject (code, name, parent_id, level, direction, is_leaf, aux_calc_type,
                           is_active, remark, created_by, created_at, updated_by, updated_at,
                           deleted, enterprise_id, accounting_standard, account_type)
    SELECT s.code, s.name, NULL, s.level, s.direction, s.is_leaf, s.aux_calc_type,
           s.is_active, s.remark, s.created_by, NOW(), s.updated_by, NOW(),
           0, 0, s.accounting_standard, s.account_type
    FROM t_subject s
    WHERE s.enterprise_id = 1
      AND s.deleted = 0
      AND NOT EXISTS (
            SELECT 1 FROM t_subject t
            WHERE t.enterprise_id = 0 AND t.code = s.code
      )
    ON CONFLICT (code, enterprise_id) DO NOTHING;

    GET DIAGNOSTICS v_inserted = ROW_COUNT;

    -- ── ② 回填 parent_id：按 code 在「企业1」与「模板企业0」之间映射父节点 ──
    UPDATE t_subject c
    SET parent_id = p.id
    FROM t_subject s
    JOIN t_subject sp ON sp.id = s.parent_id AND sp.enterprise_id = 1
    JOIN t_subject p  ON p.enterprise_id = 0 AND p.code = sp.code
    WHERE s.enterprise_id = 1
      AND s.deleted = 0
      AND c.enterprise_id = 0
      AND c.code = s.code;

    GET DIAGNOSTICS v_relinked = ROW_COUNT;

    RAISE NOTICE 'V181: 模板企业新增科目 % 条，重连父子关系 % 条', v_inserted, v_relinked;

    -- ── ③ 留证：模板当前规模，以及是否已追平企业 1 ──
    RAISE NOTICE 'V181: 模板(企业0)科目数=% ，企业1科目数=%',
        (SELECT count(*) FROM t_subject WHERE enterprise_id = 0 AND deleted = 0),
        (SELECT count(*) FROM t_subject WHERE enterprise_id = 1 AND deleted = 0);
END $$;