-- ============================================================
-- V135: t_period 增加期初建账审计字段（P58 期初建账审计增强）
--
-- 背景：
--   P58 需求：期初建账允许任意指定录入时间（建账日期），并记录
--   期初建账日期、录入人员、日志。
--
-- 变更：
--   opened_at      TIMESTAMP  期初建账日期（用户指定录入时间）
--   opened_by      BIGINT     录入人员ID
--   opened_by_name VARCHAR(50) 录入人员名（冗余，便于前端直接展示）
--
-- 幂等：ADD COLUMN IF NOT EXISTS，可重复执行。
-- ============================================================

ALTER TABLE t_period
    ADD COLUMN IF NOT EXISTS opened_at TIMESTAMP;

ALTER TABLE t_period
    ADD COLUMN IF NOT EXISTS opened_by BIGINT;

ALTER TABLE t_period
    ADD COLUMN IF NOT EXISTS opened_by_name VARCHAR(50);

COMMENT ON COLUMN t_period.opened_at IS '期初建账日期（用户指定录入时间，P58）';
COMMENT ON COLUMN t_period.opened_by IS '期初建账录入人员ID（P58）';
COMMENT ON COLUMN t_period.opened_by_name IS '期初建账录入人员名（冗余，P58）';