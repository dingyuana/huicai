-- V117: 为测试会计/助理分配客户企业，确保企业切换器有可选企业
-- 给会计分配 DEFAULT 企业
INSERT INTO t_agency_user_enterprise (agency_user_id, enterprise_id, assigned_by, assigned_at, deleted)
SELECT au.id, e.id, 1, CURRENT_TIMESTAMP, 0
FROM t_agency_user au, t_enterprise e
WHERE au.user_id = (SELECT id FROM t_user WHERE username = 'accountant01')
  AND e.enterprise_code = 'DEFAULT'
  AND NOT EXISTS (
    SELECT 1 FROM t_agency_user_enterprise aue
    WHERE aue.agency_user_id = au.id AND aue.enterprise_id = e.id AND aue.deleted = 0
  );

-- 给助理分配 DEFAULT 企业
INSERT INTO t_agency_user_enterprise (agency_user_id, enterprise_id, assigned_by, assigned_at, deleted)
SELECT au.id, e.id, 1, CURRENT_TIMESTAMP, 0
FROM t_agency_user au, t_enterprise e
WHERE au.user_id = (SELECT id FROM t_user WHERE username = 'assistant01')
  AND e.enterprise_code = 'DEFAULT'
  AND NOT EXISTS (
    SELECT 1 FROM t_agency_user_enterprise aue
    WHERE aue.agency_user_id = au.id AND aue.enterprise_id = e.id AND aue.deleted = 0
  );