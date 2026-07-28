-- V120: 修复银行流水审核状态问题
-- 根因：chk_stmt_review_status CHECK 约束只允许 PENDING/CONFIRMED/REJECTED，
--       但代码中 audit() 会将 reviewStatus 设为 voucher_generated/payment_created/approved，
--       导致 updateById 违反 CHECK 约束，事务回滚，状态卡在 CONFIRMED。
-- 修复：扩展 CHECK 约束允许完整状态机值。

DO $$
BEGIN
    ALTER TABLE t_bank_statement DROP CONSTRAINT IF EXISTS chk_stmt_review_status;
    ALTER TABLE t_bank_statement ADD CONSTRAINT chk_stmt_review_status
        CHECK (review_status::text = ANY (ARRAY[
            'PENDING',
            'UNCONFIRMED',
            'CLASSIFIED',
            'CONFIRMED',
            'RECLASSIFIED',
            'REJECTED',
            'MANUAL_PENDING',
            'voucher_generated',
            'payment_created',
            'approved'
        ]::text[]));
END $$;
