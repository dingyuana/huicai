-- =============================================================================
-- V157: 修复 chk_stmt_review_status 与代码枚举不一致（REQ-2026-111 / SPC-P99）
-- =============================================================================
-- 【缺陷背景】
-- chk_stmt_review_status 的允许集与 Java 枚举 StatementStatus、前端映射
-- frontend/src/api/modules/bankStatement.ts 存在三处不一致（已实证：以下三个
-- 值插入 t_bank_statement 均报 violates check constraint）：
--
--   枚举值              写入位置                                          DB
--   --------------------------------------------------------------------------------
--   'manual_pending'    BankStatementServiceImpl:669/679                 拒绝
--   'DUPLICATE'         BankStatementExcelImportService:178/315         拒绝
--   'classified'        StatementStatus.CLASSIFIED                      拒绝
--
-- V120 曾修过同一约束（当时补入 voucher_generated/payment_created/approved），
-- 但未覆盖以上三个值，属同一问题二次复发。
--
-- 【业务影响】
-- 1. processManual 走到兜底分支（ok=false）时写入 'manual_pending' 被拒，
--    事务回滚，状态卡死；
-- 2. 银行流水 Excel 导入命中重复流水（isDup=true）时写入 'DUPLICATE' 被拒，
--    整批导入失败；
-- 3. 前端 PendingPool.vue:136 按 reviewStatus='manual_pending' 查询，
--    因该值无法落库而恒查不到数据。
--
-- 【本迁移做法：扩展 DB 约束为超集，不动 Java 与前端】
-- 理由：Java 侧（StatementStatus）与前端（bankStatement.ts / PendingPool.vue）
-- 已经一致使用这些小写值，DB 约束是唯一异类；只改迁移可把改动面压到 1 个文件，
-- 且超集写法向后兼容 —— 原有 10 个值全部继续合法。
--
-- 【存量数据归一化】
-- 生产库可能存在历史写入的大写 'MANUAL_PENDING' / 'CLASSIFIED'（V120 之后
-- 允许写入），而代码实际写的是小写，会造成「查得到但流程不认」的孤岛数据。
-- 因此在换约束前统一归一化为代码实际写入的小写形式。
-- 本地/dev 库 t_bank_statement 为 0 行，此段为幂等空操作。
--
-- 【执行顺序说明】
-- 必须先 DROP 约束 —— 旧约束不允许小写值，直接 UPDATE 会被拒绝。
--   1. DROP 旧约束
--   2. 归一化存量（此时已无约束限制）
--   3. ADD 新约束（13 值超集）
-- =============================================================================

DO $$
BEGIN
    -- 1. 先移除旧约束
    ALTER TABLE t_bank_statement DROP CONSTRAINT IF EXISTS chk_stmt_review_status;

    -- 2. 存量归一化：把历史大写值转为代码实际写入的小写值
    --    （两列均 NOT NULL 之外的普通 varchar，可安全 UPDATE）
    UPDATE t_bank_statement SET review_status = 'manual_pending'
     WHERE review_status = 'MANUAL_PENDING';
    UPDATE t_bank_statement SET review_status = 'classified'
     WHERE review_status = 'CLASSIFIED';

    -- 3. 新约束：原 10 值 + 本次补入 3 值 = 13 值超集
    ALTER TABLE t_bank_statement ADD CONSTRAINT chk_stmt_review_status
        CHECK (review_status::text = ANY (ARRAY[
            -- 原有 10 值（V120 修复后集合）
            'PENDING',
            'UNCONFIRMED',
            'CLASSIFIED',
            'CONFIRMED',
            'RECLASSIFIED',
            'REJECTED',
            'MANUAL_PENDING',
            'voucher_generated',
            'payment_created',
            'approved',
            -- 本次补入：与 StatementStatus / 前端映射对齐
            'manual_pending',
            'classified',
            'DUPLICATE'
        ]::text[]));
END $$;

COMMENT ON CONSTRAINT chk_stmt_review_status ON t_bank_statement IS
    'REQ-2026-111: 扩展为 13 值超集，补入 manual_pending / classified / DUPLICATE 以对齐 StatementStatus 枚举与前端映射（V157）';
