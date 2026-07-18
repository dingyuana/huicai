-- ============================================================
-- V53: P1 状态机 — t_bank_statement.review_status 扩展枚举
-- 新增状态: classified, voucher_generated, payment_created,
--           manual_pending, approved
-- 保留: CONFIRMED, PENDING (向后兼容旧数据)
-- ============================================================

-- 表空则无需迁移旧数据
-- 修改默认值: PENDING → classified
ALTER TABLE t_bank_statement
    ALTER COLUMN review_status SET DEFAULT 'classified';

-- 重建 CHECK 约束: 先用新名替代(旧名可能在早期 migration 中已建)
ALTER TABLE t_bank_statement
    DROP CONSTRAINT IF EXISTS chk_t_bank_statement_review_status;

ALTER TABLE t_bank_statement
    ADD CONSTRAINT chk_t_bank_statement_review_status
        CHECK (review_status IN (
            'classified',
            'voucher_generated',
            'payment_created',
            'manual_pending',
            'approved',
            'CONFIRMED',
            'PENDING',
            'UNCONFIRMED',
            'RECLASSIFIED'
        ));

COMMENT ON COLUMN t_bank_statement.review_status IS
    '出纳确认状态: classified=已分类, voucher_generated=凭证草稿待核准, payment_created=付款单待核销, manual_pending=C类待人工, approved=已核准; 兼容旧值 CONFIRMED/PENDING/UNCONFIRMED/RECLASSIFIED';
