-- ============================================================
-- V11: 报表物化视图与自定义报表
-- ============================================================

-- 1. 自定义报表模板
CREATE TABLE IF NOT EXISTS t_report_template (
    id              BIGINT PRIMARY KEY,
    template_code   VARCHAR(32)  NOT NULL,
    template_name   VARCHAR(100) NOT NULL,
    report_type     VARCHAR(32)  NOT NULL,
    config          JSONB        NOT NULL,
    is_system       BOOLEAN      DEFAULT FALSE,
    created_by      BIGINT,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT uq_report_template_code UNIQUE (template_code)
);

COMMENT ON TABLE  t_report_template IS '自定义报表模板';
COMMENT ON COLUMN t_report_template.config IS '报表配置 JSON: 行/列/数据源/筛选等';

-- 2. 现金流量分配规则
CREATE TABLE IF NOT EXISTS t_cash_flow_rule (
    id              BIGINT PRIMARY KEY,
    code            VARCHAR(32)  NOT NULL,
    name            VARCHAR(100) NOT NULL,
    flow_type       VARCHAR(20)  NOT NULL,
    match_subject   VARCHAR(200) NOT NULL,
    flow_item       VARCHAR(64)  NOT NULL,
    priority        INTEGER      DEFAULT 1,
    is_active       BOOLEAN      DEFAULT TRUE,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT uq_cf_rule_code UNIQUE (code),
    CONSTRAINT chk_cf_flow_type CHECK (flow_type IN ('OPERATING_IN', 'OPERATING_OUT', 'INVESTING_IN', 'INVESTING_OUT', 'FINANCING_IN', 'FINANCING_OUT'))
);

COMMENT ON TABLE  t_cash_flow_rule IS '现金流量分配规则';
COMMENT ON COLUMN t_cash_flow_rule.match_subject IS '匹配的科目编码(支持通配符)';
COMMENT ON COLUMN t_cash_flow_rule.flow_item IS '现金流量项目';

-- 3. 凭证现金流量分配
CREATE TABLE IF NOT EXISTS t_voucher_cash_flow (
    id              BIGINT PRIMARY KEY,
    voucher_id      BIGINT       NOT NULL,
    entry_id        BIGINT       NOT NULL,
    flow_type       VARCHAR(20)  NOT NULL,
    flow_item       VARCHAR(64)  NOT NULL,
    amount          NUMERIC(18,2) NOT NULL,
    is_manual       BOOLEAN      DEFAULT FALSE,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_cf_voucher FOREIGN KEY (voucher_id) REFERENCES t_voucher(id),
    CONSTRAINT fk_cf_entry FOREIGN KEY (entry_id) REFERENCES t_voucher_entry(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_cf_voucher ON t_voucher_cash_flow(voucher_id);

COMMENT ON TABLE  t_voucher_cash_flow IS '凭证现金流量分配';

-- 4. 财务指标定义
CREATE TABLE IF NOT EXISTS t_financial_metric (
    id              BIGINT PRIMARY KEY,
    metric_code     VARCHAR(32)  NOT NULL,
    metric_name     VARCHAR(100) NOT NULL,
    category        VARCHAR(32)  NOT NULL,
    formula         TEXT         NOT NULL,
    unit            VARCHAR(16)  DEFAULT '%',
    description     VARCHAR(500),
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT uq_metric_code UNIQUE (metric_code)
);

COMMENT ON TABLE  t_financial_metric IS '财务指标定义';
COMMENT ON COLUMN t_financial_metric.formula IS '指标公式(可使用科目编码与运算符)';
COMMENT ON COLUMN t_financial_metric.category IS '指标分类: PROFITABILITY, SOLVENCY, OPERATION, GROWTH';

-- 5. 预警规则
CREATE TABLE IF NOT EXISTS t_alert_rule (
    id              BIGINT PRIMARY KEY,
    rule_code       VARCHAR(32)  NOT NULL,
    rule_name       VARCHAR(100) NOT NULL,
    metric_code     VARCHAR(32)  NOT NULL,
    comparator      VARCHAR(10)  NOT NULL,
    threshold       NUMERIC(18,4) NOT NULL,
    severity        VARCHAR(20)  DEFAULT 'MEDIUM',
    notify_channels VARCHAR(200),
    is_active       BOOLEAN      DEFAULT TRUE,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT uq_alert_code UNIQUE (rule_code),
    CONSTRAINT chk_alert_comparator CHECK (comparator IN ('GT', 'GTE', 'LT', 'LTE', 'EQ'))
);

COMMENT ON TABLE  t_alert_rule IS '预警规则';
COMMENT ON COLUMN t_alert_rule.notify_channels IS '通知渠道(逗号分隔): EMAIL, SMS, SITE';

-- 6. 物化视图: 科目余额(加速报表)
CREATE MATERIALIZED VIEW IF NOT EXISTS mv_subject_balance AS
SELECT
    subject_id,
    period,
    SUM(begin_balance)   AS begin_balance,
    SUM(debit_total)     AS debit_total,
    SUM(credit_total)    AS credit_total,
    SUM(end_balance)     AS end_balance
FROM t_subject_balance
GROUP BY subject_id, period;

CREATE UNIQUE INDEX IF NOT EXISTS idx_mv_sb_subject_period ON mv_subject_balance(subject_id, period);

COMMENT ON MATERIALIZED VIEW mv_subject_balance IS '科目余额物化视图(报表加速)';

-- 7. 物化视图: 期间凭证汇总
CREATE MATERIALIZED VIEW IF NOT EXISTS mv_period_voucher AS
SELECT
    v.period,
    v.status,
    COUNT(*)             AS voucher_count,
    SUM(v.total_debit)   AS total_debit,
    SUM(v.total_credit)  AS total_credit
FROM t_voucher v
WHERE v.status = 'POSTED' AND v.deleted = 0
GROUP BY v.period, v.status;

CREATE UNIQUE INDEX IF NOT EXISTS idx_mv_pv_period_status ON mv_period_voucher(period, status);

COMMENT ON MATERIALIZED VIEW mv_period_voucher IS '期间凭证汇总物化视图';

-- 8. 现金流量项目定义(预置)
INSERT INTO t_cash_flow_rule (id, code, name, flow_type, match_subject, flow_item, priority) VALUES
(1,  'CF001', '销售商品收到现金',  'OPERATING_IN',  '1001*',     'SALE_GOODS',  1),
(2,  'CF002', '购买商品支付现金',  'OPERATING_OUT', '1001*',     'BUY_GOODS',   1),
(3,  'CF003', '支付职工薪酬',     'OPERATING_OUT', '2211*',     'PAY_EMPLOYEE',1),
(4,  'CF004', '支付各项税费',     'OPERATING_OUT', '2221*',     'PAY_TAX',     1),
(5,  'CF005', '购买固定资产',     'INVESTING_OUT', '1601*',     'BUY_FIXED',   1),
(6,  'CF006', '取得借款',        'FINANCING_IN',  '2001*',     'BORROW',      1),
(7,  'CF007', '偿还债务',        'FINANCING_OUT', '2001*',     'REPAY',       1),
(8,  'CF008', '分配股利支付现金',  'FINANCING_OUT', '2232*',     'PAY_DIVIDEND',1)
ON CONFLICT (code) DO NOTHING;

-- 9. 预置财务指标
INSERT INTO t_financial_metric (id, metric_code, metric_name, category, formula, unit, description) VALUES
(1,  'GROSS_MARGIN',    '毛利率',     'PROFITABILITY', '(营业收入 - 营业成本) / 营业收入 * 100',  '%', '反映企业产品销售的初始获利能力'),
(2,  'NET_MARGIN',      '净利率',     'PROFITABILITY', '净利润 / 营业收入 * 100',                '%', '反映企业最终盈利能力'),
(3,  'ROA',             '资产回报率',  'PROFITABILITY', '净利润 / 总资产 * 100',                  '%', '总资产回报率'),
(4,  'ROE',             '净资产收益率', 'PROFITABILITY', '净利润 / 净资产 * 100',                  '%', '股东权益回报率'),
(5,  'CURRENT_RATIO',   '流动比率',   'SOLVENCY',      '流动资产 / 流动负债 * 100',              '%', '短期偿债能力'),
(6,  'QUICK_RATIO',     '速动比率',   'SOLVENCY',      '(流动资产 - 存货) / 流动负债 * 100',     '%', '速动偿债能力'),
(7,  'DEBT_RATIO',      '资产负债率', 'SOLVENCY',      '总负债 / 总资产 * 100',                  '%', '长期偿债能力'),
(8,  'AR_TURNover',     '应收账款周转率', 'OPERATION',   '营业收入 / 应收账款平均余额',             '次', '应收周转效率'),
(9,  'INVENTORY_TURN',  '存货周转率',  'OPERATION',     '营业成本 / 存货平均余额',                 '次', '存货周转效率'),
(10, 'REVENUE_GROWTH',  '营收增长率',  'GROWTH',        '(本期收入 - 上期收入) / 上期收入 * 100', '%', '营业收入增长')
ON CONFLICT (metric_code) DO NOTHING;
