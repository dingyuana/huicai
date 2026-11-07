-- SPEC-P153 A-4（迁移链冒烟）：V192 在全新库冷启动时 FK 断链的修复。
--
-- 根因（Testcontainers 全新容器实测）：V192 硬编码 agency_user_id=5，但全新库
-- t_agency_user 只有 V114 种子的 4 行（admin/accountant01/reviewer01/assistant01，
-- id 1~4）—— id=5 是生产运行时注册「王会计」才出现的行 ⇒ 冷启动必违反
-- fk_aue_au，整条 Flyway 链停在 192。
-- L1 不连真库、生产库早已越过 V192，两侧都照不到 ⇒ L2 全量重跑才暴露
-- （AGENTS §4.5「真库专属缺陷」形态，迁移脚本的存量库惯性 ≠ 新库可回放性）。
--
-- 修法：改为数据驱动 + 幂等 —— 从 t_agency_user 反查 user_id=5 的真实主键，
-- 行不存在时整段自然 no-op（新库语义：没有王会计就不需要他的绑定）。
-- 生产库该操作早已生效，本迁移重放（repair 场景）无数据变化。
--
-- ⚠️ 本文件修改后 checksum 变化：生产侧须先
--   DELETE FROM flyway_schema_history WHERE version = '192';
-- 再重启后端重放（本脚本幂等，重放安全）。

-- 清空该 agency_user 的旧绑定（按真实主键反查，不再硬编码 5）
DELETE FROM t_agency_user_enterprise
WHERE agency_user_id IN (SELECT id FROM t_agency_user WHERE user_id = 5);

-- enterprise 1（排前，有业务数据）与 4（可切换目标）
INSERT INTO t_agency_user_enterprise
    (agency_user_id, enterprise_id, assigned_by, assigned_at, deleted, created_by, updated_by)
SELECT au.id, v.eid, 1, v.assigned_at, 0, 1, 1
FROM t_agency_user au
CROSS JOIN (VALUES
    (1, NOW() - INTERVAL '3 day'),
    (4, NOW() - INTERVAL '1 day')
) AS v(eid, assigned_at)
WHERE au.user_id = 5
  AND EXISTS (SELECT 1 FROM t_enterprise te WHERE te.id = v.eid)
  AND NOT EXISTS (
      SELECT 1 FROM t_agency_user_enterprise x
      WHERE x.agency_user_id = au.id AND x.enterprise_id = v.eid
  );
