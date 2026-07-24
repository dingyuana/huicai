-- ============================================================
-- V111: 创建现金流量表中间表
-- 用于存储按凭证分配的现金流量数据，支撑现金流量表报表
-- ============================================================

CREATE TABLE IF NOT EXISTS t_voucher_cash_flow (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    voucher_id      BIGINT        NOT NULL,
    flow_type       VARCHAR(30)   NOT NULL,
    amount          NUMERIC(18,2) NOT NULL DEFAULT 0,
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    enterprise_id   BIGINT        NOT NULL DEFAULT 1,
    CONSTRAINT fk_cf_voucher FOREIGN KEY (voucher_id) REFERENCES t_voucher(id)
);

COMMENT ON TABLE  t_voucher_cash_flow IS '凭证现金流量分配';
COMMENT ON COLUMN t_voucher_cash_flow.voucher_id IS '凭证ID';
COMMENT ON COLUMN t_voucher_cash_flow.flow_type IS '流量类型: OPERATING_IN/OUT, INVESTING_IN/OUT, FINANCING_IN/OUT';
COMMENT ON COLUMN t_voucher_cash_flow.amount IS '金额';

CREATE INDEX IF NOT EXISTS idx_cf_voucher ON t_voucher_cash_flow(voucher_id);
CREATE INDEX IF NOT EXISTS idx_cf_flow_type ON t_voucher_cash_flow(flow_type);
CREATE INDEX IF NOT EXISTS idx_t_voucher_cash_flow_enterprise ON t_voucher_cash_flow(enterprise_id);

-- 启用行级安全策略
ALTER TABLE t_voucher_cash_flow ENABLE ROW LEVEL SECURITY;
ALTER TABLE t_voucher_cash_flow FORCE ROW LEVEL SECURITY;

CREATE POLICY enterprise_policy ON t_voucher_cash_flow
    USING (enterprise_id = (current_setting('app.enterprise_id', true))::bigint);