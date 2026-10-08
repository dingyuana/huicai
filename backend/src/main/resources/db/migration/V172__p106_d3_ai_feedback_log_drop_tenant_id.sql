-- =====================================================================
-- V172: P106 D-3 补做 —— 删除 t_ai_feedback_log.tenant_id 列
-- 关联：docs/specs/P106-multi-book-account-set.md §0.2 / §11、V170 的同类决策
-- 守卫：AccountSetIsolationRealDBTest#aiFeedbackLogTenantIdColumnMustBeDropped
-- =====================================================================
--
-- 【为什么是「补做」】
-- D-3 裁定（SPEC §10.1）只点名了 `t_classification_rule` 一张表，
-- `t_ai_feedback_log.tenant_id` 因此「暂存」下来成为未决债。
-- 复核结论：这张表的该列与 classification_rule **同属双列并存形态、同处 V105 补
-- enterprise_id 的批次**，而 D-1 已把所有读写路径改为 enterprise_id
-- （`AiFeedbackLogServiceImpl` 分页/汇总/鉴权路径零 tenant_id 引用，
-- `grep` 全仓主代码 main 0 处、test 仅 1 处夹具赋值），
-- 且**索引只建在 (enterprise_id, deleted) 上、没有任何 tenant_id 索引**。
-- ⇒ 「暂存」这一列既无功能收益，又在持续向读写路径发出「这张表仍有两个隔离列」的错误信号。
-- 按 §7.7 的精神，补做这一刀属「清除同一缺陷类在另一张表上的同一形态」，不带宽解释义。
--
-- 【风险与顺序】
-- - 该列可空，无 NOT NULL、无 FK、无索引 ⇒ DROP COLUMN 安全；
-- - Entity 字段 `AiFeedbackLogEntity.tenantId` 必须**同一版本内同步删除**，
--   否则 MP 会把该字段纳入 SELECT/INSERT ⇒ 真库 `column "tenant_id" does not exist`
--   （与 V170 在 classification_rule 上实测的教训一致，SPEC V1.7 已沉淀）；
-- - 无留证型查询需要前置（无索引引用、无统计依赖），可直接删。

ALTER TABLE t_ai_feedback_log DROP COLUMN IF EXISTS tenant_id;

COMMENT ON TABLE t_ai_feedback_log IS
    'AI 反馈日志；P106 D-3 补做起隔离维度唯一为 enterprise_id（tenant_id 列已由 V172 删除）';
