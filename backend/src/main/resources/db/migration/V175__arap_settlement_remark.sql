-- =============================================================================
-- V175: 核销单补 remark 列（P102 出参面批次 15 实施中查出的功能缺口）
-- =============================================================================
-- 【缺陷背景】（实测，非推演）
-- 出参面 Entity 直出 VO 化时逐字段核对，发现：
--   `t_arap_settlement` 实测 19 列（information_schema）里**没有 remark**，
--   而前端 `frontend/src/api/modules/arapSettlement.ts#ArapSettlement` 声明了 `remark`，
--   且 `views/arap/reconciliation/SettlementPanel.vue` **两处真的在用**：
--     :88   新建表单的「备注」输入框  v-model="createForm.remark"
--     :115  详情页的「备注」展示位    {{ settlementDetail.remark || '-' }}
--   ⇒ 提交进去的备注**无处落库**、详情页**永远显示 '-'**。
--
-- 【为什么按 AGENTS §4.5 第 39 条要补列而不是删契约】
-- 那条沉淀的判据是：「改 TS 之前先 grep 页面有没有真的读它。
--   **读了 ⇒ 必须接上真实数据**，没读 ⇒ 才可删」。
-- 本例页面**读了**（且是写入 + 展示双向）⇒ 删契约只会把「恒为空的备注」
-- 伪装成「用户没填备注」，比空白更隐蔽。
--
-- 【处置】
-- ADD COLUMN 而非改表结构：可空、无默认值 ⇒ 对既有行与既有代码零影响，
-- 不需要回填历史数据，也不会让任何 INSERT 失败。
-- 对齐同族表的做法：t_business_doc / t_prepayment 等均已有 remark 列。
--
-- 【幂等】IF NOT EXISTS，重复执行结果相同。

ALTER TABLE t_arap_settlement
    ADD COLUMN IF NOT EXISTS remark VARCHAR(500);

COMMENT ON COLUMN t_arap_settlement.remark IS '备注（SettlementPanel.vue 新建输入 + 详情展示）';