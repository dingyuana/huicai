-- ============================================================
-- V12: 初始数据种子 - 完整演示数据
-- 资产类别、税种、客户/供应商、现金流量规则、指标定义
-- 注: 用户/角色/部门/科目/凭证类型 已在 V1-V3 预置
-- ============================================================

-- 1. 资产类别
INSERT INTO t_asset_category (id, code, name, level, depreciation_method, useful_life, residual_rate, remark) VALUES
(1, 'EQ',  '电子设备', 1, 'STRAIGHT_LINE',     5,  0.05, '电脑/打印机/服务器等'),
(2, 'OF',  '办公家具', 1, 'STRAIGHT_LINE',     10, 0.05, '桌椅/柜子等'),
(3, 'VH',  '运输工具', 1, 'DOUBLE_DECLINING',  8,  0.05, '汽车/货车等'),
(4, 'MA',  '机械设备', 1, 'STRAIGHT_LINE',     10, 0.10, '生产设备'),
(5, 'BD',  '房屋建筑', 1, 'STRAIGHT_LINE',     20, 0.05, '厂房/办公楼')
ON CONFLICT (code) DO NOTHING;

-- 2. 税种定义
INSERT INTO t_tax_type (id, code, name, tax_category, rate, is_active, remark) VALUES
(1, 'VAT_IN_13',  '增值税进项13%', 'VAT_INPUT',  0.13, TRUE, '一般纳税人13%进项'),
(2, 'VAT_IN_9',   '增值税进项9%',  'VAT_INPUT',  0.09, TRUE, '9%税率(运输/建筑)'),
(3, 'VAT_IN_6',   '增值税进项6%',  'VAT_INPUT',  0.06, TRUE, '6%现代服务业'),
(4, 'VAT_OUT_13', '增值税销项13%', 'VAT_OUTPUT', 0.13, TRUE, '一般销售'),
(5, 'VAT_OUT_9',  '增值税销项9%',  'VAT_OUTPUT', 0.09, TRUE, '9%销售'),
(6, 'VAT_OUT_6',  '增值税销项6%',  'VAT_OUTPUT', 0.06, TRUE, '6%服务'),
(7, 'CITY_TAX',   '城建税',        'SURCHARGE',  0.07, TRUE, '7%'),
(8, 'EDU_TAX',    '教育费附加',    'SURCHARGE',  0.03, TRUE, '3%'),
(9, 'LOCAL_EDU',  '地方教育附加',  'SURCHARGE',  0.02, TRUE, '2%')
ON CONFLICT (code) DO NOTHING;

-- 3. 客户档案
INSERT INTO t_customer (id, code, name, contact_person, phone, address, tax_no, credit_limit, credit_days, is_active) VALUES
(1, 'C001', '上海明远科技有限公司', '张伟', '13800138001', '上海市浦东新区张江路100号', '91310115MA1K12345X', 500000, 60, TRUE),
(2, 'C002', '北京华联贸易有限公司', '李娜', '13800138002', '北京市朝阳区建国路88号',     '91110105MA01N5432Y', 300000, 45, TRUE),
(3, 'C003', '深圳科创电子有限公司', '王强', '13800138003', '深圳市南山区科技园路1号',     '91440300MA5G9876Z',  1000000, 90, TRUE),
(4, 'C004', '广州盛达物流有限公司', '刘洋', '13800138004', '广州市天河区珠江新城路50号', '91440101MA59C1234W', 200000, 30, TRUE)
ON CONFLICT (code) DO NOTHING;

-- 4. 供应商档案
INSERT INTO t_vendor (id, code, name, contact_person, phone, address, tax_no, credit_limit, credit_days, is_active) VALUES
(1, 'V001', '上海原料供应有限公司',  '陈刚', '13900139001', '上海市嘉定区工业区路88号',   '91310114MA1G5678X', 300000, 60, TRUE),
(2, 'V002', '北京能源股份有限公司',  '赵敏', '13900139002', '北京市西城区金融街1号',     '91110102MA02P9876Q', 500000, 30, TRUE),
(3, 'V003', '深圳电子配件供应商',    '孙浩', '13900139003', '深圳市宝安区松岗街道10号',   '91440300MA5R1122S', 100000, 30, TRUE),
(4, 'V004', '广州物流服务公司',      '周丽', '13900139004', '广州市白云区机场路100号',    '91440111MA5T3344U', 80000,  30, TRUE)
ON CONFLICT (code) DO NOTHING;

-- 5. 现金流量规则(已在 V11 预置 8 条, 此处补充)
INSERT INTO t_cash_flow_rule (id, code, name, flow_type, match_subject, flow_item, priority) VALUES
(9,  'CF009', '收到税费返还',    'OPERATING_IN',  '2221*',     'TAX_REFUND',    1),
(10, 'CF010', '支付其他经营',    'OPERATING_OUT', '6601*',     'OTHER_OP',      1),
(11, 'CF011', '处置资产收到',    'INVESTING_IN',  '1606*',     'DISPOSAL_IN',   1),
(12, 'CF012', '收回投资',        'INVESTING_IN',  '1511*',     'INVEST_BACK',   1)
ON CONFLICT (code) DO NOTHING;

-- 6. 客户分类汇总(预警阈值)
INSERT INTO t_sys_config (id, config_key, config_value, config_type, description) VALUES
(10, 'bad_debt.aging_ratios', '{"current":0,"days_0_30":0.05,"days_31_60":0.20,"days_61_90":0.50,"days_91_180":0.80,"days_181_365":1.00}', 'business', '账龄比例法默认比例'),
(11, 'ar.credit_check_enabled', 'true', 'business', '保存单据时检查客户信用'),
(12, 'budget.control_enabled',  'true', 'business', '保存单据时检查预算'),
(13, 'ai.auto_match_enabled',   'true', 'business', '银行对账启用 AI 自动匹配'),
(14, 'ai.ocr_enabled',          'false', 'business', 'OCR 智能填单(需先安装 Tesseract)')
ON CONFLICT (config_key) DO NOTHING;

-- 7. 常用摘要
INSERT INTO t_summary_lib (id, summary_code, summary_text, category, sort_order) VALUES
(1, 'S01', '销售商品收入',         '收入', 1),
(2, 'S02', '提供劳务收入',         '收入', 2),
(3, 'S03', '收到货款',             '收款', 3),
(4, 'S04', '支付货款',             '付款', 4),
(5, 'S05', '支付职工薪酬',         '费用', 5),
(6, 'S06', '办公费',               '费用', 6),
(7, 'S07', '差旅费',               '费用', 7),
(8, 'S08', '水电费',               '费用', 8),
(9, 'S09', '收到银行利息',         '收入', 9),
(10, 'S10', '支付银行手续费',      '费用', 10),
(11, 'S11', '采购原材料',          '采购', 11),
(12, 'S12', '计提固定资产折旧',    '折旧', 12),
(13, 'S13', '结转销售成本',        '结转', 13),
(14, 'S14', '结转损益',            '结转', 14),
(15, 'S15', '计提坏账准备',        '计提', 15)
ON CONFLICT (summary_code) DO NOTHING;
