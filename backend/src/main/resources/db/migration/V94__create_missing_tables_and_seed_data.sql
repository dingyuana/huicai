-- ============================================================
-- V94: 创建 14 张缺失表（Entity 已定义但无 migration）
-- 根因：这些表在开发过程中直接写了 Entity 但未创建 CREATE TABLE migration
-- 说明：所有表使用 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY，
--       与 Entity 的 @TableId(type = IdType.AUTO) 对齐
-- ============================================================

-- 1. t_ai_anomaly_tag — AI 异常标记
CREATE TABLE t_ai_anomaly_tag (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    biz_type VARCHAR(50),
    biz_id BIGINT,
    anomaly_type VARCHAR(50),
    severity VARCHAR(20),
    description TEXT,
    ai_task_id BIGINT,
    resolved BOOLEAN DEFAULT FALSE,
    resolved_by BIGINT,
    resolved_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 2. t_cash_flow_rule — 现金流分类规则
CREATE TABLE t_cash_flow_rule (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(200) NOT NULL,
    flow_type VARCHAR(20),
    match_subject VARCHAR(200),
    flow_item VARCHAR(100),
    priority INT DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted INT NOT NULL DEFAULT 0,
    CONSTRAINT uq_cash_flow_rule_code UNIQUE (code)
);

-- 3. t_financial_metric — 财务指标库
CREATE TABLE t_financial_metric (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    metric_code VARCHAR(50) NOT NULL,
    metric_name VARCHAR(200) NOT NULL,
    category VARCHAR(50),
    formula TEXT,
    unit VARCHAR(50),
    description TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted INT NOT NULL DEFAULT 0,
    CONSTRAINT uq_financial_metric_code UNIQUE (metric_code)
);

-- 4. t_attachment — 附件管理
CREATE TABLE t_attachment (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    biz_type VARCHAR(50),
    biz_id BIGINT,
    file_name VARCHAR(200),
    original_name VARCHAR(200),
    file_path VARCHAR(500),
    bucket_name VARCHAR(100),
    file_size BIGINT,
    content_type VARCHAR(100),
    file_hash VARCHAR(100),
    ocr_data TEXT,
    vector TEXT,
    uploaded_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted INT NOT NULL DEFAULT 0
);

-- 5. t_voucher_template_line — 凭证模板分录行
CREATE TABLE t_voucher_template_line (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    template_id BIGINT NOT NULL,
    subject_id BIGINT NOT NULL,
    dr_amount_template TEXT,
    cr_amount_template TEXT,
    summary_template TEXT,
    direction VARCHAR(10) CHECK (direction IN ('debit', 'credit')),
    assist_type VARCHAR(20) CHECK (assist_type IN ('CUSTOMER', 'VENDOR', 'DEPT', 'EMPLOYEE', 'PROJECT')),
    assist_required BOOLEAN DEFAULT FALSE,
    line_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted INT NOT NULL DEFAULT 0,
    CONSTRAINT fk_vtl_template FOREIGN KEY (template_id) REFERENCES t_voucher_template(id),
    CONSTRAINT fk_vtl_subject FOREIGN KEY (subject_id) REFERENCES t_subject(id)
);

-- 6. t_bad_debt_provision_detail — 坏账计提明细
CREATE TABLE t_bad_debt_provision_detail (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    provision_id BIGINT NOT NULL,
    source_type VARCHAR(50) NOT NULL CHECK (source_type IN ('INVOICE_OUT', 'OTHER_RECEIVABLE', 'NOTE_RECEIVABLE', 'PREPAYMENT')),
    source_id BIGINT,
    customer_id BIGINT,
    doc_no VARCHAR(100),
    due_date DATE,
    unsettled_amount NUMERIC(18,2),
    aging_bucket VARCHAR(30),
    ratio NUMERIC(5,4),
    provision_amount NUMERIC(18,2),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_bad_debt_detail_provision FOREIGN KEY (provision_id) REFERENCES t_bad_debt_provision(id)
);

-- 7. t_bad_debt_provision_scheme — 坏账计提方案
CREATE TABLE t_bad_debt_provision_scheme (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    method VARCHAR(20) NOT NULL CHECK (method IN ('AGING_RATIO', 'PERCENTAGE')),
    is_default BOOLEAN DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    remark TEXT,
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted INT NOT NULL DEFAULT 0
);

-- 8. t_bad_debt_provision_scheme_item — 坏账计提方案明细
CREATE TABLE t_bad_debt_provision_scheme_item (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    scheme_id BIGINT NOT NULL,
    bucket_name VARCHAR(30) NOT NULL CHECK (bucket_name IN ('current', 'days_0_30', 'days_31_60', 'days_61_90', 'days_91_180', 'days_181_365', 'over_365')),
    ratio NUMERIC(5,4) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_scheme_item_scheme FOREIGN KEY (scheme_id) REFERENCES t_bad_debt_provision_scheme(id)
);

-- 9. t_reconciliation_dispute — 客户对账单争议
CREATE TABLE t_reconciliation_dispute (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    statement_id BIGINT,
    customer_id BIGINT,
    doc_no VARCHAR(100),
    dispute_type VARCHAR(50),
    expected_amount NUMERIC(18,2),
    actual_amount NUMERIC(18,2),
    diff_amount NUMERIC(18,2),
    reason TEXT,
    resolution TEXT,
    resolved_by BIGINT,
    resolved_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 10. t_reconciliation_outstanding — 客户对账单未达项
CREATE TABLE t_reconciliation_outstanding (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    customer_id BIGINT,
    statement_id BIGINT,
    outstanding_type VARCHAR(50),
    amount NUMERIC(18,2),
    description TEXT,
    evidence TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    resolved_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted INT NOT NULL DEFAULT 0
);

-- 11. t_purchase_return — 采购退货
CREATE TABLE t_purchase_return (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    return_no VARCHAR(100),
    vendor_id BIGINT,
    original_doc_no VARCHAR(100),
    original_doc_id BIGINT,
    return_amount NUMERIC(18,2),
    tax_amount NUMERIC(18,2),
    reason TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    voucher_id BIGINT,
    voucher_no VARCHAR(100),
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted INT NOT NULL DEFAULT 0,
    CONSTRAINT fk_purchase_return_vendor FOREIGN KEY (vendor_id) REFERENCES t_vendor(id)
);

-- 12. t_reconciliation_log — 核销日志（关键！核销模块依赖此表）
CREATE TABLE t_reconciliation_log (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    source_doc_type VARCHAR(50) NOT NULL,
    source_doc_id BIGINT NOT NULL,
    target_doc_type VARCHAR(50) NOT NULL,
    target_doc_id BIGINT NOT NULL,
    allocated_amount NUMERIC(18,2),
    discount_amount NUMERIC(18,2),
    target_business_doc_id BIGINT,
    match_score NUMERIC(5,4),
    match_method VARCHAR(20) CHECK (match_method IN ('AUTO', 'MANUAL')),
    status VARCHAR(20) NOT NULL DEFAULT 'CONFIRMED',
    operation_type VARCHAR(20),
    rule_id VARCHAR(100),
    remark TEXT,
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_reconciliation_status CHECK (status IN ('CONFIRMED', 'EXECUTED', 'REJECTED', 'CANCELLED'))
);
CREATE INDEX idx_reconciliation_log_source ON t_reconciliation_log(source_doc_type, source_doc_id);
CREATE INDEX idx_reconciliation_log_target ON t_reconciliation_log(target_doc_type, target_doc_id);

-- 13. t_budget_adjustment — 预算调整
CREATE TABLE t_budget_adjustment (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    adjustment_no VARCHAR(100),
    budget_id BIGINT NOT NULL,
    adjustment_type VARCHAR(20),
    adjustment_date DATE,
    period VARCHAR(7),
    adjustment_amount NUMERIC(18,2),
    reason TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    approved_by BIGINT,
    approved_at TIMESTAMP,
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted INT NOT NULL DEFAULT 0,
    CONSTRAINT fk_budget_adjustment_budget FOREIGN KEY (budget_id) REFERENCES t_budget(id)
);

-- 14. t_budget_entry — 预算明细
CREATE TABLE t_budget_entry (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    budget_id BIGINT NOT NULL,
    subject_id BIGINT NOT NULL,
    amount NUMERIC(18,2) NOT NULL DEFAULT 0,
    period VARCHAR(7),
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_budget_entry_budget FOREIGN KEY (budget_id) REFERENCES t_budget(id),
    CONSTRAINT fk_budget_entry_subject FOREIGN KEY (subject_id) REFERENCES t_subject(id)
);

-- ============================================================
-- 恢复种子数据
-- ============================================================

-- 常用摘要库（t_summary_lib）— 幂等：已存在则跳过
INSERT INTO t_summary_lib (summary_code, summary_text, category, sort_order, is_active, deleted)
SELECT * FROM (VALUES
    ('INCOME', '收到货款', '收入', 1, TRUE, 0),
    ('INCOME_02', '收到预付款', '收入', 2, TRUE, 0),
    ('INCOME_03', '收到退款', '收入', 3, TRUE, 0),
    ('INCOME_04', '收到利息收入', '收入', 4, TRUE, 0),
    ('EXPENSE', '支付货款', '费用', 10, TRUE, 0),
    ('EXPENSE_02', '支付预付款', '费用', 11, TRUE, 0),
    ('EXPENSE_03', '支付工资', '费用', 12, TRUE, 0),
    ('EXPENSE_04', '支付社保', '费用', 13, TRUE, 0),
    ('EXPENSE_05', '支付税费', '费用', 14, TRUE, 0),
    ('EXPENSE_06', '支付银行手续费', '费用', 15, TRUE, 0),
    ('EXPENSE_07', '支付租金', '费用', 16, TRUE, 0),
    ('EXPENSE_08', '支付水电费', '费用', 17, TRUE, 0),
    ('TRANSFER', '内部转账', '转账', 20, TRUE, 0),
    ('TRANSFER_02', '银行间转账', '转账', 21, TRUE, 0),
    ('OTHER', '其它收支', '其它', 30, TRUE, 0)
) AS v (summary_code, summary_text, category, sort_order, is_active, deleted)
WHERE NOT EXISTS (SELECT 1 FROM t_summary_lib WHERE t_summary_lib.summary_code = v.summary_code AND t_summary_lib.deleted = 0);

-- 凭证类型（t_voucher_type）— 补充 SK/FK/ZZ，与 VoucherType 常量对应
-- 幂等：已存在则跳过
-- 注：id 列为 GENERATED ALWAYS AS IDENTITY，需 OVERRIDING SYSTEM VALUE 显式指定 id
-- 同时 t_voucher_type 已有 id=1 (JZ)，IDENTITY 序列起始值为 2，OVERRIDING SYSTEM VALUE 允许覆盖
-- PostgreSQL 语法：OVERRIDING 放在列名列表之后、VALUES 之前
INSERT INTO t_voucher_type (id, code, name, sort_order, numbering_rule, is_active, deleted) OVERRIDING SYSTEM VALUE
VALUES
    (2, 'SK', '收款凭证', 2, 'SK-{year}{month}-{serial}', TRUE, 0),
    (3, 'FK', '付款凭证', 3, 'FK-{year}{month}-{serial}', TRUE, 0),
    (4, 'ZZ', '转账凭证', 4, 'ZZ-{year}{month}-{serial}', TRUE, 0);

-- 分类规则（t_classification_rule）— 8 条种子规则，与 seedForNewTenant 一致
-- 幂等：已存在则跳过
INSERT INTO t_classification_rule (tenant_id, name, rule_type, pattern, match_field, direction, classification, priority, is_active, is_system, deleted)
SELECT * FROM (VALUES
    (1, '银行利息与手续费', 'keyword_regex', '手续费|工本费|年费|账户管理费|利息|结息|存款利息', 'description', NULL, 'bank_interest_fee', 1, TRUE, FALSE, 0),
    (1, '业务收款', 'keyword_regex', '货款|收款|销售|回款|客户|应收|收入', 'description', 'in', 'business_receipt', 2, TRUE, FALSE, 0),
    (1, '业务付款', 'keyword_regex', '货款|付款|采购|支付|供应商|应付|支出', 'description', 'out', 'business_payment', 3, TRUE, FALSE, 0),
    (1, '内部转账', 'keyword_regex', '转账|转存|调拨|上划|下拨', 'description', NULL, 'internal_transfer', 4, TRUE, FALSE, 0),
    (1, '税费扣缴', 'keyword_regex', '税|税务|缴税|税金|税款|增值税|所得税|城建税|教育费附加|国家金库|国库|印花', 'description', 'out', 'tax_withholding', 5, TRUE, FALSE, 0),
    (1, '薪酬与社保', 'keyword_regex', '工资|薪酬|社保|公积金|养老|医疗|失业|工伤|生育|代扣|个税', 'description', 'out', 'salary_social', 6, TRUE, FALSE, 0),
    (1, '筹资与投资活动', 'keyword_regex', '借款|还款|贷款|理财|投资|融资|分红|股本|债券', 'description', NULL, 'financing_invest', 7, TRUE, FALSE, 0),
    (1, '其它/待认领', 'keyword_regex', '', 'description', NULL, 'other_unknown', 8, TRUE, FALSE, 0)
) AS v (tenant_id, name, rule_type, pattern, match_field, direction, classification, priority, is_active, is_system, deleted)
WHERE NOT EXISTS (SELECT 1 FROM t_classification_rule cr WHERE cr.tenant_id = v.tenant_id AND cr.name = v.name AND cr.deleted = 0);
