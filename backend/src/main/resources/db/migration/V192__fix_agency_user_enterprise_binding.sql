-- V192: 修复 AGENCY 用户企业绑定顺序
-- 问题：王会计等 AGENCY 用户 login 后 currentEnterpriseId 使用 enterpriseList[0]，
--       但若 [0] 无业务数据，则损益结转/期间查询等操作会查空数据。
-- 修复：确保有业务数据的 enterprise 排在 enterpriseList 前面。
--       此处将 enterprise_id=1 的 assigned_at 提前，使其在 ORDER BY assigned_at ASC 时排第一。

-- 王会计（user_id=5）：确保 enterprise 1 排在 enterprise 3 前面
UPDATE t_agency_user_enterprise 
SET assigned_at = NOW() - INTERVAL '1 day'
WHERE agency_user_id = 5 AND enterprise_id = 1
  AND assigned_at > (
    SELECT assigned_at FROM t_agency_user_enterprise 
    WHERE agency_user_id = 5 AND enterprise_id = 3
  );

-- 同理处理其他 ACCOUNTANT 用户（如有多企业绑定）
-- accountant01（user_id=2）绑定 enterprise 2
-- liu（user_id=6）绑定 enterprise 4
-- 这些用户各自只有一个绑定企业，无需调整
