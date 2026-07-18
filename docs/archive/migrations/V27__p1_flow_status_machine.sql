-- ============================================================
-- V27: P1 银行流水状态机扩展
-- 将 review_status 从简单的"待确认/已确认"扩展为完整工作流状态机:
-- classified → voucher_generated / payment_created / manual_pending → approved
-- ============================================================

-- 1. 删除旧约束 (DO 块实现幂等)
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_stmt_review_status') THEN
        ALTER TABLE t_bank_statement DROP CONSTRAINT chk_stmt_review_status;
    END IF;
END $$;

-- 2. 添加新约束
ALTER TABLE t_bank_statement ADD CONSTRAINT chk_stmt_review_status
    CHECK (review_status IN (
        'PENDING',              -- 兼容旧数据, 后续全量迁移后可选删除此值
        'CONFIRMED',             -- 兼容旧数据
        'RECLASSIFIED',          -- 兼容旧数据
        'classified',            -- 已分类, 待确认
        'voucher_generated',     -- A类: 已制证, 待核准 (autoGenerate 完成)
        'payment_created',       -- B类: 已生单, 待核销 (autoGenerate 完成)
        'manual_pending',        -- C类: 待人工指定 A/B 类型后处理
        'approved'               -- 已过账/已核准 (终极终态)
    ));

-- 3. 迁移现有数据: 旧状态 → 新状态映射
UPDATE t_bank_statement SET review_status = 'classified'
WHERE review_status = 'PENDING';

-- CONFIRMED + 有凭证 → voucher_generated (A类或旧的已处理数据)
UPDATE t_bank_statement SET review_status = 'voucher_generated'
WHERE review_status = 'CONFIRMED' AND generated_voucher_id IS NOT NULL;

-- CONFIRMED + 有单据但无凭证 → payment_created (B类旧数据)
UPDATE t_bank_statement SET review_status = 'payment_created'
WHERE review_status = 'CONFIRMED' AND generated_doc_id IS NOT NULL AND generated_voucher_id IS NULL;

-- 其他 CONFIRMED (无任何生成产物) → 统一为 voucher_generated
UPDATE t_bank_statement SET review_status = 'voucher_generated'
WHERE review_status = 'CONFIRMED';

-- RECLASSIFIED → classified
UPDATE t_bank_statement SET review_status = 'classified'
WHERE review_status = 'RECLASSIFIED';

-- 4. 注释更新
COMMENT ON COLUMN t_bank_statement.review_status IS '流程状态: classified-已分类, voucher_generated-A类已制证, payment_created-B类已生单, manual_pending-C类待人工, approved-已过账';
