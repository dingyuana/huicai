-- =============================================================================
-- V178: 补 created_by / updated_by 审计列 —— 修复 BaseEntity 幽灵字段（SPEC-P117）
-- 关联：REQ-2026-141 / SPEC-P117
-- =============================================================================
-- 【缺陷：created_by 从不落库、也从不读回 —— 一条"从未生效过"的制审分离内控】
--
-- BaseEntity 的 createdBy / updatedBy 标了 @TableField(exist = false)：
--   @TableField(exist = false) private Long createdBy;   // "由 Service 层手动 set"
-- 该注解使 MyBatis-Plus **既不把该列写进 INSERT/UPDATE，也不把它映射进 SELECT**。
-- 于是全仓 57 处 setCreatedBy(...) 全是**误导性死代码**（§4.2 第 10 条「幽灵字段」）。
--
-- 【行为证据（非推演）】
--   夹具行经 jdbcTemplate 直查  : created_by = 900001
--   同一行经 voucherMapper.selectById : entity.getCreatedBy() = null
--   ⇒ 断言 userId.equals(entity.getCreatedBy()) 恒 false
--   ⇒ BusinessDocServiceImpl#approve 的「制单人不能审核自己提交的单据」
--      **自落地起从未拦下过一次**，属装饰性内控；凭证层同理。
--
-- 【为什么以前"看起来正常"】
--   ① t_voucher 等 39 张表的 created_by 实测 37/37 行全为 NULL，
--      很容易被读成「种子数据不走 Service」；
--   ② 前端「制单人」列恒空白，而该列本就少被使用；
--   ③ null 判空让代码路径看起来"健壮"（`entity.getCreatedBy() != null`）。
--   ⚠️ 这三点都不是证据，只是**缺陷恰好不显形**的三个条件（§4.3 第 9 条同型）。
--
-- 【本迁移的作用：先把列补齐，使去掉 exist=false 成为可能】
--   去掉 exist=false 后，MyBatis-Plus 会把这两列写进**所有** BaseEntity 子类的 SQL。
--   实测 83 张 t_* 表中：
--     同时有 created_by 与 updated_by : 22 张
--     至少缺其中之一                 : 61 张  ← 本迁移补齐
--   缺列表若不补，MP 生成的 SQL 会报 column does not exist（**运行期**才炸，
--   L1 Mock 测不出来，只有 L2 真库会红 —— §4.5 第 7 条 Mock 盲区）。
--
-- 【类型裁定：bigint，与 BaseEntity 的 Long 对齐】
--   既有分布（实测）：created_by bigint 39 张、updated_by bigint 22 张。
--   ⚠️ **唯一例外 t_prepayment.created_by 是 varchar(50)** ——
--      PrepaymentEntity 刻意声明 String 版本且不继承 BaseEntity 的 Long
--      （该表 created_at/updated_at 是 date，与基类 LocalDateTime 亦不兼容）。
--      ⇒ 本迁移**排除 t_prepayment**，不得改其 created_by 类型。
--
-- 【已知局限（不在本轮范围，登记 SPEC-P117 §7）】
--   历史行的 created_by 仍是 NULL（「不知道谁制的」不能反过来说「他自审」）。
--   回填需业务判断"该填谁"，不属机械迁移，故不做。
-- =============================================================================

-- 幂等：仅当列不存在时补（IF NOT EXISTS 保证重复执行安全）
DO $$
DECLARE
    t  text;
    -- 实测缺 created_by 或 updated_by 的表（83 张 t_* 表中 61 张）
    tbls text[] := ARRAY[
        't_account_mapping_rule', 't_agency_enterprise', 't_agency_user_enterprise',
        't_aging_alert', 't_ai_anomaly_tag', 't_ai_feedback_log', 't_ai_task',
        't_arap_settlement_entry', 't_asset_category', 't_asset_change',
        't_asset_depreciation', 't_asset_disposal', 't_asset_inventory',
        't_asset_inventory_entry', 't_attachment', 't_audit_log', 't_aux_dimension',
        't_bad_debt_detail', 't_bad_debt_provision', 't_bad_debt_provision_detail',
        't_bad_debt_provision_scheme', 't_bad_debt_provision_scheme_item',
        't_bad_debt_scheme', 't_bank_account', 't_bank_journal',
        't_bank_reconciliation_log', 't_bank_statement', 't_budget',
        't_budget_adjustment', 't_budget_entry', 't_business_doc_entry',
        't_cash_flow_rule', 't_close_log', 't_customer', 't_customer_statement',
        't_employee', 't_financial_metric', 't_input_invoice', 't_note_receivable',
        't_output_invoice', 't_purchase_return', 't_reconciliation_dispute',
        't_reconciliation_exception', 't_reconciliation_log',
        't_reconciliation_outstanding', 't_reconciliation_suggestion',
        't_reconciliation_tolerance', 't_report_template', 't_role_menu',
        't_subject_balance', 't_tax_carry_over', 't_tax_declaration', 't_tax_type',
        't_ticket_transaction', 't_user_role', 't_vendor', 't_voucher_cash_flow',
        't_voucher_entry', 't_voucher_template', 't_voucher_template_line'
        -- ⚠️ 刻意排除 t_prepayment：其 created_by 是 varchar(50)，
        --    PrepaymentEntity 用 String 且不继承基类的 Long，不得改动其类型。
    ];
BEGIN
    FOREACH t IN ARRAY tbls LOOP
        -- 列类型与既有 39/22 张表逐字一致：bigint，可空，无默认
        -- （不给 DEFAULT：created_by 必须是显式业务赋值才可追溯，
        --   DEFAULT 0 会造出"用户 id=0"这种幽灵记录）
        EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS created_by bigint', t);
        EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS updated_by bigint', t);
    END LOOP;
END $$;
