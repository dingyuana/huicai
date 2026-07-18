-- ============================================================
-- V80: 以票定账 — 科目映射规则表 + AI 映射结果字段
--
-- 改动:
-- 1. 新增 t_account_mapping_rule（商品名→会计科目映射规则表）
-- 2. t_output_invoice 增加 ai_mapping_result JSONB
-- 3. t_input_invoice 增加 ai_mapping_result JSONB
-- 4. 种子数据：常用商品→科目映射规则
-- ============================================================

-- ===== 1. 科目映射规则表 =====
CREATE TABLE IF NOT EXISTS t_account_mapping_rule (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    item_keyword VARCHAR(255) NOT NULL,
    account_code VARCHAR(32) NOT NULL,
    account_name VARCHAR(128),
    direction VARCHAR(10) DEFAULT 'BOTH',
    aux_dimension JSONB DEFAULT '{}',
    priority INT DEFAULT 0,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE t_account_mapping_rule IS '发票品名→会计科目映射规则表（以票定账用）';
COMMENT ON COLUMN t_account_mapping_rule.item_keyword IS '商品/费用名称关键字（支持 LIKE 匹配）';
COMMENT ON COLUMN t_account_mapping_rule.account_code IS '目标会计科目编码';
COMMENT ON COLUMN t_account_mapping_rule.account_name IS '科目名称（冗余，便于查阅）';
COMMENT ON COLUMN t_account_mapping_rule.direction IS '适用方向: INPUT(进项)/OUTPUT(销项)/BOTH';

-- 索引
CREATE INDEX IF NOT EXISTS idx_account_mapping_rule_keyword ON t_account_mapping_rule(item_keyword);
CREATE INDEX IF NOT EXISTS idx_account_mapping_rule_account ON t_account_mapping_rule(account_code);

-- ===== 2. ai_mapping_result 字段 =====
ALTER TABLE t_output_invoice ADD COLUMN IF NOT EXISTS ai_mapping_result JSONB DEFAULT '{}';
COMMENT ON COLUMN t_output_invoice.ai_mapping_result IS 'AI 科目映射推荐结果（含account_code/confidence/reasoning）';

ALTER TABLE t_input_invoice ADD COLUMN IF NOT EXISTS ai_mapping_result JSONB DEFAULT '{}';
COMMENT ON COLUMN t_input_invoice.ai_mapping_result IS 'AI 科目映射推荐结果（含account_code/confidence/reasoning）';

-- ===== 3. 种子数据：常用商品→科目映射 =====
INSERT INTO t_account_mapping_rule (item_keyword, account_code, account_name, direction, priority) VALUES
    -- 销售商品（销项）
    ('电脑', '6001', '主营业务收入', 'OUTPUT', 10),
    ('服务器', '6001', '主营业务收入', 'OUTPUT', 10),
    ('软件', '6001', '主营业务收入', 'OUTPUT', 10),
    ('硬件', '6001', '主营业务收入', 'OUTPUT', 10),
    ('电子产品', '6001', '主营业务收入', 'OUTPUT', 10),
    ('办公用品', '6602', '管理费用', 'BOTH', 5),
    ('办公桌', '6602', '管理费用', 'BOTH', 5),
    ('打印纸', '6602', '管理费用', 'BOTH', 5),
    ('文具', '6602', '管理费用', 'BOTH', 5),
    -- 采购商品（进项）
    ('原材料', '1403', '原材料', 'INPUT', 10),
    ('库存商品', '1405', '库存商品', 'INPUT', 10),
    ('包装物', '1411', '包装物', 'INPUT', 10),
    -- 费用类
    ('差旅费', '6602', '管理费用-差旅费', 'BOTH', 8),
    ('交通费', '6602', '管理费用-交通费', 'BOTH', 8),
    ('住宿费', '6602', '管理费用-住宿费', 'BOTH', 8),
    ('餐饮', '6602', '管理费用-招待费', 'BOTH', 8),
    ('招待费', '6602', '管理费用-招待费', 'BOTH', 8),
    ('培训费', '6602', '管理费用-培训费', 'BOTH', 5),
    ('咨询费', '6602', '管理费用-咨询费', 'BOTH', 5),
    ('广告费', '6601', '销售费用-广告费', 'OUTPUT', 5),
    ('运输费', '6601', '销售费用-运输费', 'BOTH', 5),
    ('物业费', '6602', '管理费用-物业费', 'BOTH', 5),
    ('水电费', '6602', '管理费用-水电费', 'BOTH', 5),
    ('邮电费', '6602', '管理费用-邮电费', 'BOTH', 5),
    -- 固定资产
    ('固定资产', '1601', '固定资产', 'INPUT', 10),
    ('设备', '1601', '固定资产', 'INPUT', 10),
    ('机器', '1601', '固定资产', 'INPUT', 10),
    ('车辆', '1601', '固定资产', 'INPUT', 10)
ON CONFLICT DO NOTHING;

-- 自定义类型约束（仅未来使用，当前不做 CHECK 避免与历史数据冲突）
-- direction: INPUT=进项, OUTPUT=销项, BOTH=通用
-- priority: 数值越大匹配优先级越高