-- V102.5: 迁移自 V95 的 t_voucher_template_line 分录行数据
-- V95 的 t_voucher_template 表插入成功，但 t_voucher_template_line 因外键
-- 约束 (fk_vtl_subject) 需要在 t_subject 有数据后才能插入。
-- 已存在数据库升级时，V95 已执行过 line 插入，此处用 ON CONFLICT DO NOTHING 跳过。
-- 同时预置基础科目，确保 Testcontainers 等空数据库场景下模板分录行可正常插入。

-- 预置基础科目（供凭证模板分录行引用）
INSERT INTO t_subject (id, enterprise_id, code, name, parent_id, level, direction, is_leaf, aux_calc_type, is_active, remark, created_by, created_at, updated_by, updated_at, deleted)
OVERRIDING SYSTEM VALUE VALUES
(1, 1, '1001', '库存现金', NULL, 1, 'debit', true, NULL, true, NULL, 1, NOW(), 1, NOW(), 0),
(2, 1, '1002', '银行存款', NULL, 1, 'debit', true, NULL, true, NULL, 1, NOW(), 1, NOW(), 0),
(3, 1, '1012', '其他货币资金', NULL, 1, 'debit', true, NULL, true, NULL, 1, NOW(), 1, NOW(), 0),
(4, 1, '1101', '交易性金融资产', NULL, 1, 'debit', true, NULL, true, NULL, 1, NOW(), 1, NOW(), 0),
(5, 1, '1121', '应收票据', NULL, 1, 'debit', true, NULL, true, NULL, 1, NOW(), 1, NOW(), 0),
(6, 1, '1122', '应收账款', NULL, 1, 'debit', true, NULL, true, NULL, 1, NOW(), 1, NOW(), 0),
(7, 1, '1123', '预付账款', NULL, 1, 'debit', true, NULL, true, NULL, 1, NOW(), 1, NOW(), 0),
(28, 1, '1601', '固定资产', NULL, 1, 'debit', true, NULL, true, NULL, 1, NOW(), 1, NOW(), 0),
(44, 1, '2202', '应付账款', NULL, 1, 'credit', true, NULL, true, NULL, 1, NOW(), 1, NOW(), 0),
(47, 1, '2221', '应交税费', NULL, 1, 'credit', true, NULL, true, NULL, 1, NOW(), 1, NOW(), 0),
(64, 1, '4103', '本年利润', NULL, 1, 'credit', true, NULL, true, NULL, 1, NOW(), 1, NOW(), 0),
(67, 1, '5001', '生产成本', NULL, 1, 'debit', true, NULL, true, NULL, 1, NOW(), 1, NOW(), 0),
(74, 1, '6001', '主营业务收入', NULL, 1, 'credit', true, NULL, true, NULL, 1, NOW(), 1, NOW(), 0),
(81, 1, '6401', '主营业务成本', NULL, 1, 'debit', true, NULL, true, NULL, 1, NOW(), 1, NOW(), 0),
(82, 1, '6402', '其他业务成本', NULL, 1, 'debit', true, NULL, true, NULL, 1, NOW(), 1, NOW(), 0),
(85, 1, '6602', '管理费用', NULL, 1, 'debit', true, NULL, true, NULL, 1, NOW(), 1, NOW(), 0)
ON CONFLICT (id) DO NOTHING;

-- 模板分录行（从 V95 迁移至此）
-- 科目ID映射：1001→1, 1002→2, 1122→6, 1123→7, 1221→10, 2202→44, 2203→45, 2211→46, 2221→47, 2241→50, 4101→63, 4103→64, 5001→67, 6001→74, 6051→75, 6401→81, 6402→82, 6601→84, 6602→85, 6603→86, 1601→28

-- 销售收款模板分录 (template_id=1)
INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, assist_type, assist_required, line_order, deleted) VALUES
(1, 2, '{{amount}}', NULL, '销售收款-货款', 'debit', NULL, FALSE, 1, 0),
(1, 74, NULL, '{{amount}}', '销售收款-货款', 'credit', NULL, FALSE, 1, 0),
(1, 2, '{{taxAmount}}', NULL, '销售收款-销项税', 'debit', NULL, FALSE, 2, 0),
(1, 47, NULL, '{{taxAmount}}', '销售收款-销项税', 'credit', NULL, FALSE, 2, 0)
ON CONFLICT DO NOTHING;

-- 采购付款模板分录 (template_id=2)
INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, assist_type, assist_required, line_order, deleted) VALUES
(2, 67, '{{amount}}', NULL, '采购成本', 'debit', NULL, FALSE, 1, 0),
(2, 44, NULL, '{{amount}}', '采购成本', 'credit', NULL, FALSE, 1, 0),
(2, 82, '{{taxAmount}}', NULL, '进项税额', 'debit', NULL, FALSE, 2, 0),
(2, 2, NULL, '{{taxAmount}}', '进项税额', 'credit', NULL, FALSE, 2, 0)
ON CONFLICT DO NOTHING;

-- 费用报销模板分录 (template_id=3)
INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, assist_type, assist_required, line_order, deleted) VALUES
(3, 85, '{{amount}}', NULL, '费用报销', 'debit', NULL, FALSE, 1, 0),
(3, 2, NULL, '{{amount}}', '费用报销', 'credit', NULL, FALSE, 1, 0)
ON CONFLICT DO NOTHING;

-- 现金存行模板分录 (template_id=4)
INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, assist_type, assist_required, line_order, deleted) VALUES
(4, 2, '{{amount}}', NULL, '现金存入银行', 'debit', NULL, FALSE, 1, 0),
(4, 1, NULL, '{{amount}}', '现金存入银行', 'credit', NULL, FALSE, 1, 0)
ON CONFLICT DO NOTHING;

-- 固定资产购入模板分录 (template_id=5)
INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, assist_type, assist_required, line_order, deleted) VALUES
(5, 28, '{{amount}}', NULL, '购入固定资产', 'debit', NULL, FALSE, 1, 0),
(5, 44, NULL, '{{amount}}', '购入固定资产', 'credit', NULL, FALSE, 1, 0),
(5, 47, '{{taxAmount}}', NULL, '固定资产进项税', 'debit', NULL, FALSE, 2, 0),
(5, 2, NULL, '{{taxAmount}}', '固定资产进项税', 'credit', NULL, FALSE, 2, 0)
ON CONFLICT DO NOTHING;

-- 缴纳税费模板分录 (template_id=6)
INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, assist_type, assist_required, line_order, deleted) VALUES
(6, 47, '{{amount}}', NULL, '缴纳税费', 'debit', NULL, FALSE, 1, 0),
(6, 2, NULL, '{{amount}}', '缴纳税费', 'credit', NULL, FALSE, 1, 0)
ON CONFLICT DO NOTHING;

-- 期末结转模板分录 (template_id=7)
INSERT INTO t_voucher_template_line (template_id, subject_id, dr_amount_template, cr_amount_template, summary_template, direction, assist_type, assist_required, line_order, deleted) VALUES
(7, 74, '{{amount}}', NULL, '结转收入', 'debit', NULL, FALSE, 1, 0),
(7, 64, NULL, '{{amount}}', '结转收入', 'credit', NULL, FALSE, 1, 0),
(7, 64, '{{amount}}', NULL, '结转成本', 'debit', NULL, FALSE, 2, 0),
(7, 81, NULL, '{{amount}}', '结转成本', 'credit', NULL, FALSE, 2, 0)
ON CONFLICT DO NOTHING;