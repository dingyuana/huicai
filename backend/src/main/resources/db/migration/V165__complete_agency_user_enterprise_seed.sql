-- P102 / REQ-2026-129 M4：补齐种子数据，避免成员校验把审核员账号锁死
--
-- 背景：X-Enterprise-Id 三源并集校验上线后，切换企业的第二源是
--   t_user → t_agency_user.user_id → t_agency_user_enterprise.agency_user_id
-- 实测种子中 accountant01（agency_user=2）与 assistant01（agency_user=4）
--   均有企业 1 的授权行，唯独 reviewer01（agency_user=3）**没有任何行**，
--   且其 t_user.enterprise_id 为 NULL、user_type 是 AGENCY 而非 SUPER_ADMIN。
--   三源逐一不命中 ⇒ 该账号将**一个企业都进不去**。
--   （修复前它反而能进任意企业 —— 那正是本次要堵的越权漏洞。）
--
-- 修法：补齐与同侪账号一致的种子授权行，不放宽校验规则。
-- 用 NOT EXISTS 保证幂等；agency_user_id 由 username 反查，避免硬编码 id。

INSERT INTO t_agency_user_enterprise (agency_user_id, enterprise_id, assigned_by, assigned_at, deleted)
SELECT au.id, 1, 1, NOW(), 0
FROM t_agency_user au
JOIN t_user u ON u.id = au.user_id
WHERE u.username = 'reviewer01'
  AND au.deleted = 0
  AND NOT EXISTS (
        SELECT 1 FROM t_agency_user_enterprise x
        WHERE x.agency_user_id = au.id AND x.enterprise_id = 1 AND x.deleted = 0
  );
