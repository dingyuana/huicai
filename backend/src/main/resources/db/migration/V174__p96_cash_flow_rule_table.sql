-- ============================================================
-- V174: P96 REQ-095 — 现金流量项目规则初始化
-- 复用 V94 已建的 t_cash_flow_rule 表（code/name/flow_type/match_subject/flow_item/priority/is_active）
-- match_subject 存储对方科目编码；当银行存款(1002)对方科目匹配时分配 flow_type
-- 人工指定优先于规则（铁律 #1：人是唯一审核主体）
-- ============================================================

-- 筹资活动：借款/资本注入（现金流入，对方科目在贷方）
INSERT INTO t_cash_flow_rule (code, name, flow_type, match_subject, flow_item, priority, is_active) VALUES
  ('FIN_IN_2001', '取得短期借款', 'FINANCING_IN', '2001', '取得借款收到的现金', 100, TRUE),
  ('FIN_IN_2501', '取得长期借款', 'FINANCING_IN', '2501', '取得借款收到的现金', 100, TRUE),
  ('FIN_IN_4001', '实收资本注入', 'FINANCING_IN', '4001', '吸收投资收到的现金', 100, TRUE),
  ('FIN_IN_4101', '资本公积增加', 'FINANCING_IN', '4101', '吸收投资收到的现金', 90, TRUE),
-- 筹资活动：还款/股利（现金流出，对方科目在借方）
  ('FIN_OUT_2001', '偿还短期借款', 'FINANCING_OUT', '2001', '偿还债务支付的现金', 100, TRUE),
  ('FIN_OUT_2501', '偿还长期借款', 'FINANCING_OUT', '2501', '偿还债务支付的现金', 100, TRUE),
  ('FIN_OUT_4104', '分配股利利润', 'FINANCING_OUT', '4104', '分配股利利润支付的现金', 100, TRUE),
-- 投资活动：设备采购（修正原错归经营）
  ('INV_OUT_1123', '预付设备款', 'INVESTING_OUT', '1123', '购建固定资产支付的现金', 90, TRUE),
  ('INV_OUT_1601', '购置固定资产', 'INVESTING_OUT', '1601', '购建固定资产支付的现金', 100, TRUE),
  ('INV_OUT_1701', '购置无形资产', 'INVESTING_OUT', '1701', '购建无形资产支付的现金', 100, TRUE)
ON CONFLICT (code) DO NOTHING;
