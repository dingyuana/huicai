-- ============================================================
-- V168: P106 批次 1a-5 —— 为双列并存表补「按企业维度」的复合索引
--
-- 关联：REQ-2026-133 / SPC-P106 §0.2 / §7
-- 前置：1a-1 ~ 1a-4 已把写入路径收敛到 enterprise_id（企业上下文）
--
-- 背景（实测，非推演）：
--   SPEC 原计划写「把 idx_classification_rule_tenant 从 tenant_id 迁到 enterprise_id」，
--   但真实库核对发现该假设不成立 —— enterprise_id 的索引**早已存在**：
--     t_classification_rule: idx_t_classification_rule_enterprise ON (enterprise_id)   [V104]
--     t_prepayment:          idx_t_prepayment_enterprise          ON (enterprise_id)   [V105]
--     t_reconciliation_log:  idx_t_reconciliation_log_enterprise  ON (enterprise_id)   [V105]
--   故本次不是「迁移」，而是补两处真实缺口：
--     ① t_ai_feedback_log **完全没有** enterprise_id 索引（只有主键）
--     ② t_classification_rule 的 enterprise_id 索引是单列，而实际查询同时过滤
--        enterprise_id 与 deleted，单列索引在 deleted 过滤上无法收敛
--
-- 为什么不在本迁移删除 idx_classification_rule_tenant：
--   ① 删索引属破坏性 DDL，按 AGENTS §7 需老丁确认；
--   ② t_classification_rule.tenant_id 列本身是否废弃，取决于批次 1a-3 的
--      「tenantId 请求参数去留」决策（尚未做），提前删索引会抢先替业务方决策。
--   ⇒ 本迁移只做幂等新增，旧索引保留并在注释中标注为待清理。
-- ============================================================

-- ① t_ai_feedback_log：补企业维度复合索引（原先只有主键索引）
CREATE INDEX IF NOT EXISTS idx_t_ai_feedback_log_enterprise_deleted
    ON t_ai_feedback_log (enterprise_id, deleted);

COMMENT ON INDEX idx_t_ai_feedback_log_enterprise_deleted IS
    'P106 / AT-106-3：按企业 + 逻辑删除过滤 AI 反馈日志；V104 只补了列未补索引';

-- ② t_classification_rule：补 (enterprise_id, deleted) 复合索引
--    单列 enterprise_id 索引已由 V104 建立，此处补 deleted 以匹配实际查询形状
CREATE INDEX IF NOT EXISTS idx_t_classification_rule_enterprise_deleted
    ON t_classification_rule (enterprise_id, deleted);

COMMENT ON INDEX idx_t_classification_rule_enterprise_deleted IS
    'P106 / AT-106-3：按企业 + 逻辑删除过滤分类规则；V104 的 idx_t_classification_rule_enterprise 为单列索引';

-- ③ 标注旧索引为待清理（仅注释，不删）
COMMENT ON INDEX idx_classification_rule_tenant IS
    '【待清理 / P106 批次 1a-3 待决策】建在已废弃的 tenant_id 列上；是否删除取决于 tenantId 请求参数的去留决策';