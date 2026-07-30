-- ============================================================
-- V127: 为继承 BaseEntity 但缺少 updated_at 列的表添加该列
--
-- 背景：BaseEntity 定义了 updatedAt 字段，标注了
-- @TableField(fill = FieldFill.INSERT_UPDATE)，因此所有继承
-- BaseEntity 的 Entity 都会在 INSERT/UPDATE 时尝试写入
-- updated_at 列。19 张表缺少该列，导致 MyBatis-Plus 自动
-- 填充时报错 "column "updated_at" does not exist"。
--
-- 首次触发：ReconciliationServiceImpl.execute() 执行
-- logMapper.insert(reconLog) 时发现 t_reconciliation_log
-- 缺少 updated_at。
-- ============================================================

-- 为所有缺少 updated_at 的 BaseEntity 继承表添加该列
-- 使用 TIMESTAMP(6) 与 BaseEntity 的 LocalDateTime 类型匹配
-- 允许 NULL 以兼容表中已有数据

ALTER TABLE t_aging_alert ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP(6);
ALTER TABLE t_ai_anomaly_tag ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP(6);
ALTER TABLE t_ai_feedback_log ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP(6);
ALTER TABLE t_ai_task ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP(6);
ALTER TABLE t_arap_settlement_entry ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP(6);
ALTER TABLE t_asset_change ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP(6);
ALTER TABLE t_asset_depreciation ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP(6);
ALTER TABLE t_attachment ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP(6);
ALTER TABLE t_bad_debt_provision_detail ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP(6);
ALTER TABLE t_bad_debt_provision_scheme_item ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP(6);
ALTER TABLE t_bank_journal ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP(6);
ALTER TABLE t_budget_adjustment ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP(6);
ALTER TABLE t_budget_entry ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP(6);
ALTER TABLE t_business_doc_entry ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP(6);
ALTER TABLE t_financial_metric ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP(6);
ALTER TABLE t_reconciliation_exception ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP(6);
ALTER TABLE t_reconciliation_log ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP(6);
ALTER TABLE t_ticket_transaction ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP(6);
ALTER TABLE t_voucher_template_line ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP(6);