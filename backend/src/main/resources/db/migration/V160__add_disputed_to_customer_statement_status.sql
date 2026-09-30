-- ============================================================
-- V160: 对账单状态补 DISPUTED（REQ-2026-134 / P107 D1）
--
-- 缺陷：CustomerStatementServiceImpl.dispute() 设 status="DISPUTED"，而
--       chk_customer_statement_status 允许集为
--       (DRAFT, GENERATED, SENT, CONFIRMED) —— 无 DISPUTED，
--       致「发起差异」端点 100% 抛 chk_customer_statement_status 约束违约。
--
-- 为何补约束而非改代码值：前端 CustomerStatementList.vue:152,160 已为
-- DISPUTED 配置了中文标签「争议中」与 danger 颜色，业务语义早已确定，
-- 缺的是 DB 侧允许值。故本迁移补 CHECK（若反向改代码为 CONFIRMED 等，
-- 会让前端标签失效并丢失「争议中」这一业务状态）。
--
-- 同时兜底 t_customer_statement.status 的其他可能取值审计：
--   代码写入路径共 6 处 —— DRAFT(:78) / SENT(:116) / CONFIRMED(:128) /
--   DISPUTED(:140) / RESOLVED(:195) / CANCELLED(:210)。
--   注意 RESOLVED / CANCELLED 是 OutstandingItemEntity 的状态
--   （t_arap_settlement_outstanding），不是 t_customer_statement 的，
--   故本迁移只补 DISPUTED。
--
-- 幂等：先 DROP 后 ADD CHECK，重复执行结果一致。
-- 迁移后应用角色需重连（CHECK 变更会使旧连接缓存的约束失效）。
-- ============================================================

ALTER TABLE t_customer_statement
    DROP CONSTRAINT IF EXISTS chk_customer_statement_status;

ALTER TABLE t_customer_statement
    ADD CONSTRAINT chk_customer_statement_status
    CHECK (status IN ('DRAFT', 'GENERATED', 'SENT', 'CONFIRMED', 'DISPUTED'));
