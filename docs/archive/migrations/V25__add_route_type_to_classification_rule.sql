-- ============================================================
-- V25: P0.3 分类规则添加 A/B/C 路由类型
-- 当规则匹配时, 其 route_type 决定自动生单方向:
--   A - 直接生成凭证
--   B - 先生成业务单据再生成凭证
--   C - 不处理, 留待人工认领
-- ============================================================

ALTER TABLE t_classification_rule
    ADD COLUMN route_type VARCHAR(4)
        CONSTRAINT chk_cls_rule_route_type CHECK (route_type IN ('A', 'B', 'C'))
        DEFAULT NULL;

COMMENT ON COLUMN t_classification_rule.route_type IS 'A/B/C路由类型: A-直接制证, B-生单后制证, C-待人工; NULL则使用分类硬编码映射';

-- 为已有种子规则设置 route_type (与 classifyType 硬编码保持一致)
UPDATE t_classification_rule SET route_type = 'A' WHERE classification IN ('bank_fee', 'interest_income', 'tax_payment', 'social_security', 'insurance_fee');
UPDATE t_classification_rule SET route_type = 'B' WHERE classification IN ('business_receipt', 'business_payment', 'internal_transfer', 'salary_payment');
