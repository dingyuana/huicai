-- ============================================================
-- V39: AR/AP 乐观锁版本列 — 防超核销
-- 给 t_receivable / t_payable 加 version 字段，由 MyBatis-Plus @Version 维护
-- ============================================================

ALTER TABLE t_receivable ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 0;
ALTER TABLE t_payable    ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 0;

COMMENT ON COLUMN t_receivable.version IS '乐观锁版本号（MyBatis-Plus @Version）';
COMMENT ON COLUMN t_payable.version    IS '乐观锁版本号（MyBatis-Plus @Version）';