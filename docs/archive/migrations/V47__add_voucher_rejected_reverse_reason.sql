-- V47__add_voucher_rejected_reverse_reason.sql
-- 2026-06-22 P22 实施 (修订版: 只加 2 字段, 不动 status CHECK 约束)
-- 依据: docs/specs/P22-voucher-state-machine.md (2026-06-22 修订)

-- 1. 加 2 个新字段 (text 类型, nullable)
ALTER TABLE t_voucher ADD COLUMN IF NOT EXISTS rejected_reason VARCHAR(500);
ALTER TABLE t_voucher ADD COLUMN IF NOT EXISTS reverse_reason VARCHAR(500);

-- 注: status 字段 CHECK 约束不变 (4 状态 DRAFT/SUBMITTED/AUDITED/POSTED, V8 已建)
-- 注: reversedFrom 字段已存在 (V41 之前已加), 不动

-- 2. COMMENT 更新
COMMENT ON COLUMN t_voucher.rejected_reason IS
    '驳回原因: SUBMITTED 驳回时记录, status 回退到 DRAFT (2026-06-22 P22)';
COMMENT ON COLUMN t_voucher.reverse_reason IS
    '红冲原因: 生成红字凭证时记录, 红字凭证 source=REVERSAL (2026-06-22 P22)';

-- 3. 校验: status 字段无非法值
DO $$
DECLARE
    invalid_status_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO invalid_status_count
    FROM t_voucher
    WHERE status NOT IN ('DRAFT', 'SUBMITTED', 'AUDITED', 'POSTED');

    IF invalid_status_count > 0 THEN
        RAISE EXCEPTION 'V47 校验失败: t_voucher 有 % 条记录的 status 不在 4 状态枚举中', invalid_status_count;
    ELSE
        RAISE NOTICE 'V47 校验通过: t_voucher status 全部合法';
    END IF;
END $$;
