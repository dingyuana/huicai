-- ============================================================
-- V113: t_user 加 agency_role 列（S-26 V2.0 Sprint 5）
-- 冗余字段，方便查询代理用户的角色
-- ============================================================

ALTER TABLE t_user ADD COLUMN IF NOT EXISTS agency_role VARCHAR(20);

COMMENT ON COLUMN t_user.agency_role IS '代理内角色: AGENCY_ADMIN/ACCOUNTANT/REVIEWER/ASSISTANT，非AGENCY用户为null';
