-- ============================================================
-- V109: 测试用 — 将 admin 升级为 SUPER_ADMIN + 创建测试代账公司
-- 目的: 开发测试用，admin 拥有全部权限（超级管理员 + 代账公司）
-- ============================================================

-- 1. 创建测试代账公司
INSERT INTO t_agency (agency_code, agency_name, contact_name, contact_phone, status, deleted, version)
VALUES ('TEST_AGENCY', '测试代账公司', '测试联系人', '13800000000', 'ACTIVE', 0, 1)
ON CONFLICT (agency_code) DO NOTHING;

-- 2. 绑定测试代账公司与默认企业
INSERT INTO t_agency_enterprise (agency_id, enterprise_id, status)
SELECT a.id, e.id, 'ACTIVE'
FROM t_agency a, t_enterprise e
WHERE a.agency_code = 'TEST_AGENCY' AND e.enterprise_code = 'DEFAULT'
ON CONFLICT (agency_id, enterprise_id) DO NOTHING;

-- 3. 将 admin 升级为 SUPER_ADMIN，绑定到测试代账公司
UPDATE t_user
SET user_type = 'SUPER_ADMIN',
    agency_id = (SELECT id FROM t_agency WHERE agency_code = 'TEST_AGENCY')
WHERE username = 'admin';
