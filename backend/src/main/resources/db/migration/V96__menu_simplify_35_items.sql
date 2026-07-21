-- ============================================================
-- V96: 菜单精简重构 — 67 项 → 35 项（SME MVP 设计）
-- 对应设计文档：docs/design/FRONTEND_SIMPLIFICATION_PLAN.md
-- 变更内容：
--   1. 移除 Agency/系统管理/预算管理/财务分析/AI中心 菜单（保留为实验室+设置）
--   2. 合并客商档案（客户+供应商+员工+部门 → /basis/party）
--   3. 核销工作台/费用报销/预收预付/坏账准备 → 业务单据 Tab
--   4. 一级导航从 10 个减为 7 个
-- ============================================================

-- 清理旧菜单数据（逻辑删除，可回滚）
UPDATE t_menu SET deleted = 1 WHERE id IS NOT NULL;

-- 说明：t_menu.id 为 GENERATED ALWAYS AS IDENTITY，
--       必须使用 OVERRIDING SYSTEM VALUE 才能显式指定 id。

-- ===================== 一级目录 =====================
INSERT INTO t_menu (id, menu_name, menu_code, path, component, icon, parent_id, menu_type, permission, sort_order, is_active, created_at, deleted)
OVERRIDING SYSTEM VALUE VALUES
(1,  '首页',    'menu-dashboard',    '/dashboard',    NULL, 'HomeFilled',     NULL, 'DIR',   '',      1,  TRUE, CURRENT_TIMESTAMP, 0),
(2,  '基础数据', 'menu-basis',        '/basis',        NULL, 'Notebook',     NULL, 'DIR',   '',      2,  TRUE, CURRENT_TIMESTAMP, 0),
(3,  '财务核心', 'menu-finance',      '/finance',      NULL, 'Coin',         NULL, 'DIR',   '',      3,  TRUE, CURRENT_TIMESTAMP, 0),
(4,  '业务单据', 'menu-business',     '/business',     NULL, 'Document',     NULL, 'DIR',   '',      4,  TRUE, CURRENT_TIMESTAMP, 0),
(5,  '税务发票', 'menu-tax',          '/tax',          NULL, 'Ticket',       NULL, 'DIR',   '',      5,  TRUE, CURRENT_TIMESTAMP, 0),
(6,  '固定资产', 'menu-asset',        '/asset',        NULL, 'Box',          NULL, 'DIR',   '',      6,  TRUE, CURRENT_TIMESTAMP, 0),
(7,  '报表中心', 'menu-report',       '/report',       NULL, 'DataAnalysis', NULL, 'DIR',   '',      7,  TRUE, CURRENT_TIMESTAMP, 0);

-- ===================== 首页 =====================
INSERT INTO t_menu (id, menu_name, menu_code, path, component, icon, parent_id, menu_type, permission, sort_order, is_active, created_at, deleted)
OVERRIDING SYSTEM VALUE VALUES
(10, '仪表盘', 'menu-dashboard-home', '/dashboard', 'dashboard/DashboardView', 'Dashboard', 1, 'MENU', '', 1, TRUE, CURRENT_TIMESTAMP, 0);

-- ===================== 基础数据 =====================
INSERT INTO t_menu (id, menu_name, menu_code, path, component, icon, parent_id, menu_type, permission, sort_order, is_active, created_at, deleted)
OVERRIDING SYSTEM VALUE VALUES
(21, '会计科目', 'menu-basis-subject',     '/basis/subject',              'basis/subject/SubjectList',              'List',         2, 'MENU', 'subjects:manage',         1, TRUE, CURRENT_TIMESTAMP, 0),
(22, '会计期间', 'menu-basis-period',      '/basis/period',               'basis/period/PeriodList',                'Calendar',     2, 'MENU', 'periods:manage',           2, TRUE, CURRENT_TIMESTAMP, 0),
(23, '常用摘要', 'menu-basis-summary',     '/basis/summary-lib',          'basis/summary-lib/SummaryLibList',       'EditPen',      2, 'MENU', 'summary:lib:list',       3, TRUE, CURRENT_TIMESTAMP, 0),
(24, '客商档案', 'menu-basis-party',       '/basis/party',                'basis/party/PartyList',                  'UserFilled',   2, 'MENU', 'party:list',             4, TRUE, CURRENT_TIMESTAMP, 0),
(25, '分类规则', 'menu-basis-classify',    '/system/classification-rule', 'system/classification-rule/ClassificationRuleList', 'Collection', 2, 'MENU', '',                    5, TRUE, CURRENT_TIMESTAMP, 0),
(26, '系统参数', 'menu-basis-config',      '/basis/config',               'basis/config/SysConfigList',             'Setting',      2, 'MENU', 'sys:config:list',        6, TRUE, CURRENT_TIMESTAMP, 0),
(27, '凭证类型', 'menu-basis-vouchertype', '/finance/voucher-setup?tab=type', 'finance/voucher-setup/VoucherSetupView', 'Setting', 2, 'MENU', 'voucher:type:list',     7, TRUE, CURRENT_TIMESTAMP, 0);

-- ===================== 财务核心 =====================
INSERT INTO t_menu (id, menu_name, menu_code, path, component, icon, parent_id, menu_type, permission, sort_order, is_active, created_at, deleted)
OVERRIDING SYSTEM VALUE VALUES
(31, '凭证管理', 'menu-finance-voucher',      '/finance/voucher',             'finance/voucher/VoucherList',           'DocumentCopy', 3, 'MENU', 'voucher:list',                1, TRUE, CURRENT_TIMESTAMP, 0),
(32, '凭证模板', 'menu-finance-template',     '/finance/voucher-setup?tab=template', 'finance/voucher-setup/VoucherSetupView', 'Setting', 3, 'MENU', 'voucher:template:list',   2, TRUE, CURRENT_TIMESTAMP, 0),
(33, '账簿查询', 'menu-finance-ledger',       '/finance/ledger',              'finance/ledger/LedgerView',             'Reading',      3, 'MENU', 'ledger:list',                   3, TRUE, CURRENT_TIMESTAMP, 0),
(34, '期末结账', 'menu-finance-periodclose',  '/finance/period-close',        'finance/period-close/PeriodCloseView',  'Finished',     3, 'MENU', 'period:close',                  4, TRUE, CURRENT_TIMESTAMP, 0),
(35, '期初建账', 'menu-finance-beginning',    '/finance/beginning-balance',   'finance/beginning-balance/BeginningBalanceView', 'Coin', 3, 'MENU', 'beginning:balance:init',  5, TRUE, CURRENT_TIMESTAMP, 0),
(36, '结转向导', 'menu-finance-carryover',    '/finance/carryover-guide',     'finance/period-close/CarryOverGuide',   'Right',        3, 'MENU', 'period:carryover:guide',    6, TRUE, CURRENT_TIMESTAMP, 0);

-- ===================== 业务单据 =====================
INSERT INTO t_menu (id, menu_name, menu_code, path, component, icon, parent_id, menu_type, permission, sort_order, is_active, created_at, deleted)
OVERRIDING SYSTEM VALUE VALUES
(41, '业务单据',   'menu-business-doc',          '/finance/business-doc',        'finance/business-doc/BusinessDocList',         'List',         4, 'MENU', 'doc:list',                        1, TRUE, CURRENT_TIMESTAMP, 0),
(42, '银行账户',   'menu-business-bankacct',     '/finance/bank-account',        'finance/bank-account/BankAccountList',         'CreditCard',   4, 'MENU', 'bank:account:list',              2, TRUE, CURRENT_TIMESTAMP, 0),
(43, '银行日记账', 'menu-business-bankjournal',  '/finance/bank-journal',        'finance/bank-journal/BankJournalList',         'Document',     4, 'MENU', 'bank:journal:list',              3, TRUE, CURRENT_TIMESTAMP, 0),
(44, '银行对账单', 'menu-business-bankstmt',     '/finance/bank-statement',      'finance/bank-statement/BankStatementView',     'DocumentCopy', 4, 'MENU', 'bank:statement:list',          4, TRUE, CURRENT_TIMESTAMP, 0),
(45, '银行对账',   'menu-business-recon',        '/finance/bank-reconciliation', 'finance/bank-reconciliation/ReconciliationView', 'DataAnalysis', 4, 'MENU', 'bank:reconciliation:list',   5, TRUE, CURRENT_TIMESTAMP, 0),
(46, '现金日记账', 'menu-business-cashjournal',  '/finance/cash-journal',        'finance/cash-journal/CashJournalList',         'Wallet',       4, 'MENU', 'cash:journal:list',              6, TRUE, CURRENT_TIMESTAMP, 0),
(47, '票据管理',   'menu-business-ticket',       '/finance/ticket',              'finance/ticket/TicketList',                    'Ticket',       4, 'MENU', 'ticket:list',                    7, TRUE, CURRENT_TIMESTAMP, 0),
(48, '核销工作台', 'menu-business-recon-wb',     '/arap/reconciliation-workbench', 'arap/reconciliation-workbench/ReconciliationWorkbench', 'Connection', 4, 'MENU', 'arap:reconciliation:workbench', 8, TRUE, CURRENT_TIMESTAMP, 0),
(49, '费用报销',   'menu-business-expense',      '/finance/business-doc?tab=expense', 'finance/business-doc/BusinessDocList', 'EditPen', 4, 'MENU', 'arap:expense:list',          9, TRUE, CURRENT_TIMESTAMP, 0);

-- ===================== 税务发票 =====================
INSERT INTO t_menu (id, menu_name, menu_code, path, component, icon, parent_id, menu_type, permission, sort_order, is_active, created_at, deleted)
OVERRIDING SYSTEM VALUE VALUES
(51, '进项发票',   'menu-tax-input',   '/tax/input-invoice',  'tax/input-invoice/InputInvoiceList',  'Document',     5, 'MENU', 'tax:input:list',    1, TRUE, CURRENT_TIMESTAMP, 0),
(52, '销项发票',   'menu-tax-output',  '/tax/output-invoice', 'tax/output-invoice/OutputInvoiceList','DocumentCopy', 5, 'MENU', 'tax:output:list',   2, TRUE, CURRENT_TIMESTAMP, 0),
(53, '增值税计算', 'menu-tax-vat',     '/tax/vat',           'tax/declaration/TaxVatView',           'DataAnalysis', 5, 'MENU', 'tax:vat:view',      3, TRUE, CURRENT_TIMESTAMP, 0);

-- ===================== 固定资产 =====================
INSERT INTO t_menu (id, menu_name, menu_code, path, component, icon, parent_id, menu_type, permission, sort_order, is_active, created_at, deleted)
OVERRIDING SYSTEM VALUE VALUES
(61, '资产类别',   'menu-asset-category',  '/asset/category',       'asset/category/AssetCategoryList',      'List',       6, 'MENU', 'asset:category:list',      1, TRUE, CURRENT_TIMESTAMP, 0),
(62, '资产卡片',   'menu-asset-card',      '/asset/card',           'asset/card/AssetCardList',             'Document',   6, 'MENU', 'asset:card:list',          2, TRUE, CURRENT_TIMESTAMP, 0),
(63, '折旧计提',   'menu-asset-depreciate','/asset/depreciation',   'asset/depreciation/AssetDepreciationView', 'Setting', 6, 'MENU', 'asset:depreciation:run',   3, TRUE, CURRENT_TIMESTAMP, 0),
(64, '资产处置',   'menu-asset-disposal',  '/asset/disposal',       'asset/disposal/AssetDisposalList',     'Delete',     6, 'MENU', 'asset:disposal:list',      4, TRUE, CURRENT_TIMESTAMP, 0),
(65, '资产盘点',   'menu-asset-inventory', '/asset/inventory',      'asset/inventory/AssetInventoryList',   'Checked',    6, 'MENU', 'asset:inventory:list',     5, TRUE, CURRENT_TIMESTAMP, 0);

-- ===================== 报表中心 =====================
INSERT INTO t_menu (id, menu_name, menu_code, path, component, icon, parent_id, menu_type, permission, sort_order, is_active, created_at, deleted)
OVERRIDING SYSTEM VALUE VALUES
(71, '科目余额表',   'menu-report-balancesheet',   '/report/subject-balance',   'report/subject-balance/SubjectBalanceView', 'List',         7, 'MENU', 'report:subject:list',        1, TRUE, CURRENT_TIMESTAMP, 0),
(72, '资产负债表',   'menu-report-balance',        '/report/balance-sheet',     'report/balance-sheet/BalanceSheetView',      'Document',     7, 'MENU', 'report:balance:view',        2, TRUE, CURRENT_TIMESTAMP, 0),
(73, '利润表',       'menu-report-income',         '/report/income-statement',  'report/income-statement/IncomeStatementView','TrendCharts',  7, 'MENU', 'report:income:view',         3, TRUE, CURRENT_TIMESTAMP, 0),
(74, '现金流量表',   'menu-report-cashflow',       '/report/cash-flow',         'report/cash-flow/CashFlowView',             'TrendCharts',  7, 'MENU', 'report:cashflow:view',       4, TRUE, CURRENT_TIMESTAMP, 0);

-- ===================== 更新角色-菜单关联 =====================
DELETE FROM t_role_menu WHERE role_id = 1;
INSERT INTO t_role_menu (role_id, menu_id)
SELECT 1, id FROM t_menu WHERE deleted = 0;
