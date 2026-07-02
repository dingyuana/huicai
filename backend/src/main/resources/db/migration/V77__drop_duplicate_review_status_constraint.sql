-- ============================================================
-- V77: 清理 t_bank_statement 重复的 review_status CHECK 约束
-- 
-- 问题: t_bank_statement 上有两个 review_status 约束:
--   chk_stmt_review_status             (8 值)
--   chk_t_bank_statement_review_status (9 值, 含 UNCONFIRMED)
-- 后者是前者的重复，且 UNCONFIRMED 不在 P23 SPEC 中
-- ============================================================

ALTER TABLE t_bank_statement DROP CONSTRAINT IF EXISTS chk_t_bank_statement_review_status;
