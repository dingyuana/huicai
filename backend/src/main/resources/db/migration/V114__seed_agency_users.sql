-- 修复序列（防止 GENERATED ALWAYS AS IDENTITY 序列漂移）
SELECT setval('t_user_id_seq', (SELECT MAX(id) FROM t_user));

-- ============================================================
-- V114: 种子数据 — 代理内角色体系（S-26 V2.0 Sprint 5）
-- 1. admin 设为 AGENCY_ADMIN（如果已是 AGENCY 类型）
-- 2. 创建测试会计/审核员/助理用户
-- ============================================================

-- 将 admin 插入 t_agency_user（如果尚未存在）
INSERT INTO t_agency_user (agency_id, user_id, agency_role, status, created_by, created_at, deleted, version)
SELECT 1, id, 'AGENCY_ADMIN', 'ACTIVE', 1, CURRENT_TIMESTAMP, 0, 0
FROM t_user
WHERE username = 'admin'
  AND user_type IN ('AGENCY', 'SUPER_ADMIN')
  AND NOT EXISTS (SELECT 1 FROM t_agency_user WHERE user_id = t_user.id)
ON CONFLICT (user_id) DO NOTHING;

-- 更新 admin 的 agency_role
UPDATE t_user SET agency_role = 'AGENCY_ADMIN'
WHERE username = 'admin' AND user_type IN ('AGENCY', 'SUPER_ADMIN');

-- 创建测试会计用户（密码: admin123）
INSERT INTO t_user (username, password, real_name, user_type, agency_id, agency_role, status, created_by, created_at, deleted)
SELECT 'accountant01', '$2a$10$N9qo8uLOickgx2ZMRZoMye.IjzqAKL9xL5jvMFVdNJHvGCgTq/VEq', '张会计', 'AGENCY', 1, 'ACCOUNTANT', 'ACTIVE', 1, CURRENT_TIMESTAMP, 0
WHERE NOT EXISTS (SELECT 1 FROM t_user WHERE username = 'accountant01');

-- 创建测试审核员用户（密码: admin123）
INSERT INTO t_user (username, password, real_name, user_type, agency_id, agency_role, status, created_by, created_at, deleted)
SELECT 'reviewer01', '$2a$10$N9qo8uLOickgx2ZMRZoMye.IjzqAKL9xL5jvMFVdNJHvGCgTq/VEq', '李审核', 'AGENCY', 1, 'REVIEWER', 'ACTIVE', 1, CURRENT_TIMESTAMP, 0
WHERE NOT EXISTS (SELECT 1 FROM t_user WHERE username = 'reviewer01');

-- 创建测试助理用户（密码: admin123）
INSERT INTO t_user (username, password, real_name, user_type, agency_id, agency_role, status, created_by, created_at, deleted)
SELECT 'assistant01', '$2a$10$N9qo8uLOickgx2ZMRZoMye.IjzqAKL9xL5jvMFVdNJHvGCgTq/VEq', '王助理', 'AGENCY', 1, 'ASSISTANT', 'ACTIVE', 1, CURRENT_TIMESTAMP, 0
WHERE NOT EXISTS (SELECT 1 FROM t_user WHERE username = 'assistant01');

-- 将测试用户插入 t_agency_user
INSERT INTO t_agency_user (agency_id, user_id, agency_role, status, created_by, created_at, deleted, version)
SELECT 1, id, agency_role, 'ACTIVE', 1, CURRENT_TIMESTAMP, 0, 0
FROM t_user
WHERE username IN ('accountant01', 'reviewer01', 'assistant01')
  AND NOT EXISTS (SELECT 1 FROM t_agency_user WHERE user_id = t_user.id)
ON CONFLICT (user_id) DO NOTHING;
