-- V158: 预算执行控制落地（REQ-2026-126 / SPC-P16 拓展）
--
-- 背景: BudgetFlowE2ETest 慢测失败, 根因是 t_budget_entry 缺 5 个功能必需列
--       (dept_id/project_id/period_month/control_type/used_amount), 且 t_budget
--       缺 approved_at/approved_by。Entity 全部标 exist=false（幽灵字段）导致:
--       - approve() 对 approvedAt 赋值无效, DB 往返恒 null (E2E L119 失败)
--       - addUsedAmount() 引用不存在的 used_amount 列, 运行必报 SQL 错
--       - checkBudget() 读不到 control_type, switch(null) NPE
--       - executionAnalysis() 读幽灵 usedAmount 恒 0
--
-- 说明:
--   - dept_id 不加 FK: t_dept 当前为空表(无种子), 强 FK 会卡住测试与空库新装。
--     维度合法性由业务端点/前端约束(铁律 #9 的编号关联在业务层保证)。
--   - project_id 同理, 且无 t_project 表, 纯预留列。
--   - control_type CHECK 允许集与 BudgetServiceImpl.checkBudget 的 switch 分支
--     一致: WARN/BLOCK/APPROVE, 默认 WARN。

-- ========== t_budget_entry 补 5 列 ==========
ALTER TABLE t_budget_entry
    ADD COLUMN IF NOT EXISTS dept_id      BIGINT,
    ADD COLUMN IF NOT EXISTS project_id   BIGINT,
    ADD COLUMN IF NOT EXISTS period_month INTEGER,
    ADD COLUMN IF NOT EXISTS control_type VARCHAR(20) NOT NULL DEFAULT 'WARN',
    ADD COLUMN IF NOT EXISTS used_amount  NUMERIC(18,2) NOT NULL DEFAULT 0;

-- control_type 取值约束(与 Service switch 分支一致)
ALTER TABLE t_budget_entry
    DROP CONSTRAINT IF EXISTS chk_budget_entry_control_type;
ALTER TABLE t_budget_entry
    ADD CONSTRAINT chk_budget_entry_control_type
    CHECK (control_type IN ('WARN', 'BLOCK', 'APPROVE'));

-- checkBudget 走 be.subject_id + b.period 联合查找, 补组合索引
CREATE INDEX IF NOT EXISTS idx_budget_entry_subject
    ON t_budget_entry (subject_id, budget_id)
    WHERE deleted = 0;

-- ========== t_budget 补审批审计列(与 t_budget_adjustment 对称) ==========
ALTER TABLE t_budget
    ADD COLUMN IF NOT EXISTS approved_by BIGINT,
    ADD COLUMN IF NOT EXISTS approved_at TIMESTAMP;

-- 回填: 历史已审批/激活的预算补一个审批时间, 避免 approved_at 恒 null 掩盖问题
UPDATE t_budget
   SET approved_at = updated_at
 WHERE approved_at IS NULL
   AND status IN ('APPROVED', 'ACTIVE', 'CLOSED', 'FROZEN');
