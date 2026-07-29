-- ============================================================
-- V126: 修复 3 张表的 CHECK 约束缺少代码实际使用的状态值
--
-- 背景：V1 baseline 定义的 CHECK 约束状态集与 Java 代码
-- 实际设置的状态值不一致，导致运行时 500 错误。
-- 修复方式：扩展约束列表，包含 ArapStatus / BudgetStatus
-- 常量类中定义且被 Service 代码使用的所有状态值。
-- ============================================================

-- ─── 1. t_arap_settlement (chk_settlement_status) ───────────
-- ArapStatus 常量类: DRAFT, SUBMITTED, CONFIRMED, REJECTED, REVERSED,
--                    VOUCHERED, EXECUTED, CANCELLED, APPLIED, SETTLED
-- Service 实际使用: DRAFT, SUBMITTED, CONFIRMED, REJECTED, CANCELLED, VOUCHERED, REVERSED
ALTER TABLE t_arap_settlement DROP CONSTRAINT IF EXISTS chk_settlement_status;
ALTER TABLE t_arap_settlement ADD CONSTRAINT chk_settlement_status
    CHECK (status IN (
        'DRAFT', 'SUBMITTED', 'CONFIRMED', 'REJECTED',
        'VOUCHERED', 'REVERSED', 'CANCELLED'
    ));

-- ─── 2. t_tax_declaration (chk_declaration_status) ──────────
-- DB 旧值: DRAFT, SUBMITTED, APPROVED, PAID
-- Service 实际使用: DRAFT, SUBMITTED, APPROVED, REJECTED
ALTER TABLE t_tax_declaration DROP CONSTRAINT IF EXISTS chk_declaration_status;
ALTER TABLE t_tax_declaration ADD CONSTRAINT chk_declaration_status
    CHECK (status IN (
        'DRAFT', 'SUBMITTED', 'APPROVED', 'REJECTED'
    ));

-- ─── 3. t_budget (chk_budget_status) ───────────────────────
-- BudgetStatus 常量类: DRAFT, SUBMITTED, APPROVED, ACTIVE, CLOSED, REJECTED, FROZEN
-- DB 旧值: DRAFT, APPROVED, EXECUTING, CLOSED
-- Service 实际使用: DRAFT, SUBMITTED, APPROVED, ACTIVE, CLOSED, REJECTED, FROZEN(预留)
ALTER TABLE t_budget DROP CONSTRAINT IF EXISTS chk_budget_status;
ALTER TABLE t_budget ADD CONSTRAINT chk_budget_status
    CHECK (status IN (
        'DRAFT', 'SUBMITTED', 'APPROVED', 'ACTIVE',
        'CLOSED', 'REJECTED', 'FROZEN'
    ));