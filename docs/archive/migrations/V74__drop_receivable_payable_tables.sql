-- V74__drop_receivable_payable_tables.sql
-- ⚠️ 破坏性操作，不可回滚
-- 执行条件：V73 数据迁移已验证通过，所有业务功能正常
-- 执行前请确认：
--   1. SELECT COUNT(*) FROM t_business_doc WHERE doc_type IN ('INVOICE_OUT','INVOICE_IN') 与源表行数一致
--   2. 核销结算功能正常（ArapSettlement）
--   3. 对账推荐功能正常（Reconciliation）
--   4. 编号追溯功能正常（NumberingTrace）

DROP TABLE IF EXISTS t_receivable CASCADE;
DROP TABLE IF EXISTS t_payable CASCADE;