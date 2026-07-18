-- ============================================================
-- V14: 添加 M3+ 模块菜单权限（财务核心/固定资产/往来/税务/预算/报表/分析/AI）
-- 这些模块的前端和后端代码已实现，但菜单数据缺失导致权限检查失败
-- 所有 ID 从 1000 开始，避免与已有菜单冲突（当前最高 ID=804）
-- ============================================================

-- 1. 财务核心（父菜单）
INSERT INTO t_menu (id, name, permission_code, type, parent_id, path, component, icon, sort_order, is_active, is_visible)
VALUES (1000, '财务核心', '', 'menu', NULL, NULL, NULL, 'Money', 3, TRUE, TRUE);

-- 凭证管理
INSERT INTO t_menu (id, name, permission_code, type, parent_id, path, component, icon, sort_order, is_active, is_visible)
VALUES (1010, '凭证管理', 'voucher:list', 'menu', 1000, '/finance/voucher', 'finance/voucher/VoucherList', 'Document', 1, TRUE, TRUE);
INSERT INTO t_menu (id, name, permission_code, type, parent_id, sort_order) VALUES (1011, '查询凭证', 'voucher:query', 'button', 1010, 1);
INSERT INTO t_menu (id, name, permission_code, type, parent_id, sort_order) VALUES (1012, '新增凭证', 'voucher:create', 'button', 1010, 2);
INSERT INTO t_menu (id, name, permission_code, type, parent_id, sort_order) VALUES (1013, '修改凭证', 'voucher:update', 'button', 1010, 3);
INSERT INTO t_menu (id, name, permission_code, type, parent_id, sort_order) VALUES (1014, '删除凭证', 'voucher:delete', 'button', 1010, 4);
INSERT INTO t_menu (id, name, permission_code, type, parent_id, sort_order) VALUES (1015, '提交凭证', 'voucher:submit', 'button', 1010, 5);
INSERT INTO t_menu (id, name, permission_code, type, parent_id, sort_order) VALUES (1016, '审核凭证', 'voucher:audit', 'button', 1010, 6);
INSERT INTO t_menu (id, name, permission_code, type, parent_id, sort_order) VALUES (1017, '记账凭证', 'voucher:post', 'button', 1010, 7);
INSERT INTO t_menu (id, name, permission_code, type, parent_id, sort_order) VALUES (1018, '红冲凭证', 'voucher:reverse', 'button', 1010, 8);

-- 账簿查询
INSERT INTO t_menu (id, name, permission_code, type, parent_id, path, component, icon, sort_order, is_active, is_visible)
VALUES (1020, '账簿查询', 'ledger:list', 'menu', 1000, '/finance/ledger', 'finance/ledger/LedgerView', 'Notebook', 2, TRUE, TRUE);
INSERT INTO t_menu (id, name, permission_code, type, parent_id, sort_order) VALUES (1021, '查询账簿', 'ledger:query', 'button', 1020, 1);

-- 期末结账
INSERT INTO t_menu (id, name, permission_code, type, parent_id, path, component, icon, sort_order, is_active, is_visible)
VALUES (1030, '期末结账', 'period:close', 'menu', 1000, '/finance/period-close', 'finance/period-close/PeriodCloseView', 'Lock', 3, TRUE, TRUE);
INSERT INTO t_menu (id, name, permission_code, type, parent_id, sort_order) VALUES (1031, '执行结账', 'period:close:exec', 'button', 1030, 1);
INSERT INTO t_menu (id, name, permission_code, type, parent_id, sort_order) VALUES (1032, '反结账', 'period:reopen', 'button', 1030, 2);

-- 业务单据
INSERT INTO t_menu (id, name, permission_code, type, parent_id, path, component, icon, sort_order, is_active, is_visible)
VALUES (1040, '业务单据', 'doc:list', 'menu', 1000, '/finance/business-doc', 'finance/business-doc/BusinessDocList', 'Tickets', 4, TRUE, TRUE);
INSERT INTO t_menu (id, name, permission_code, type, parent_id, sort_order) VALUES (1041, '查询单据', 'doc:query', 'button', 1040, 1);
INSERT INTO t_menu (id, name, permission_code, type, parent_id, sort_order) VALUES (1042, '新增单据', 'doc:create', 'button', 1040, 2);
INSERT INTO t_menu (id, name, permission_code, type, parent_id, sort_order) VALUES (1043, '修改单据', 'doc:update', 'button', 1040, 3);
INSERT INTO t_menu (id, name, permission_code, type, parent_id, sort_order) VALUES (1044, '删除单据', 'doc:delete', 'button', 1040, 4);
INSERT INTO t_menu (id, name, permission_code, type, parent_id, sort_order) VALUES (1045, '审核单据', 'doc:audit', 'button', 1040, 5);

-- 银行账户
INSERT INTO t_menu (id, name, permission_code, type, parent_id, path, component, icon, sort_order, is_active, is_visible)
VALUES (1050, '银行账户', 'bank:account:list', 'menu', 1000, '/finance/bank-account', 'finance/bank-account/BankAccountList', 'CreditCard', 5, TRUE, TRUE);
INSERT INTO t_menu (id, name, permission_code, type, parent_id, sort_order) VALUES (1051, '查询账户', 'bank:account:query', 'button', 1050, 1);
INSERT INTO t_menu (id, name, permission_code, type, parent_id, sort_order) VALUES (1052, '新增账户', 'bank:account:create', 'button', 1050, 2);
INSERT INTO t_menu (id, name, permission_code, type, parent_id, sort_order) VALUES (1053, '修改账户', 'bank:account:update', 'button', 1050, 3);
INSERT INTO t_menu (id, name, permission_code, type, parent_id, sort_order) VALUES (1054, '删除账户', 'bank:account:delete', 'button', 1050, 4);

-- 银行日记账
INSERT INTO t_menu (id, name, permission_code, type, parent_id, path, component, icon, sort_order, is_active, is_visible)
VALUES (1060, '银行日记账', 'bank:journal:list', 'menu', 1000, '/finance/bank-journal', 'finance/bank-journal/BankJournalList', 'List', 6, TRUE, TRUE);
INSERT INTO t_menu (id, name, permission_code, type, parent_id, sort_order) VALUES (1061, '查询日记账', 'bank:journal:query', 'button', 1060, 1);

-- 银行对账单
INSERT INTO t_menu (id, name, permission_code, type, parent_id, path, component, icon, sort_order, is_active, is_visible)
VALUES (1070, '银行对账单', 'bank:statement:list', 'menu', 1000, '/finance/bank-statement', 'finance/bank-statement/BankStatementView', 'DocumentCopy', 7, TRUE, TRUE);
INSERT INTO t_menu (id, name, permission_code, type, parent_id, sort_order) VALUES (1071, '查询对账单', 'bank:statement:query', 'button', 1070, 1);

-- 银行对账
INSERT INTO t_menu (id, name, permission_code, type, parent_id, path, component, icon, sort_order, is_active, is_visible)
VALUES (1080, '银行对账', 'bank:reconciliation:list', 'menu', 1000, '/finance/bank-reconciliation', 'finance/bank-reconciliation/ReconciliationView', 'ScaleToOriginal', 8, TRUE, TRUE);
INSERT INTO t_menu (id, name, permission_code, type, parent_id, sort_order) VALUES (1081, '执行对账', 'bank:reconciliation:exec', 'button', 1080, 1);

-- 2. 固定资产
INSERT INTO t_menu (id, name, permission_code, type, parent_id, path, component, icon, sort_order, is_active, is_visible)
VALUES (2000, '固定资产', '', 'menu', NULL, NULL, NULL, 'Box', 4, TRUE, TRUE);

INSERT INTO t_menu (id, name, permission_code, type, parent_id, path, component, icon, sort_order, is_active, is_visible)
VALUES (2010, '资产类别', 'asset:category:list', 'menu', 2000, '/asset/category', 'asset/category/AssetCategoryList', 'Collection', 1, TRUE, TRUE);
INSERT INTO t_menu (id, name, permission_code, type, parent_id, sort_order) VALUES (2011, '查询类别', 'asset:category:query', 'button', 2010, 1);
INSERT INTO t_menu (id, name, permission_code, type, parent_id, sort_order) VALUES (2012, '新增类别', 'asset:category:create', 'button', 2010, 2);
INSERT INTO t_menu (id, name, permission_code, type, parent_id, sort_order) VALUES (2013, '修改类别', 'asset:category:update', 'button', 2010, 3);
INSERT INTO t_menu (id, name, permission_code, type, parent_id, sort_order) VALUES (2014, '删除类别', 'asset:category:delete', 'button', 2010, 4);

INSERT INTO t_menu (id, name, permission_code, type, parent_id, path, component, icon, sort_order, is_active, is_visible)
VALUES (2020, '资产卡片', 'asset:card:list', 'menu', 2000, '/asset/card', 'asset/card/AssetCardList', 'Files', 2, TRUE, TRUE);
INSERT INTO t_menu (id, name, permission_code, type, parent_id, sort_order) VALUES (2021, '查询卡片', 'asset:card:query', 'button', 2020, 1);
INSERT INTO t_menu (id, name, permission_code, type, parent_id, sort_order) VALUES (2022, '新增卡片', 'asset:card:create', 'button', 2020, 2);
INSERT INTO t_menu (id, name, permission_code, type, parent_id, sort_order) VALUES (2023, '修改卡片', 'asset:card:update', 'button', 2020, 3);
INSERT INTO t_menu (id, name, permission_code, type, parent_id, sort_order) VALUES (2024, '删除卡片', 'asset:card:delete', 'button', 2020, 4);
INSERT INTO t_menu (id, name, permission_code, type, parent_id, sort_order) VALUES (2025, '计提折旧', 'asset:card:depreciate', 'button', 2020, 5);

INSERT INTO t_menu (id, name, permission_code, type, parent_id, path, component, icon, sort_order, is_active, is_visible)
VALUES (2030, '资产处置', 'asset:disposal:list', 'menu', 2000, '/asset/disposal', 'asset/disposal/AssetDisposalList', 'Remove', 3, TRUE, TRUE);
INSERT INTO t_menu (id, name, permission_code, type, parent_id, sort_order) VALUES (2031, '查询处置', 'asset:disposal:query', 'button', 2030, 1);
INSERT INTO t_menu (id, name, permission_code, type, parent_id, sort_order) VALUES (2032, '新增处置', 'asset:disposal:create', 'button', 2030, 2);

-- 3. 往来管理
INSERT INTO t_menu (id, name, permission_code, type, parent_id, path, component, icon, sort_order, is_active, is_visible)
VALUES (3000, '往来管理', '', 'menu', NULL, NULL, NULL, 'Connection', 5, TRUE, TRUE);

INSERT INTO t_menu (id, name, permission_code, type, parent_id, path, component, icon, sort_order, is_active, is_visible)
VALUES (3010, '客户档案', 'customer:list', 'menu', 3000, '/arap/customer', 'arap/customer/CustomerList', 'User', 1, TRUE, TRUE);
INSERT INTO t_menu (id, name, permission_code, type, parent_id, sort_order) VALUES (3011, '查询客户', 'customer:query', 'button', 3010, 1);
INSERT INTO t_menu (id, name, permission_code, type, parent_id, sort_order) VALUES (3012, '新增客户', 'customer:create', 'button', 3010, 2);
INSERT INTO t_menu (id, name, permission_code, type, parent_id, sort_order) VALUES (3013, '修改客户', 'customer:update', 'button', 3010, 3);
INSERT INTO t_menu (id, name, permission_code, type, parent_id, sort_order) VALUES (3014, '删除客户', 'customer:delete', 'button', 3010, 4);

INSERT INTO t_menu (id, name, permission_code, type, parent_id, path, component, icon, sort_order, is_active, is_visible)
VALUES (3020, '供应商档案', 'vendor:list', 'menu', 3000, '/arap/vendor', 'arap/vendor/VendorList', 'UserFilled', 2, TRUE, TRUE);
INSERT INTO t_menu (id, name, permission_code, type, parent_id, sort_order) VALUES (3021, '查询供应商', 'vendor:query', 'button', 3020, 1);
INSERT INTO t_menu (id, name, permission_code, type, parent_id, sort_order) VALUES (3022, '新增供应商', 'vendor:create', 'button', 3020, 2);
INSERT INTO t_menu (id, name, permission_code, type, parent_id, sort_order) VALUES (3023, '修改供应商', 'vendor:update', 'button', 3020, 3);
INSERT INTO t_menu (id, name, permission_code, type, parent_id, sort_order) VALUES (3024, '删除供应商', 'vendor:delete', 'button', 3020, 4);

INSERT INTO t_menu (id, name, permission_code, type, parent_id, path, component, icon, sort_order, is_active, is_visible)
VALUES (3030, '应收明细', 'receivable:list', 'menu', 3000, '/arap/receivable', 'arap/receivable/ReceivableList', 'Money', 3, TRUE, TRUE);
INSERT INTO t_menu (id, name, permission_code, type, parent_id, sort_order) VALUES (3031, '查询应收', 'receivable:query', 'button', 3030, 1);

INSERT INTO t_menu (id, name, permission_code, type, parent_id, path, component, icon, sort_order, is_active, is_visible)
VALUES (3040, '应付明细', 'payable:list', 'menu', 3000, '/arap/payable', 'arap/payable/PayableList', 'Money', 4, TRUE, TRUE);
INSERT INTO t_menu (id, name, permission_code, type, parent_id, sort_order) VALUES (3041, '查询应付', 'payable:query', 'button', 3040, 1);

INSERT INTO t_menu (id, name, permission_code, type, parent_id, path, component, icon, sort_order, is_active, is_visible)
VALUES (3050, '坏账准备', 'bad:debt:list', 'menu', 3000, '/arap/bad-debt', 'arap/bad-debt/BadDebtList', 'Warning', 5, TRUE, TRUE);
INSERT INTO t_menu (id, name, permission_code, type, parent_id, sort_order) VALUES (3051, '查询坏账', 'bad:debt:query', 'button', 3050, 1);
INSERT INTO t_menu (id, name, permission_code, type, parent_id, sort_order) VALUES (3052, '计提坏账', 'bad:debt:provision', 'button', 3050, 2);

-- 4. 税务管理
INSERT INTO t_menu (id, name, permission_code, type, parent_id, path, component, icon, sort_order, is_active, is_visible)
VALUES (4000, '税务管理', '', 'menu', NULL, NULL, NULL, 'Document', 6, TRUE, TRUE);

INSERT INTO t_menu (id, name, permission_code, type, parent_id, path, component, icon, sort_order, is_active, is_visible)
VALUES (4010, '进项发票', 'tax:input:list', 'menu', 4000, '/tax/input-invoice', 'tax/input-invoice/InputInvoiceList', 'Upload', 1, TRUE, TRUE);
INSERT INTO t_menu (id, name, permission_code, type, parent_id, sort_order) VALUES (4011, '查询进项', 'tax:input:query', 'button', 4010, 1);
INSERT INTO t_menu (id, name, permission_code, type, parent_id, sort_order) VALUES (4012, '新增进项', 'tax:input:create', 'button', 4010, 2);
INSERT INTO t_menu (id, name, permission_code, type, parent_id, sort_order) VALUES (4013, '认证发票', 'tax:input:certify', 'button', 4010, 3);

INSERT INTO t_menu (id, name, permission_code, type, parent_id, path, component, icon, sort_order, is_active, is_visible)
VALUES (4020, '销项发票', 'tax:output:list', 'menu', 4000, '/tax/output-invoice', 'tax/output-invoice/OutputInvoiceList', 'Download', 2, TRUE, TRUE);
INSERT INTO t_menu (id, name, permission_code, type, parent_id, sort_order) VALUES (4021, '查询销项', 'tax:output:query', 'button', 4020, 1);
INSERT INTO t_menu (id, name, permission_code, type, parent_id, sort_order) VALUES (4022, '新增销项', 'tax:output:create', 'button', 4020, 2);

INSERT INTO t_menu (id, name, permission_code, type, parent_id, path, component, icon, sort_order, is_active, is_visible)
VALUES (4030, '增值税计算', 'tax:vat:view', 'menu', 4000, '/tax/vat', 'tax/declaration/TaxVatView', 'DataBoard', 3, TRUE, TRUE);
INSERT INTO t_menu (id, name, permission_code, type, parent_id, sort_order) VALUES (4031, '查询增值税', 'tax:vat:query', 'button', 4030, 1);

-- 5. 预算管理
INSERT INTO t_menu (id, name, permission_code, type, parent_id, path, component, icon, sort_order, is_active, is_visible)
VALUES (5000, '预算管理', 'budget:list', 'menu', NULL, '/budget', 'budget/BudgetList', 'PieChart', 7, TRUE, TRUE);
INSERT INTO t_menu (id, name, permission_code, type, parent_id, sort_order) VALUES (5001, '查询预算', 'budget:query', 'button', 5000, 1);
INSERT INTO t_menu (id, name, permission_code, type, parent_id, sort_order) VALUES (5002, '新增预算', 'budget:create', 'button', 5000, 2);
INSERT INTO t_menu (id, name, permission_code, type, parent_id, sort_order) VALUES (5003, '修改预算', 'budget:update', 'button', 5000, 3);
INSERT INTO t_menu (id, name, permission_code, type, parent_id, sort_order) VALUES (5004, '删除预算', 'budget:delete', 'button', 5000, 4);

-- 6. 报表中心
INSERT INTO t_menu (id, name, permission_code, type, parent_id, path, component, icon, sort_order, is_active, is_visible)
VALUES (6000, '报表中心', '', 'menu', NULL, NULL, NULL, 'DataLine', 8, TRUE, TRUE);

INSERT INTO t_menu (id, name, permission_code, type, parent_id, path, component, icon, sort_order, is_active, is_visible)
VALUES (6010, '科目余额表', 'report:subject:list', 'menu', 6000, '/report/subject-balance', 'report/subject-balance/SubjectBalanceView', 'List', 1, TRUE, TRUE);
INSERT INTO t_menu (id, name, permission_code, type, parent_id, sort_order) VALUES (6011, '查询余额表', 'report:subject:query', 'button', 6010, 1);

INSERT INTO t_menu (id, name, permission_code, type, parent_id, path, component, icon, sort_order, is_active, is_visible)
VALUES (6020, '资产负债表', 'report:balance:view', 'menu', 6000, '/report/balance-sheet', 'report/balance-sheet/BalanceSheetView', 'DataAnalysis', 2, TRUE, TRUE);
INSERT INTO t_menu (id, name, permission_code, type, parent_id, sort_order) VALUES (6021, '查看资产负债表', 'report:balance:query', 'button', 6020, 1);

INSERT INTO t_menu (id, name, permission_code, type, parent_id, path, component, icon, sort_order, is_active, is_visible)
VALUES (6030, '利润表', 'report:income:view', 'menu', 6000, '/report/income-statement', 'report/income-statement/IncomeStatementView', 'TrendCharts', 3, TRUE, TRUE);
INSERT INTO t_menu (id, name, permission_code, type, parent_id, sort_order) VALUES (6031, '查看利润表', 'report:income:query', 'button', 6030, 1);

INSERT INTO t_menu (id, name, permission_code, type, parent_id, path, component, icon, sort_order, is_active, is_visible)
VALUES (6040, '现金流量表', 'report:cashflow:view', 'menu', 6000, '/report/cash-flow', 'report/cash-flow/CashFlowView', 'Money', 4, TRUE, TRUE);
INSERT INTO t_menu (id, name, permission_code, type, parent_id, sort_order) VALUES (6041, '查看现金流量表', 'report:cashflow:query', 'button', 6040, 1);

-- 7. 财务分析
INSERT INTO t_menu (id, name, permission_code, type, parent_id, path, component, icon, sort_order, is_active, is_visible)
VALUES (7000, '财务分析', '', 'menu', NULL, NULL, NULL, 'TrendCharts', 9, TRUE, TRUE);

INSERT INTO t_menu (id, name, permission_code, type, parent_id, path, component, icon, sort_order, is_active, is_visible)
VALUES (7010, '关键指标', 'analysis:key:view', 'menu', 7000, '/analysis/key-metrics', 'analysis/key-metrics/KeyMetricsView', 'DataBoard', 1, TRUE, TRUE);
INSERT INTO t_menu (id, name, permission_code, type, parent_id, sort_order) VALUES (7011, '查看关键指标', 'analysis:key:query', 'button', 7010, 1);

INSERT INTO t_menu (id, name, permission_code, type, parent_id, path, component, icon, sort_order, is_active, is_visible)
VALUES (7020, '杜邦分析', 'analysis:dupont:view', 'menu', 7000, '/analysis/dupont', 'analysis/dupont/DupontView', 'Odometer', 2, TRUE, TRUE);
INSERT INTO t_menu (id, name, permission_code, type, parent_id, sort_order) VALUES (7021, '查看杜邦分析', 'analysis:dupont:query', 'button', 7020, 1);

-- 8. AI 中心
INSERT INTO t_menu (id, name, permission_code, type, parent_id, path, component, icon, sort_order, is_active, is_visible)
VALUES (8000, 'AI 中心', '', 'menu', NULL, NULL, NULL, 'MagicStick', 10, TRUE, TRUE);

INSERT INTO t_menu (id, name, permission_code, type, parent_id, path, component, icon, sort_order, is_active, is_visible)
VALUES (8010, 'AI 任务', 'ai:task:list', 'menu', 8000, '/ai/task', 'ai/task/AiTaskList', 'SetUp', 1, TRUE, TRUE);
INSERT INTO t_menu (id, name, permission_code, type, parent_id, sort_order) VALUES (8011, '查询任务', 'ai:task:query', 'button', 8010, 1);

INSERT INTO t_menu (id, name, permission_code, type, parent_id, path, component, icon, sort_order, is_active, is_visible)
VALUES (8020, 'AI 异常', 'ai:anomaly:list', 'menu', 8000, '/ai/anomaly', 'ai/anomaly/AnomalyList', 'WarningFilled', 2, TRUE, TRUE);
INSERT INTO t_menu (id, name, permission_code, type, parent_id, sort_order) VALUES (8021, '查询异常', 'ai:anomaly:query', 'button', 8020, 1);

-- ============================================================
-- 将新菜单全部授权给 admin 角色
-- t_role_menu.id 无自增默认值，需在 DO 块内生成唯一 ID
-- ============================================================
DO $$
DECLARE
    v_next_id BIGINT;
    v_role_id BIGINT;
    v_menu_id BIGINT;
BEGIN
    SELECT COALESCE(MAX(id), 0) + 1 INTO v_next_id FROM t_role_menu;
    SELECT id INTO v_role_id FROM t_role WHERE code = 'admin';
    FOR v_menu_id IN SELECT m.id FROM t_menu m WHERE m.id >= 1000 LOOP
        INSERT INTO t_role_menu (id, role_id, menu_id)
        VALUES (v_next_id, v_role_id, v_menu_id)
        ON CONFLICT (role_id, menu_id) DO NOTHING;
        IF FOUND THEN
            v_next_id := v_next_id + 1;
        END IF;
    END LOOP;
END $$;
