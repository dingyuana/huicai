-- P141 / V1.90 遗留收口：t_user 种子账号 enterprise_id 回填，迁移化
--
-- 背景（实测取证，全见 docs/specs/P141-tai-closure-and-user-seed-backfill.md §0.2）：
--   V1.90（2026-10-27）曾在运行库手动执行 UPDATE t_user SET enterprise_id = 1
--   修复 3 个种子 AGENCY 账号无归属企业的问题，但**未落进 Flyway 迁移链**。
--   2026-10-28 以「全新环境重放全部迁移」探针坐实：scratch 库 106/106 迁移
--   全部成功后，accountant01 / reviewer01 / assistant01 三行 enterprise_id
--   仍为 NULL ⇒ 每次全新环境都复现（登录后 enterpriseId 为 null，
--   JwtAuthenticationFilter 不设 ThreadLocal ⇒ GUC 不落 ⇒ 读接口空数据）。
--   历史复核：V165 只补 t_agency_user_enterprise 授权行，从未修过列本身。
--
-- 修法（与 V165 同风格：幂等、按 username 反查、不 hard-code id）：
--   范围刻意限定三个系统种子账号——归属企业 1 无业务歧义、且与既有归属一致
--   （三者在 t_agency_user_enterprise 均有企业 1 的授权行，见 V117/V165）。
--   运行时注册的 AGENCY 用户（王会计/liu/zhou_kj 等）归属由注册业务决定，
--   不在本迁移范围；NOT NULL 约束收紧同样不在范围（超 SPEC 边界）。

UPDATE t_user
SET enterprise_id = 1
WHERE enterprise_id IS NULL
  AND username IN ('accountant01', 'reviewer01', 'assistant01');
