-- V192: 修复 AGENCY 用户企业绑定顺序和数据可见性
--
-- 问题背景：
-- 1. AGENCY 用户（如王会计）登录时，后端使用 enterpriseList[0] 作为 currentEnterpriseId
-- 2. 但如果 [0] 没有业务数据，用户将看到空列表
-- 3. 同时需要确保用户能访问有数据的 enterprises
--
-- 修复内容：
-- 1. 为王会计（user_id=5）添加 enterprise 1 的绑定（排在前面，有数据）
-- 2. 保留 enterprise 4 的绑定（作为可切换目标）
-- 3. AuthController 已修复：AGENCY 用户使用 enterpriseList[0] 作为默认企业

-- 清空王会计的旧绑定
DELETE FROM t_agency_user_enterprise WHERE agency_user_id = 5;

-- 添加 enterprise 1 绑定（排在最前，有 28 条凭证数据）
INSERT INTO t_agency_user_enterprise (agency_user_id, enterprise_id, assigned_by, assigned_at, deleted, created_by, updated_by)
VALUES (5, 1, 1, NOW() - INTERVAL '3 day', 0, 1, 1);

-- 添加 enterprise 4 绑定（排在第二）
INSERT INTO t_agency_user_enterprise (agency_user_id, enterprise_id, assigned_by, assigned_at, deleted, created_by, updated_by)
VALUES (5, 4, 1, NOW() - INTERVAL '1 day', 0, 1, 1);
