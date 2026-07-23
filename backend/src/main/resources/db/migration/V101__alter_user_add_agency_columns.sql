-- ============================================================
-- V101: S-26 Agency 分支 — t_user 扩展用户类型字段
-- 关联 SPEC: S-26-agency-branch-development.md §1.1
-- 关联架构: 多租户架构设计.md §2.1
-- ============================================================

-- 1. 添加用户类型列
ALTER TABLE t_user ADD COLUMN IF NOT EXISTS user_type VARCHAR(20) NOT NULL DEFAULT 'ENTERPRISE';
COMMENT ON COLUMN t_user.user_type IS '用户类型: SUPER_ADMIN/AGENCY/ENTERPRISE';

-- 2. 添加代理公司关联
ALTER TABLE t_user ADD COLUMN IF NOT EXISTS agency_id BIGINT;
COMMENT ON COLUMN t_user.agency_id IS '代理用户所属代理公司ID';

-- 3. 添加企业关联
ALTER TABLE t_user ADD COLUMN IF NOT EXISTS enterprise_id BIGINT;
COMMENT ON COLUMN t_user.enterprise_id IS '用户所属企业ID';

-- 4. 添加约束
ALTER TABLE t_user ADD CONSTRAINT chk_user_type CHECK (user_type IN ('SUPER_ADMIN', 'AGENCY', 'ENTERPRISE'));

-- 5. 插入默认企业（SME 模式兼容）
INSERT INTO t_enterprise (enterprise_code, enterprise_name, mode, status, seed_data_done, deleted, version)
VALUES ('DEFAULT', '默认企业', 'SME', 'ACTIVE', TRUE, 0, 1)
ON CONFLICT (enterprise_code) DO NOTHING;

-- 6. 更新现有用户为 ENTERPRISE 类型并绑定默认企业
UPDATE t_user
SET enterprise_id = (SELECT id FROM t_enterprise WHERE enterprise_code = 'DEFAULT'),
    user_type = 'ENTERPRISE'
WHERE enterprise_id IS NULL;
