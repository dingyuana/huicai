-- =====================================================================
-- V170: P106 批次 3（D-3）—— 删除 t_classification_rule.tenant_id 列与旧索引
-- 关联：docs/specs/P106-multi-book-account-set.md §10.1 D-3（老丁裁定：随 D-1 联动删除）
--       docs/dir/README.md（DIR-004 已闭环，与本迁移无关）
-- =====================================================================
--
-- 【为什么现在可以删 —— D-3 的前提条件已在批次 1a-3（D-1）达成】
-- D-3 原裁定写「等 D-1 定案后再定」。D-1 已实施完成（SPEC V1.6），实测结果：
--   ① ClassificationRuleServiceImpl#page  按 enterprise_id 过滤（不再按 tenant_id）
--   ② #seedForNewTenant(tenantId) 已改名 #seedForCurrentEnterprise()，按上下文企业
--   ③ #create 的 tenant_id 兜底已从硬编码 1L 改为上下文企业
--   ④ **#match() 也已改** —— 它原按 tenant_id 过滤，是 §0.4 原清单漏掉的第 7 处
-- ⇒ 全部读写路径对 tenant_id 的依赖为零，删列不会造成任何功能缺失。
--
-- 【为什么必须用 IF EXISTS —— 非破坏性写法】
-- §7.7「破坏性操作确认」与 §4.5 第 19 条的同类教训：迁移要能在
-- 「已删过」与「还没删」的库上都安全跑通（开发库可能停在旧版本，见 §4.5 第 23 条），
-- 且不得因为列/索引已不存在而中断整个迁移链。
--
-- 【数据前置检查：不阻塞迁移，只留证】
-- 历史行里 tenant_id 与 enterprise_id 可能不一致（D-1 之前允许两列各写各的）。
-- 删列即以 enterprise_id 为唯一真相 —— 这与 V104 起 RLS 谓词、企业索引、
-- 以及 D-1 之后全部读路径的口径完全一致，故无需人工修数。
-- 下方查询仅用于把「删列前到底有多少行两列不一致」记录进 Flyway 历史，避免
-- 这一事实随删列一并消失、日后无从追溯。

-- ---------- ① 留证：删列前两列不一致的行数（必须在删列之前执行）----------
-- ⚠️ 这段统计必须在**删列之前**跑；放在删列之后会因为列不存在而失败。
--    （首版误放在 ② 之后，Flyway 直接报 `column "tenant_id" does not exist` ⇒ 迁移失败。
--      DDL 的执行顺序对「留证型查询」是硬约束，不是风格问题。）
DO $$
DECLARE
    inconsistent_rows BIGINT;
BEGIN
    EXECUTE 'SELECT count(*) FROM t_classification_rule WHERE tenant_id IS DISTINCT FROM enterprise_id'
        INTO inconsistent_rows;
    RAISE NOTICE 'V170（P106 D-3）: 删列前 t_classification_rule 中 tenant_id <> enterprise_id 的行数 = %'
        , inconsistent_rows;
    RAISE NOTICE 'V170: 这些行现以 enterprise_id 为唯一隔离真相（与 V104 起 RLS 谓词、V168 企业索引一致）';
END $$;

-- ---------- ② 删除建在旧列上的索引 ----------
DROP INDEX IF EXISTS idx_classification_rule_tenant;

-- ---------- ③ 删除双列并存中的旧隔离列 ----------
ALTER TABLE t_classification_rule DROP COLUMN IF EXISTS tenant_id;

-- ---------- ④ 标注保留的 enterprise_id 索引为唯一隔离依据 ----------
COMMENT ON INDEX idx_t_classification_rule_enterprise IS
    'P106 D-3 后：enterprise_id 是 t_classification_rule 的唯一隔离列（本索引为其单列索引）';
COMMENT ON INDEX idx_t_classification_rule_enterprise_deleted IS
    'P106 / AT-106-3：按企业 + 逻辑删除过滤分类规则；V104 的 idx_t_classification_rule_enterprise 为单列索引';

COMMENT ON TABLE t_classification_rule IS
    '分类规则表；P106 D-3 起隔离维度唯一为 enterprise_id（原 tenant_id 列已由 V170 删除）';

-- ⚠️ 本迁移**不删** t_ai_feedback_log.tenant_id：它同属 §0.2 双列并存表，但
--    不在 D-3 裁定范围内（裁定只点名 t_classification_rule）。按铁律 #7
--    「三方对照」与 §7.7「破坏性操作需确认」，不得顺手扩大 DDL 范围。
--    该列已无任何读写路径（D-1 已清零），删除需另行决策。