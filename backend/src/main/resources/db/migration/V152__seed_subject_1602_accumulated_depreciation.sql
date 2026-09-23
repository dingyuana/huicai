-- ============================================================
-- V152: 补充 seed 1602 累计折旧科目（P85-C 折旧自动制证依赖）
--
-- 背景：V102_5 预置了基础科目但只 seed 了 1601 固定资产，漏了 1602 累计折旧。
-- 导致全新数据库（Testcontainers 从零跑 Flyway）缺 1602 —— P85-C 折旧制证的
-- 贷方科目查不到，DepreciationVoucherService 直接报"未配置科目 1602"。
-- dev 库里 1602 存在是人工补建的，不能依赖。
--
-- 1602 是会计基本科目（与 1601 固定资产配套），缺失会导致：
--   · 折旧制证完全不可用（贷方无科目可挂）
--   · 资产减值/处置模块若引用累计折旧同样受影响
--
-- 幂等：t_subject 主键 id 无唯一业务语义（dev 库 1602 的 id=29 是人工插入的任意值），
--       故不硬编码 id，动态取 MAX(id)+1；唯一约束 uq_subject_code_ent(code, enterprise_id)
--       配合 NOT EXISTS 守卫保证重跑安全（已实测：首次 INSERT 1 行，重跑 INSERT 0 行）。
--
-- 注意：不能用 `INSERT ... VALUES (SELECT MAX(id)... ) WHERE NOT EXISTS(...)` 形式——
--       PostgreSQL 的 VALUES 不允许后跟 WHERE，会直接语法错误。必须用 CTE + SELECT 形式。
-- ============================================================

WITH nextid AS (
    SELECT COALESCE(MAX(id), 0) + 1 AS nid FROM t_subject
)
-- t_subject.id 是 GENERATED ALWAYS AS IDENTITY，显式插 id 必须带
-- OVERRIDING SYSTEM VALUE（同 V102_5 的写法），否则报
-- "cannot insert a non-DEFAULT value into column \"id\""。
INSERT INTO t_subject
    (id, enterprise_id, code, name, parent_id, level, direction,
     is_leaf, aux_calc_type, is_active, remark,
     created_by, created_at, updated_by, updated_at, deleted)
OVERRIDING SYSTEM VALUE
SELECT
    nextid.nid,
    1, '1602', '累计折旧', NULL, 1, 'credit',
    true, NULL, true, '累计折旧（P85-C 折旧制证贷方科目）',
    1, NOW(), 1, NOW(), 0
FROM nextid
WHERE NOT EXISTS (
    -- 注意：NOT EXISTS 不能加 deleted = 0 条件。
    -- uq_subject_code_ent(code, enterprise_id) 是非部分唯一索引（不含 deleted 过滤），
    -- 若存在一条已逻辑删除的 1602 行，带 deleted=0 的守卫会放行插入 → 撞唯一约束。
    SELECT 1 FROM t_subject
    WHERE code = '1602' AND enterprise_id = 1
);
