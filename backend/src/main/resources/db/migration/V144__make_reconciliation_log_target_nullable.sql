-- ============================================================
-- V144: t_reconciliation_log.target_doc_id/target_doc_type 改为可空
--
-- 背景：
--   ArapSettlementServiceImpl.logReconciliationLog() 写入核销单
--   生命周期日志(SUBMIT/APPROVE/REJECT/CANCEL/GENERATE_VOUCHER/
--   REVERSE)时硬编码 target_doc_id = null——这类日志描述的是
--   核销单自身的状态流转，并无目标单据。
--   但 V94 建表时 target_doc_id/target_doc_type 为 NOT NULL，
--   导致每次 INSERT 违反约束 → PostgreSQL 事务 aborted →
--   COMMIT 被静默转为 ROLLBACK → 审批/提报/制证等操作全部
--   静默回滚(API 返回 200 但数据未变)。
--   (2026-09-10 实测: approve 核销单4 后端日志报
--    "null value in column target_doc_id violates not-null constraint")
--
-- 变更：
--   target_doc_id   bigint  NOT NULL → 可空
--   target_doc_type varchar NOT NULL → 可空
--
-- 三方对照：
--   PG:   本迁移
--   Entity: ReconciliationLogEntity.targetDocId/targetDocType 无约束注解
--   Code:  logReconciliationLog() setTargetDocId(null)，意图即空
--          ReconciliationServiceImpl.execute() 正常路径仍写入实际值
--
-- 幂等：PG 的 ALTER COLUMN DROP NOT NULL 天然幂等，可重复执行。
-- ============================================================

ALTER TABLE t_reconciliation_log
    ALTER COLUMN target_doc_id DROP NOT NULL;

ALTER TABLE t_reconciliation_log
    ALTER COLUMN target_doc_type DROP NOT NULL;