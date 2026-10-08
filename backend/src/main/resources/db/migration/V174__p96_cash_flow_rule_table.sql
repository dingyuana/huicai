-- ============================================================
-- V174: P96 REQ-095 — 现金流量项目规则表
-- 用于按借贷科目组合自动分配现金流类型（经营/投资/筹资）
-- 人工指定优先于规则（铁律 #1：人是唯一审核主体）
-- ============================================================

CREATE TABLE IF NOT EXISTS t_cash_flow_rule (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    debit_subject_code  VARCHAR(20)   NOT NULL,  -- 借方科目编码
    credit_subject_code VARCHAR(20)   NOT NULL,  -- 贷方科目编码
    flow_type       VARCHAR(30)   NOT NULL,      -- OPERATING_IN/OUT, INVESTING_IN/OUT, FINANCING_IN/OUT
    priority        INT           NOT NULL DEFAULT 0,  -- 优先级，越大越优先
    enabled         BOOLEAN       NOT NULL DEFAULT TRUE,
    remark          VARCHAR(200),
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    enterprise_id   BIGINT        NOT NULL DEFAULT 1,
    CONSTRAINT uk_cf_rule_subject UNIQUE (debit_subject_code, credit_subject_code, enterprise_id)
);

COMMENT ON TABLE  t_cash_flow_rule IS '现金流量项目分配规则';
COMMENT ON COLUMN t_cash_flow_rule.debit_subject_code IS '借方科目编码';
COMMENT ON COLUMN t_cash_flow_rule.credit_subject_code IS '贷方科目编码';
COMMENT ON COLUMN t_cash_flow_rule.flow_type IS '流量类型: OPERATING_IN/OUT, INVESTING_IN/OUT, FINANCING_IN/OUT';
COMMENT ON COLUMN t_cash_flow_rule.priority IS '优先级，越大越优先';

-- 初始化核心规则（筹资 + 投资修正）
INSERT INTO t_cash_flow_rule (debit_subject_code, credit_subject_code, flow_type, priority, remark) VALUES
  -- 筹资活动：借款/资本注入（现金流入）
  ('1002', '2001', 'FINANCING_IN', 100, '取得短期借款'),
  ('1002', '2501', 'FINANCING_IN', 100, '取得长期借款'),
  ('1002', '4001', 'FINANCING_IN', 100, '实收资本注入'),
  ('1002', '4101', 'FINANCING_IN', 90,  '资本公积增加'),
  -- 筹资活动：还款/股利（现金流出）
  ('2001', '1002', 'FINANCING_OUT', 100, '偿还短期借款本金'),
  ('2501', '1002', 'FINANCING_OUT', 100, '偿还长期借款本金'),
  ('4104', '1002', 'FINANCING_OUT', 100, '分配股利/利润'),
  -- 投资活动：设备预付款（修正原错归经营）
  ('1123', '1002', 'INVESTING_OUT', 90, '预付设备款'),
  ('1601', '1002', 'INVESTING_OUT', 100, '购置固定资产'),
  ('1701', '1002', 'INVESTING_OUT', 100, '购置无形资产');
