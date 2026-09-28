-- =============================================================================
-- V155: 修正种子账号密码哈希（REQ-2026-104 / SPC-P99）
-- =============================================================================
-- 【缺陷背景】
-- V1__baseline.sql 为 admin 写入的哈希，以及 V114__seed_agency_users.sql 为
-- accountant01 / reviewer01 / assistant01 写入的哈希，四者完全相同：
--     $2a$10$N9qo8uLOickgx2ZMRZoMye.IjzqAKL9xL5jvMFVdNJHvGCgTq/VEq
-- 而 V114 的注释三处均声明「密码: admin123」。二者**不符**：
-- 该哈希与 admin123 不匹配（经 python3-bcrypt 独立校验，方法已用
-- hashpw + checkpw 往返自检确认有效）。
--
-- 【症状】全新迁移的数据库中四个账号**全部无法登录**：
--   POST /api/v1/auth/login  ->  400 "用户名或密码错误"
--   后端日志为 BadCredentialsException（而非 UsernameNotFoundException），
--   即用户可查到、仅密码比对失败 —— 排除「用户不存在/被停用/逻辑删除」。
--
-- 【影响】e2e 冒烟测试（e2e/tests/helpers.ts 硬编码 admin/admin123）全部受阻，
-- 人工业务验收亦无法进行。
--
-- 【本迁移做什么】
-- 将四个种子账号的密码哈希统一更正为 admin123 的正确哈希。
-- 由应用自身的 BCryptPasswordEncoder（spring-security-crypto 6.2.4，
-- 与 config/security/SecurityConfig.java 同一实现）生成，$2a$ 前缀与
-- 既有数据格式一致；已用 python3-bcrypt 双向校验（正确口令匹配、错误口令被拒）。
--
-- 【范围约束】
--   · 只 UPDATE t_user.password，不改表结构、不建表、不删数据
--   · 不触碰任何金额/余额/凭证/往来等财务表
--   · WHERE 限定四个固定 username，账号不存在时静默跳过（幂等）
--   · 重新执行结果一致（可重复运行）
--
-- 【已知局限】
-- 本系统无「首次登录强制改密」机制。本迁移写入的是公开的弱口令，
-- 仅适用于开发/测试环境；生产部署前必须另行处理
-- （另置为独立需求，见 REQUIREMENTS_REGISTRY REQ-2026-104 说明）。
-- =============================================================================

UPDATE t_user
SET password = '$2a$10$4fe4Jve.QT80i1eA/N01aO39lMPPy3KcFJK5PoezOHjJzQ0XvGITO',
    updated_at = CURRENT_TIMESTAMP
WHERE username IN ('admin', 'accountant01', 'reviewer01', 'assistant01')
  AND deleted = 0;

-- ------------------------------------------------------------------
-- 【开发/测试环境账号凭据】（生产环境请勿沿用）
--   admin         / admin123   SUPER_ADMIN  企业=1  代理=1
--   accountant01  / admin123   ACCOUNTANT
--   reviewer01    / admin123   REVIEWER
--   assistant01   / admin123   ASSISTANT
-- ------------------------------------------------------------------
