-- ============================================================
-- V132: t_period 增加期初建账状态 opening_status
--
-- 背景：
--   原 t_period.status 字段 (open/closed/locked) 表示整个期间的开关，
--   "locked" 会同时锁住凭证过账 (assertPeriodOpen) 与结账 (checkBeforeClose)，
--   与"期初数据锁定"语义冲突：期初锁定后凭证业务必须照常进行。
--   因此需要新增独立字段表达期初建账状态机。
--
-- 状态机：
--   none     — 未建账（期间存在但尚未录入期初余额）
--   entered  — 已录入未锁定（允许清空重录、允许编辑）
--   locked   — 已锁定（不可修改、不可清空；解锁需走 unlock 端点）
--
-- 存量数据兼容：
--   已有 t_subject_balance 记录的期间，回填为 entered，避免历史业务被
--   P0-3 期初前置强制误伤。
-- ============================================================

ALTER TABLE t_period
    ADD COLUMN IF NOT EXISTS opening_status VARCHAR(20) NOT NULL DEFAULT 'none';

-- CHECK 约束（幂等添加）
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'chk_period_opening_status'
    ) THEN
        ALTER TABLE t_period
            ADD CONSTRAINT chk_period_opening_status
            CHECK (opening_status IN ('none', 'entered', 'locked'));
    END IF;
END $$;

COMMENT ON COLUMN t_period.opening_status IS
    '期初建账状态: none-未建账, entered-已录入未锁定, locked-已锁定';

-- 存量回填：t_subject_balance 已有记录的期间视为已建账
UPDATE t_period p
   SET opening_status = 'entered'
 WHERE opening_status = 'none'
   AND EXISTS (
        SELECT 1
          FROM t_subject_balance sb
         WHERE sb.period = p.period_code
           AND sb.enterprise_id = p.enterprise_id
   );
