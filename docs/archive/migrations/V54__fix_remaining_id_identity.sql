-- V54: 批量修复 47 张主表 id 列缺 IDENTITY 的问题
-- 2026-06-25 P31 沉淀 + skill huicai-java-backend §0 落地
--
-- 问题: 47 张 t_ 表在 V1-V53 各 migration 建表时只用 `id BIGINT PRIMARY KEY`
--       (无 IDENTITY, 无 DEFAULT), 但 Entity 用 IdType.AUTO,
--       导致任何 INSERT 都报 not-null 约束错误, 整个事务回滚.
--       P31 已证实: 触发后 outer 事务回滚, 状态假象变更, 数据静默丢失.
--
-- 修复: 把 id 列改为 GENERATED ALWAYS AS IDENTITY.
--
-- 设计要点 (沿用 V28 + V52):
--   1. 幂等保护: DO $$ ... IF NOT EXISTS ... END IF + 嵌套 EXCEPTION 块
--   2. START WITH = MAX(id) + 1, 避免新 INSERT 与历史 ID 冲突.
--   3. 空表 (max_id=0) START WITH 1 与 V23 默认行为一致.
--   4. 不重建表, 不破坏现有数据, 不影响已修 18 张表.
--
-- 真实数据来源: docker exec huicai-postgres psql 实查 (2026-06-25 21:18).
-- 已修 18 张: t_audit_log/V52 t_bank_statement t_business_doc t_business_doc_entry
--              t_customer/V30 t_employee t_expense_reimbursement t_input_invoice
--              t_output_invoice t_period/V32 t_reconciliation_exception/V38
--              t_reconciliation_log/V24 t_subject/V28 t_vendor/V30 t_voucher/V28
--              t_voucher_entry/V28 t_voucher_template/V23 t_voucher_template_line/V23
-- 待修 47 张: 下方 ALTER 范围.
--
-- 落地依据: docs/DESIGN.md §10.5 列出 44 张 + 实查补 3 张 (t_arap_settlement_entry /
--           t_beginning_control / t_tax_carry_over / t_reconciliation_suggestion /
--           t_payable / t_receivable / t_prepayment / t_voucher_cash_flow /
--           t_budget_execution / t_arap_settlement 实查有数据).

-- ============================================================
-- 幂等 DO block: 对每张表检查后 ALTER
--   - DROP DEFAULT 用 EXCEPTION WHEN OTHERS 捕获 (无 default 时不报错)
--   - ADD GENERATED ALWAYS AS IDENTITY 用 IF NOT EXISTS 保护
-- ============================================================
DO $$
DECLARE
  v_already boolean;
BEGIN
  -- t_ai_anomaly_tag (max_id=0, START WITH 1)
  SELECT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 't_ai_anomaly_tag'
      AND column_name = 'id' AND is_identity = 'YES'
  ) INTO v_already;
  IF NOT v_already THEN
    BEGIN
      EXECUTE format('ALTER TABLE t_ai_anomaly_tag ALTER COLUMN id DROP DEFAULT');
    EXCEPTION WHEN OTHERS THEN
      -- 无 DEFAULT 时不报错
      NULL;
    END;
    EXECUTE format('ALTER TABLE t_ai_anomaly_tag ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (START WITH 1)');
  END IF;

  -- t_ai_feedback_log (max_id=0, START WITH 1)
  SELECT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 't_ai_feedback_log'
      AND column_name = 'id' AND is_identity = 'YES'
  ) INTO v_already;
  IF NOT v_already THEN
    BEGIN
      EXECUTE format('ALTER TABLE t_ai_feedback_log ALTER COLUMN id DROP DEFAULT');
    EXCEPTION WHEN OTHERS THEN
      -- 无 DEFAULT 时不报错
      NULL;
    END;
    EXECUTE format('ALTER TABLE t_ai_feedback_log ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (START WITH 1)');
  END IF;

  -- t_ai_task (max_id=0, START WITH 1)
  SELECT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 't_ai_task'
      AND column_name = 'id' AND is_identity = 'YES'
  ) INTO v_already;
  IF NOT v_already THEN
    BEGIN
      EXECUTE format('ALTER TABLE t_ai_task ALTER COLUMN id DROP DEFAULT');
    EXCEPTION WHEN OTHERS THEN
      -- 无 DEFAULT 时不报错
      NULL;
    END;
    EXECUTE format('ALTER TABLE t_ai_task ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (START WITH 1)');
  END IF;

  -- t_alert_rule (max_id=0, START WITH 1)
  SELECT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 't_alert_rule'
      AND column_name = 'id' AND is_identity = 'YES'
  ) INTO v_already;
  IF NOT v_already THEN
    BEGIN
      EXECUTE format('ALTER TABLE t_alert_rule ALTER COLUMN id DROP DEFAULT');
    EXCEPTION WHEN OTHERS THEN
      -- 无 DEFAULT 时不报错
      NULL;
    END;
    EXECUTE format('ALTER TABLE t_alert_rule ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (START WITH 1)');
  END IF;

  -- t_arap_settlement (max_id=2068149335358390274, START WITH 2068149335358390275)
  SELECT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 't_arap_settlement'
      AND column_name = 'id' AND is_identity = 'YES'
  ) INTO v_already;
  IF NOT v_already THEN
    BEGIN
      EXECUTE format('ALTER TABLE t_arap_settlement ALTER COLUMN id DROP DEFAULT');
    EXCEPTION WHEN OTHERS THEN
      -- 无 DEFAULT 时不报错
      NULL;
    END;
    EXECUTE format('ALTER TABLE t_arap_settlement ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (START WITH 2068149335358390275)');
  END IF;

  -- t_arap_settlement_entry (max_id=0, START WITH 1)
  SELECT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 't_arap_settlement_entry'
      AND column_name = 'id' AND is_identity = 'YES'
  ) INTO v_already;
  IF NOT v_already THEN
    BEGIN
      EXECUTE format('ALTER TABLE t_arap_settlement_entry ALTER COLUMN id DROP DEFAULT');
    EXCEPTION WHEN OTHERS THEN
      -- 无 DEFAULT 时不报错
      NULL;
    END;
    EXECUTE format('ALTER TABLE t_arap_settlement_entry ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (START WITH 1)');
  END IF;

  -- t_asset_card (max_id=0, START WITH 1)
  SELECT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 't_asset_card'
      AND column_name = 'id' AND is_identity = 'YES'
  ) INTO v_already;
  IF NOT v_already THEN
    BEGIN
      EXECUTE format('ALTER TABLE t_asset_card ALTER COLUMN id DROP DEFAULT');
    EXCEPTION WHEN OTHERS THEN
      -- 无 DEFAULT 时不报错
      NULL;
    END;
    EXECUTE format('ALTER TABLE t_asset_card ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (START WITH 1)');
  END IF;

  -- t_asset_category (max_id=5, START WITH 6)
  SELECT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 't_asset_category'
      AND column_name = 'id' AND is_identity = 'YES'
  ) INTO v_already;
  IF NOT v_already THEN
    BEGIN
      EXECUTE format('ALTER TABLE t_asset_category ALTER COLUMN id DROP DEFAULT');
    EXCEPTION WHEN OTHERS THEN
      -- 无 DEFAULT 时不报错
      NULL;
    END;
    EXECUTE format('ALTER TABLE t_asset_category ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (START WITH 6)');
  END IF;

  -- t_asset_change (max_id=0, START WITH 1)
  SELECT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 't_asset_change'
      AND column_name = 'id' AND is_identity = 'YES'
  ) INTO v_already;
  IF NOT v_already THEN
    BEGIN
      EXECUTE format('ALTER TABLE t_asset_change ALTER COLUMN id DROP DEFAULT');
    EXCEPTION WHEN OTHERS THEN
      -- 无 DEFAULT 时不报错
      NULL;
    END;
    EXECUTE format('ALTER TABLE t_asset_change ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (START WITH 1)');
  END IF;

  -- t_asset_depreciation (max_id=0, START WITH 1)
  SELECT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 't_asset_depreciation'
      AND column_name = 'id' AND is_identity = 'YES'
  ) INTO v_already;
  IF NOT v_already THEN
    BEGIN
      EXECUTE format('ALTER TABLE t_asset_depreciation ALTER COLUMN id DROP DEFAULT');
    EXCEPTION WHEN OTHERS THEN
      -- 无 DEFAULT 时不报错
      NULL;
    END;
    EXECUTE format('ALTER TABLE t_asset_depreciation ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (START WITH 1)');
  END IF;

  -- t_asset_disposal (max_id=0, START WITH 1)
  SELECT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 't_asset_disposal'
      AND column_name = 'id' AND is_identity = 'YES'
  ) INTO v_already;
  IF NOT v_already THEN
    BEGIN
      EXECUTE format('ALTER TABLE t_asset_disposal ALTER COLUMN id DROP DEFAULT');
    EXCEPTION WHEN OTHERS THEN
      -- 无 DEFAULT 时不报错
      NULL;
    END;
    EXECUTE format('ALTER TABLE t_asset_disposal ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (START WITH 1)');
  END IF;

  -- t_asset_inventory (max_id=0, START WITH 1)
  SELECT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 't_asset_inventory'
      AND column_name = 'id' AND is_identity = 'YES'
  ) INTO v_already;
  IF NOT v_already THEN
    BEGIN
      EXECUTE format('ALTER TABLE t_asset_inventory ALTER COLUMN id DROP DEFAULT');
    EXCEPTION WHEN OTHERS THEN
      -- 无 DEFAULT 时不报错
      NULL;
    END;
    EXECUTE format('ALTER TABLE t_asset_inventory ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (START WITH 1)');
  END IF;

  -- t_asset_inventory_entry (max_id=0, START WITH 1)
  SELECT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 't_asset_inventory_entry'
      AND column_name = 'id' AND is_identity = 'YES'
  ) INTO v_already;
  IF NOT v_already THEN
    BEGIN
      EXECUTE format('ALTER TABLE t_asset_inventory_entry ALTER COLUMN id DROP DEFAULT');
    EXCEPTION WHEN OTHERS THEN
      -- 无 DEFAULT 时不报错
      NULL;
    END;
    EXECUTE format('ALTER TABLE t_asset_inventory_entry ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (START WITH 1)');
  END IF;

  -- t_attachment (max_id=0, START WITH 1)
  SELECT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 't_attachment'
      AND column_name = 'id' AND is_identity = 'YES'
  ) INTO v_already;
  IF NOT v_already THEN
    BEGIN
      EXECUTE format('ALTER TABLE t_attachment ALTER COLUMN id DROP DEFAULT');
    EXCEPTION WHEN OTHERS THEN
      -- 无 DEFAULT 时不报错
      NULL;
    END;
    EXECUTE format('ALTER TABLE t_attachment ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (START WITH 1)');
  END IF;

  -- t_bad_debt_provision (max_id=0, START WITH 1)
  SELECT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 't_bad_debt_provision'
      AND column_name = 'id' AND is_identity = 'YES'
  ) INTO v_already;
  IF NOT v_already THEN
    BEGIN
      EXECUTE format('ALTER TABLE t_bad_debt_provision ALTER COLUMN id DROP DEFAULT');
    EXCEPTION WHEN OTHERS THEN
      -- 无 DEFAULT 时不报错
      NULL;
    END;
    EXECUTE format('ALTER TABLE t_bad_debt_provision ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (START WITH 1)');
  END IF;

  -- t_bank_account (max_id=2065304748772200450, START WITH 2065304748772200451)
  SELECT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 't_bank_account'
      AND column_name = 'id' AND is_identity = 'YES'
  ) INTO v_already;
  IF NOT v_already THEN
    BEGIN
      EXECUTE format('ALTER TABLE t_bank_account ALTER COLUMN id DROP DEFAULT');
    EXCEPTION WHEN OTHERS THEN
      -- 无 DEFAULT 时不报错
      NULL;
    END;
    EXECUTE format('ALTER TABLE t_bank_account ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (START WITH 2065304748772200451)');
  END IF;

  -- t_bank_journal (max_id=0, START WITH 1)
  SELECT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 't_bank_journal'
      AND column_name = 'id' AND is_identity = 'YES'
  ) INTO v_already;
  IF NOT v_already THEN
    BEGIN
      EXECUTE format('ALTER TABLE t_bank_journal ALTER COLUMN id DROP DEFAULT');
    EXCEPTION WHEN OTHERS THEN
      -- 无 DEFAULT 时不报错
      NULL;
    END;
    EXECUTE format('ALTER TABLE t_bank_journal ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (START WITH 1)');
  END IF;

  -- t_beginning_control (max_id=0, START WITH 1)
  SELECT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 't_beginning_control'
      AND column_name = 'id' AND is_identity = 'YES'
  ) INTO v_already;
  IF NOT v_already THEN
    BEGIN
      EXECUTE format('ALTER TABLE t_beginning_control ALTER COLUMN id DROP DEFAULT');
    EXCEPTION WHEN OTHERS THEN
      -- 无 DEFAULT 时不报错
      NULL;
    END;
    EXECUTE format('ALTER TABLE t_beginning_control ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (START WITH 1)');
  END IF;

  -- t_budget (max_id=0, START WITH 1)
  SELECT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 't_budget'
      AND column_name = 'id' AND is_identity = 'YES'
  ) INTO v_already;
  IF NOT v_already THEN
    BEGIN
      EXECUTE format('ALTER TABLE t_budget ALTER COLUMN id DROP DEFAULT');
    EXCEPTION WHEN OTHERS THEN
      -- 无 DEFAULT 时不报错
      NULL;
    END;
    EXECUTE format('ALTER TABLE t_budget ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (START WITH 1)');
  END IF;

  -- t_budget_adjustment (max_id=0, START WITH 1)
  SELECT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 't_budget_adjustment'
      AND column_name = 'id' AND is_identity = 'YES'
  ) INTO v_already;
  IF NOT v_already THEN
    BEGIN
      EXECUTE format('ALTER TABLE t_budget_adjustment ALTER COLUMN id DROP DEFAULT');
    EXCEPTION WHEN OTHERS THEN
      -- 无 DEFAULT 时不报错
      NULL;
    END;
    EXECUTE format('ALTER TABLE t_budget_adjustment ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (START WITH 1)');
  END IF;

  -- t_budget_entry (max_id=0, START WITH 1)
  SELECT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 't_budget_entry'
      AND column_name = 'id' AND is_identity = 'YES'
  ) INTO v_already;
  IF NOT v_already THEN
    BEGIN
      EXECUTE format('ALTER TABLE t_budget_entry ALTER COLUMN id DROP DEFAULT');
    EXCEPTION WHEN OTHERS THEN
      -- 无 DEFAULT 时不报错
      NULL;
    END;
    EXECUTE format('ALTER TABLE t_budget_entry ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (START WITH 1)');
  END IF;

  -- t_budget_execution (max_id=0, START WITH 1)
  SELECT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 't_budget_execution'
      AND column_name = 'id' AND is_identity = 'YES'
  ) INTO v_already;
  IF NOT v_already THEN
    BEGIN
      EXECUTE format('ALTER TABLE t_budget_execution ALTER COLUMN id DROP DEFAULT');
    EXCEPTION WHEN OTHERS THEN
      -- 无 DEFAULT 时不报错
      NULL;
    END;
    EXECUTE format('ALTER TABLE t_budget_execution ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (START WITH 1)');
  END IF;

  -- t_cash_flow_rule (max_id=12, START WITH 13)
  SELECT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 't_cash_flow_rule'
      AND column_name = 'id' AND is_identity = 'YES'
  ) INTO v_already;
  IF NOT v_already THEN
    BEGIN
      EXECUTE format('ALTER TABLE t_cash_flow_rule ALTER COLUMN id DROP DEFAULT');
    EXCEPTION WHEN OTHERS THEN
      -- 无 DEFAULT 时不报错
      NULL;
    END;
    EXECUTE format('ALTER TABLE t_cash_flow_rule ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (START WITH 13)');
  END IF;

  -- t_cash_journal (max_id=0, START WITH 1)
  SELECT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 't_cash_journal'
      AND column_name = 'id' AND is_identity = 'YES'
  ) INTO v_already;
  IF NOT v_already THEN
    BEGIN
      EXECUTE format('ALTER TABLE t_cash_journal ALTER COLUMN id DROP DEFAULT');
    EXCEPTION WHEN OTHERS THEN
      -- 无 DEFAULT 时不报错
      NULL;
    END;
    EXECUTE format('ALTER TABLE t_cash_journal ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (START WITH 1)');
  END IF;

  -- t_classification_rule (max_id=26, START WITH 27)
  SELECT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 't_classification_rule'
      AND column_name = 'id' AND is_identity = 'YES'
  ) INTO v_already;
  IF NOT v_already THEN
    BEGIN
      EXECUTE format('ALTER TABLE t_classification_rule ALTER COLUMN id DROP DEFAULT');
    EXCEPTION WHEN OTHERS THEN
      -- 无 DEFAULT 时不报错
      NULL;
    END;
    EXECUTE format('ALTER TABLE t_classification_rule ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (START WITH 27)');
  END IF;

  -- t_dept (max_id=1, START WITH 2)
  SELECT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 't_dept'
      AND column_name = 'id' AND is_identity = 'YES'
  ) INTO v_already;
  IF NOT v_already THEN
    BEGIN
      EXECUTE format('ALTER TABLE t_dept ALTER COLUMN id DROP DEFAULT');
    EXCEPTION WHEN OTHERS THEN
      -- 无 DEFAULT 时不报错
      NULL;
    END;
    EXECUTE format('ALTER TABLE t_dept ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (START WITH 2)');
  END IF;

  -- t_financial_metric (max_id=10, START WITH 11)
  SELECT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 't_financial_metric'
      AND column_name = 'id' AND is_identity = 'YES'
  ) INTO v_already;
  IF NOT v_already THEN
    BEGIN
      EXECUTE format('ALTER TABLE t_financial_metric ALTER COLUMN id DROP DEFAULT');
    EXCEPTION WHEN OTHERS THEN
      -- 无 DEFAULT 时不报错
      NULL;
    END;
    EXECUTE format('ALTER TABLE t_financial_metric ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (START WITH 11)');
  END IF;

  -- t_menu (max_id=8022, START WITH 8023)
  SELECT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 't_menu'
      AND column_name = 'id' AND is_identity = 'YES'
  ) INTO v_already;
  IF NOT v_already THEN
    BEGIN
      EXECUTE format('ALTER TABLE t_menu ALTER COLUMN id DROP DEFAULT');
    EXCEPTION WHEN OTHERS THEN
      -- 无 DEFAULT 时不报错
      NULL;
    END;
    EXECUTE format('ALTER TABLE t_menu ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (START WITH 8023)');
  END IF;

  -- t_payable (max_id=0, START WITH 1)
  SELECT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 't_payable'
      AND column_name = 'id' AND is_identity = 'YES'
  ) INTO v_already;
  IF NOT v_already THEN
    BEGIN
      EXECUTE format('ALTER TABLE t_payable ALTER COLUMN id DROP DEFAULT');
    EXCEPTION WHEN OTHERS THEN
      -- 无 DEFAULT 时不报错
      NULL;
    END;
    EXECUTE format('ALTER TABLE t_payable ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (START WITH 1)');
  END IF;

  -- t_prepayment (max_id=2070059991926026242, START WITH 2070059991926026243)
  SELECT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 't_prepayment'
      AND column_name = 'id' AND is_identity = 'YES'
  ) INTO v_already;
  IF NOT v_already THEN
    BEGIN
      EXECUTE format('ALTER TABLE t_prepayment ALTER COLUMN id DROP DEFAULT');
    EXCEPTION WHEN OTHERS THEN
      -- 无 DEFAULT 时不报错
      NULL;
    END;
    EXECUTE format('ALTER TABLE t_prepayment ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (START WITH 2070059991926026243)');
  END IF;

  -- t_receivable (max_id=0, START WITH 1)
  SELECT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 't_receivable'
      AND column_name = 'id' AND is_identity = 'YES'
  ) INTO v_already;
  IF NOT v_already THEN
    BEGIN
      EXECUTE format('ALTER TABLE t_receivable ALTER COLUMN id DROP DEFAULT');
    EXCEPTION WHEN OTHERS THEN
      -- 无 DEFAULT 时不报错
      NULL;
    END;
    EXECUTE format('ALTER TABLE t_receivable ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (START WITH 1)');
  END IF;

  -- t_reconciliation_suggestion (max_id=0, START WITH 1)
  SELECT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 't_reconciliation_suggestion'
      AND column_name = 'id' AND is_identity = 'YES'
  ) INTO v_already;
  IF NOT v_already THEN
    BEGIN
      EXECUTE format('ALTER TABLE t_reconciliation_suggestion ALTER COLUMN id DROP DEFAULT');
    EXCEPTION WHEN OTHERS THEN
      -- 无 DEFAULT 时不报错
      NULL;
    END;
    EXECUTE format('ALTER TABLE t_reconciliation_suggestion ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (START WITH 1)');
  END IF;

  -- t_report_template (max_id=0, START WITH 1)
  SELECT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 't_report_template'
      AND column_name = 'id' AND is_identity = 'YES'
  ) INTO v_already;
  IF NOT v_already THEN
    BEGIN
      EXECUTE format('ALTER TABLE t_report_template ALTER COLUMN id DROP DEFAULT');
    EXCEPTION WHEN OTHERS THEN
      -- 无 DEFAULT 时不报错
      NULL;
    END;
    EXECUTE format('ALTER TABLE t_report_template ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (START WITH 1)');
  END IF;

  -- t_role (max_id=2065597443457572866, START WITH 2065597443457572867)
  SELECT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 't_role'
      AND column_name = 'id' AND is_identity = 'YES'
  ) INTO v_already;
  IF NOT v_already THEN
    BEGIN
      EXECUTE format('ALTER TABLE t_role ALTER COLUMN id DROP DEFAULT');
    EXCEPTION WHEN OTHERS THEN
      -- 无 DEFAULT 时不报错
      NULL;
    END;
    EXECUTE format('ALTER TABLE t_role ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (START WITH 2065597443457572867)');
  END IF;

  -- t_role_menu (max_id=2065597445886074886, START WITH 2065597445886074887)
  SELECT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 't_role_menu'
      AND column_name = 'id' AND is_identity = 'YES'
  ) INTO v_already;
  IF NOT v_already THEN
    BEGIN
      EXECUTE format('ALTER TABLE t_role_menu ALTER COLUMN id DROP DEFAULT');
    EXCEPTION WHEN OTHERS THEN
      -- 无 DEFAULT 时不报错
      NULL;
    END;
    EXECUTE format('ALTER TABLE t_role_menu ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (START WITH 2065597445886074887)');
  END IF;

  -- t_subject_balance (max_id=0, START WITH 1)
  SELECT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 't_subject_balance'
      AND column_name = 'id' AND is_identity = 'YES'
  ) INTO v_already;
  IF NOT v_already THEN
    BEGIN
      EXECUTE format('ALTER TABLE t_subject_balance ALTER COLUMN id DROP DEFAULT');
    EXCEPTION WHEN OTHERS THEN
      -- 无 DEFAULT 时不报错
      NULL;
    END;
    EXECUTE format('ALTER TABLE t_subject_balance ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (START WITH 1)');
  END IF;

  -- t_summary_lib (max_id=15, START WITH 16)
  SELECT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 't_summary_lib'
      AND column_name = 'id' AND is_identity = 'YES'
  ) INTO v_already;
  IF NOT v_already THEN
    BEGIN
      EXECUTE format('ALTER TABLE t_summary_lib ALTER COLUMN id DROP DEFAULT');
    EXCEPTION WHEN OTHERS THEN
      -- 无 DEFAULT 时不报错
      NULL;
    END;
    EXECUTE format('ALTER TABLE t_summary_lib ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (START WITH 16)');
  END IF;

  -- t_sys_config (max_id=14, START WITH 15)
  SELECT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 't_sys_config'
      AND column_name = 'id' AND is_identity = 'YES'
  ) INTO v_already;
  IF NOT v_already THEN
    BEGIN
      EXECUTE format('ALTER TABLE t_sys_config ALTER COLUMN id DROP DEFAULT');
    EXCEPTION WHEN OTHERS THEN
      -- 无 DEFAULT 时不报错
      NULL;
    END;
    EXECUTE format('ALTER TABLE t_sys_config ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (START WITH 15)');
  END IF;

  -- t_tax_carry_over (max_id=0, START WITH 1)
  SELECT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 't_tax_carry_over'
      AND column_name = 'id' AND is_identity = 'YES'
  ) INTO v_already;
  IF NOT v_already THEN
    BEGIN
      EXECUTE format('ALTER TABLE t_tax_carry_over ALTER COLUMN id DROP DEFAULT');
    EXCEPTION WHEN OTHERS THEN
      -- 无 DEFAULT 时不报错
      NULL;
    END;
    EXECUTE format('ALTER TABLE t_tax_carry_over ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (START WITH 1)');
  END IF;

  -- t_tax_declaration (max_id=0, START WITH 1)
  SELECT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 't_tax_declaration'
      AND column_name = 'id' AND is_identity = 'YES'
  ) INTO v_already;
  IF NOT v_already THEN
    BEGIN
      EXECUTE format('ALTER TABLE t_tax_declaration ALTER COLUMN id DROP DEFAULT');
    EXCEPTION WHEN OTHERS THEN
      -- 无 DEFAULT 时不报错
      NULL;
    END;
    EXECUTE format('ALTER TABLE t_tax_declaration ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (START WITH 1)');
  END IF;

  -- t_tax_type (max_id=9, START WITH 10)
  SELECT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 't_tax_type'
      AND column_name = 'id' AND is_identity = 'YES'
  ) INTO v_already;
  IF NOT v_already THEN
    BEGIN
      EXECUTE format('ALTER TABLE t_tax_type ALTER COLUMN id DROP DEFAULT');
    EXCEPTION WHEN OTHERS THEN
      -- 无 DEFAULT 时不报错
      NULL;
    END;
    EXECUTE format('ALTER TABLE t_tax_type ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (START WITH 10)');
  END IF;

  -- t_ticket (max_id=0, START WITH 1)
  SELECT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 't_ticket'
      AND column_name = 'id' AND is_identity = 'YES'
  ) INTO v_already;
  IF NOT v_already THEN
    BEGIN
      EXECUTE format('ALTER TABLE t_ticket ALTER COLUMN id DROP DEFAULT');
    EXCEPTION WHEN OTHERS THEN
      -- 无 DEFAULT 时不报错
      NULL;
    END;
    EXECUTE format('ALTER TABLE t_ticket ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (START WITH 1)');
  END IF;

  -- t_ticket_transaction (max_id=0, START WITH 1)
  SELECT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 't_ticket_transaction'
      AND column_name = 'id' AND is_identity = 'YES'
  ) INTO v_already;
  IF NOT v_already THEN
    BEGIN
      EXECUTE format('ALTER TABLE t_ticket_transaction ALTER COLUMN id DROP DEFAULT');
    EXCEPTION WHEN OTHERS THEN
      -- 无 DEFAULT 时不报错
      NULL;
    END;
    EXECUTE format('ALTER TABLE t_ticket_transaction ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (START WITH 1)');
  END IF;

  -- t_user (max_id=2065597735892836354, START WITH 2065597735892836355)
  SELECT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 't_user'
      AND column_name = 'id' AND is_identity = 'YES'
  ) INTO v_already;
  IF NOT v_already THEN
    BEGIN
      EXECUTE format('ALTER TABLE t_user ALTER COLUMN id DROP DEFAULT');
    EXCEPTION WHEN OTHERS THEN
      -- 无 DEFAULT 时不报错
      NULL;
    END;
    EXECUTE format('ALTER TABLE t_user ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (START WITH 2065597735892836355)');
  END IF;

  -- t_user_role (max_id=1, START WITH 2)
  SELECT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 't_user_role'
      AND column_name = 'id' AND is_identity = 'YES'
  ) INTO v_already;
  IF NOT v_already THEN
    BEGIN
      EXECUTE format('ALTER TABLE t_user_role ALTER COLUMN id DROP DEFAULT');
    EXCEPTION WHEN OTHERS THEN
      -- 无 DEFAULT 时不报错
      NULL;
    END;
    EXECUTE format('ALTER TABLE t_user_role ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (START WITH 2)');
  END IF;

  -- t_voucher_cash_flow (max_id=0, START WITH 1)
  SELECT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 't_voucher_cash_flow'
      AND column_name = 'id' AND is_identity = 'YES'
  ) INTO v_already;
  IF NOT v_already THEN
    BEGIN
      EXECUTE format('ALTER TABLE t_voucher_cash_flow ALTER COLUMN id DROP DEFAULT');
    EXCEPTION WHEN OTHERS THEN
      -- 无 DEFAULT 时不报错
      NULL;
    END;
    EXECUTE format('ALTER TABLE t_voucher_cash_flow ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (START WITH 1)');
  END IF;

  -- t_voucher_type (max_id=4, START WITH 5)
  SELECT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 't_voucher_type'
      AND column_name = 'id' AND is_identity = 'YES'
  ) INTO v_already;
  IF NOT v_already THEN
    BEGIN
      EXECUTE format('ALTER TABLE t_voucher_type ALTER COLUMN id DROP DEFAULT');
    EXCEPTION WHEN OTHERS THEN
      -- 无 DEFAULT 时不报错
      NULL;
    END;
    EXECUTE format('ALTER TABLE t_voucher_type ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (START WITH 5)');
  END IF;

END $$;

-- ============================================================
-- 验证: 重查 identity 状态, 期望 65 张表全部 = YES
-- ============================================================
-- docker exec huicai-postgres psql -U huicai -d huicai -c "
--   SELECT COUNT(*) FROM information_schema.columns
--   WHERE table_schema = 'public' AND column_name = 'id' AND is_identity = 'YES';
-- "  -- 期望: 65
