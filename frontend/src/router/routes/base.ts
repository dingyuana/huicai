import type { RouteRecordRaw } from 'vue-router'
import AppLayout from '@/layouts/AppLayout.vue'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/LoginView.vue'),
    meta: { title: '登录', layout: 'blank' },
  },
  {
    path: '/403',
    name: 'Forbidden',
    component: () => import('@/views/error/403.vue'),
    meta: { title: '无权限', layout: 'blank' },
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/error/404.vue'),
    meta: { title: '页面不存在', layout: 'blank' },
  },
  {
    path: '/',
    component: AppLayout,
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/DashboardView.vue'),
        meta: { title: '首页', keepAlive: true },
      },
      {
        path: 'system/user',
        name: 'UserList',
        component: () => import('@/views/system/user/UserList.vue'),
        meta: { title: '用户管理', permission: 'system:user:list', keepAlive: true },
      },
      {
        path: 'system/role',
        name: 'RoleList',
        component: () => import('@/views/system/role/RoleList.vue'),
        meta: { title: '角色管理', permission: 'system:role:list', keepAlive: true },
      },
      {
        path: 'system/menu',
        name: 'MenuList',
        component: () => import('@/views/system/menu/MenuList.vue'),
        meta: { title: '菜单管理', permission: 'system:menu:list', keepAlive: true },
      },
      {
        path: 'system/dept',
        name: 'DeptList',
        component: () => import('@/views/system/dept/DeptList.vue'),
        meta: { title: '部门管理', permission: 'system:dept:list', keepAlive: true },
      },
      {
        path: 'system/audit-log',
        name: 'AuditLogList',
        component: () => import('@/views/system/audit-log/AuditLogList.vue'),
        meta: { title: '操作日志', permission: 'system:audit:list', keepAlive: true },
      },
      {
        path: 'system/clear-data',
        name: 'ClearDataView',
        component: () => import('@/views/system/clear-data/ClearDataView.vue'),
        meta: { title: '数据维护', keepAlive: true },
      },
      {
        path: 'system/classification-rule',
        name: 'ClassificationRuleList',
        component: () => import('@/views/system/classification-rule/ClassificationRuleList.vue'),
        meta: { title: '分类规则', keepAlive: true },
      },
      // M2 基础数据管理
      {
        path: 'basis/subject',
        name: 'SubjectList',
        component: () => import('@/views/system/subject/SubjectList.vue'),
        meta: { title: '科目管理', permission: 'subjects:manage', keepAlive: true },
      },
      {
        path: 'basis/period',
        name: 'PeriodList',
        component: () => import('@/views/system/period/PeriodList.vue'),
        meta: { title: '会计期间', permission: 'periods:manage', keepAlive: true },
      },
      {
        path: 'basis/summary-lib',
        name: 'SummaryLibList',
        component: () => import('@/views/system/summary-lib/SummaryLibList.vue'),
        meta: { title: '常用摘要', permission: 'summary:lib:list', keepAlive: true },
      },
      {
        path: 'basis/config',
        name: 'SysConfigList',
        component: () => import('@/views/system/config/SysConfigList.vue'),
        meta: { title: '系统参数', permission: 'sys:config:list', keepAlive: true },
      },
      {
        path: 'finance/voucher',
        name: 'VoucherList',
        component: () => import('@/views/finance/voucher/VoucherList.vue'),
        meta: { title: '凭证管理', permission: 'voucher:list', keepAlive: true },
      },
      {
        path: 'finance/voucher-setup',
        name: 'VoucherSetupView',
        component: () => import('@/views/finance/voucher-setup/VoucherSetupView.vue'),
        meta: { title: '凭证设置', permission: 'voucher:type:list', keepAlive: true },
      },
      {
        path: 'finance/voucher/edit',
        name: 'VoucherEdit',
        component: () => import('@/views/finance/voucher/VoucherEdit.vue'),
        meta: { title: '编辑凭证', permission: 'voucher:update' },
      },
      {
        path: 'finance/voucher/detail',
        name: 'VoucherDetail',
        component: () => import('@/views/finance/voucher/VoucherDetail.vue'),
        meta: { title: '凭证详情', permission: 'voucher:list' },
      },
      {
        path: 'finance/ledger',
        name: 'LedgerView',
        component: () => import('@/views/finance/ledger/LedgerView.vue'),
        meta: { title: '账簿查询', permission: 'ledger:list', keepAlive: true },
      },
      {
        path: 'finance/period-close',
        name: 'PeriodClose',
        component: () => import('@/views/finance/period-close/PeriodCloseView.vue'),
        meta: { title: '期末结账', permission: 'period:close', keepAlive: true },
      },
      {
        path: 'finance/carryover-guide',
        name: 'CarryOverGuide',
        component: () => import('@/views/finance/period-close/CarryOverGuide.vue'),
        meta: { title: '结转向导', permission: 'period:carryover:guide', keepAlive: true },
      },
      {
        path: 'finance/business-doc',
        name: 'BusinessDocList',
        component: () => import('@/views/finance/business-doc/BusinessDocList.vue'),
        meta: { title: '业务单据', permission: 'doc:list', keepAlive: true },
      },
      {
        path: 'finance/business-doc/edit',
        name: 'BusinessDocEdit',
        component: () => import('@/views/finance/business-doc/BusinessDocEdit.vue'),
        meta: { title: '编辑单据', permission: 'doc:list' },
      },
      {
        path: 'finance/business-doc/detail',
        name: 'BusinessDocDetail',
        component: () => import('@/views/finance/business-doc/BusinessDocDetail.vue'),
        meta: { title: '单据详情', permission: 'doc:list' },
      },
      {
        path: 'finance/bank-account',
        name: 'BankAccountList',
        component: () => import('@/views/finance/bank-account/BankAccountList.vue'),
        meta: { title: '银行账户', permission: 'bank:account:list', keepAlive: true },
      },
      {
        path: 'finance/bank-journal',
        name: 'BankJournalList',
        component: () => import('@/views/finance/bank-journal/BankJournalList.vue'),
        meta: { title: '银行日记账', permission: 'bank:journal:list', keepAlive: true },
      },
      {
        path: 'finance/bank-statement',
        name: 'BankStatementView',
        component: () => import('@/views/finance/bank-statement/BankStatementView.vue'),
        meta: { title: '银行对账单', permission: 'bank:statement:list', keepAlive: true },
      },
      {
        path: 'finance/pending-pool',
        name: 'PendingPool',
        component: () => import('@/views/finance/pending-pool/PendingPool.vue'),
        meta: { title: '待处理流水', permission: 'bank:statement:list', keepAlive: true },
      },
      {
        path: 'finance/bank-reconciliation',
        name: 'ReconciliationView',
        component: () => import('@/views/finance/bank-reconciliation/ReconciliationView.vue'),
        meta: { title: '银行对账', permission: 'bank:reconciliation:list', keepAlive: true },
      },
      {
        path: 'finance/cash-journal',
        name: 'CashJournalList',
        component: () => import('@/views/finance/cash-journal/CashJournalList.vue'),
        meta: { title: '现金日记账', permission: 'cash:journal:list', keepAlive: true },
      },
      {
        path: 'finance/ticket',
        name: 'TicketList',
        component: () => import('@/views/finance/ticket/TicketList.vue'),
        meta: { title: '票据管理', permission: 'ticket:list', keepAlive: true },
      },
      {
        path: 'finance/beginning-balance',
        name: 'BeginningBalanceView',
        component: () => import('@/views/finance/beginning-balance/BeginningBalanceView.vue'),
        meta: { title: '期初建账', permission: 'beginning:balance:init', keepAlive: true },
      },
      // 固定资产
      {
        path: 'asset/category',
        name: 'AssetCategoryList',
        component: () => import('@/views/asset/category/AssetCategoryList.vue'),
        meta: { title: '资产类别', permission: 'asset:category:list', keepAlive: true },
      },
      {
        path: 'asset/card',
        name: 'AssetCardList',
        component: () => import('@/views/asset/card/AssetCardList.vue'),
        meta: { title: '资产卡片', permission: 'asset:card:list', keepAlive: true },
      },
      {
        path: 'asset/disposal',
        name: 'AssetDisposalList',
        component: () => import('@/views/asset/disposal/AssetDisposalList.vue'),
        meta: { title: '资产处置', permission: 'asset:disposal:list', keepAlive: true },
      },
      // 往来管理
      {
        path: 'arap/aging',
        name: 'AgingAnalysis',
        component: () => import('@/views/arap/aging/AgingAnalysis.vue'),
        meta: { title: '账龄分析', permission: 'arap:aging:view', keepAlive: true },
      },
      {
        path: 'arap/customer',
        name: 'CustomerList',
        component: () => import('@/views/arap/customer/CustomerList.vue'),
        meta: { title: '客户档案', permission: 'customer:list', keepAlive: true },
      },
      {
        path: 'arap/vendor',
        name: 'VendorList',
        component: () => import('@/views/arap/vendor/VendorList.vue'),
        meta: { title: '供应商档案', permission: 'vendor:list', keepAlive: true },
      },

      {
        path: 'arap/bad-debt',
        name: 'BadDebtList',
        component: () => import('@/views/arap/bad-debt/BadDebtList.vue'),
        meta: { title: '坏账准备', permission: 'bad:debt:list', keepAlive: true },
      },
      {
        path: 'arap/aging-analysis',
        name: 'AgingAnalysis',
        component: () => import('@/views/arap/aging-analysis/AgingAnalysisView.vue'),
        meta: { title: '账龄分析', permission: 'aging:analysis:list', keepAlive: true },
      },
      {
        path: 'arap/prepayment',
        name: 'PrepaymentList',
        component: () => import('@/views/arap/prepayment/PrepaymentList.vue'),
        meta: { title: '预收/预付管理', permission: 'prepayment:list', keepAlive: true },
      },
      {
        path: 'arap/settlement',
        name: 'SettlementList',
        component: () => import('@/views/arap/settlement/SettlementList.vue'),
        meta: { title: '核销单', permission: 'arap:settlement:list', keepAlive: true },
      },
      {
        path: 'finance/arap/reconciliation-workbench',
        redirect: 'arap/reconciliation-workbench',
      },
      {
        path: 'arap/reconciliation-workbench',
        name: 'ReconciliationWorkbench',
        component: () => import('@/views/arap/reconciliation-workbench/ReconciliationWorkbench.vue'),
        meta: { title: '往来核销', permission: 'arap:reconciliation:workbench', keepAlive: true },
      },
      {
        path: 'arap/reconciliation-approval',
        name: 'ReconciliationApproval',
        component: () => import('@/views/arap/reconciliation-approval/ReconciliationApproval.vue'),
        meta: { title: '核销审批', permission: 'arap:reconciliation:approve', keepAlive: true },
      },
      {
        path: 'arap/reconciliation-exception',
        name: 'ReconciliationExceptionList',
        component: () => import('@/views/arap/reconciliation-exception/ReconciliationExceptionList.vue'),
        meta: { title: '核销异常池', permission: 'arap:reconciliation:exception', keepAlive: true },
      },
      {
        path: 'arap/customer-statement',
        name: 'CustomerStatement',
        component: () => import('@/views/arap/customer-statement/CustomerStatementList.vue'),
        meta: { title: '客户对账', permission: 'customer:statement:list', keepAlive: true },
      },
      // 费用报销
      {
        path: 'arap/expense',
        name: 'ExpenseList',
        component: () => import('@/views/arap/ExpenseList.vue'),
        meta: { title: '费用报销单', permission: 'arap:expense:list', keepAlive: true },
      },
      {
        path: 'arap/expense/edit',
        name: 'ExpenseEdit',
        component: () => import('@/views/arap/ExpenseEdit.vue'),
        meta: { title: '编辑报销单', permission: 'arap:expense:create' },
      },
      // 税务管理
      {
        path: 'tax/input-invoice',
        name: 'InputInvoiceList',
        component: () => import('@/views/tax/input-invoice/InputInvoiceList.vue'),
        meta: { title: '进项发票', permission: 'tax:input:list', keepAlive: true },
      },
      {
        path: 'tax/output-invoice',
        name: 'OutputInvoiceList',
        component: () => import('@/views/tax/output-invoice/OutputInvoiceList.vue'),
        meta: { title: '销项发票', permission: 'tax:output:list', keepAlive: true },
      },
      {
        path: 'tax/vat',
        name: 'TaxVatView',
        component: () => import('@/views/tax/declaration/TaxVatView.vue'),
        meta: { title: '增值税计算', permission: 'tax:vat:view', keepAlive: true },
      },
      // 预算
      {
        path: 'budget',
        name: 'BudgetList',
        component: () => import('@/views/budget/BudgetList.vue'),
        meta: { title: '预算管理', permission: 'budget:list', keepAlive: true },
      },
      {
        path: 'budget/edit',
        name: 'BudgetEdit',
        component: () => import('@/views/budget/BudgetEdit.vue'),
        meta: { title: '编辑预算', permission: 'budget:create' },
      },
      {
        path: 'budget/adjustment',
        name: 'AdjustmentList',
        component: () => import('@/views/budget/AdjustmentList.vue'),
        meta: { title: '预算调整', permission: 'budget:adjustment' },
      },
      // 报表
      {
        path: 'report/subject-balance',
        name: 'SubjectBalanceView',
        component: () => import('@/views/report/subject-balance/SubjectBalanceView.vue'),
        meta: { title: '科目余额表', permission: 'report:subject:list', keepAlive: true },
      },
      {
        path: 'report/balance-sheet',
        name: 'BalanceSheetView',
        component: () => import('@/views/report/balance-sheet/BalanceSheetView.vue'),
        meta: { title: '资产负债表', permission: 'report:balance:view', keepAlive: true },
      },
      {
        path: 'report/income-statement',
        name: 'IncomeStatementView',
        component: () => import('@/views/report/income-statement/IncomeStatementView.vue'),
        meta: { title: '利润表', permission: 'report:income:view', keepAlive: true },
      },
      {
        path: 'report/cash-flow',
        name: 'CashFlowView',
        component: () => import('@/views/report/cash-flow/CashFlowView.vue'),
        meta: { title: '现金流量表', permission: 'report:cashflow:view', keepAlive: true },
      },
      // 财务分析
      {
        path: 'analysis/key-metrics',
        name: 'KeyMetricsView',
        component: () => import('@/views/analysis/key-metrics/KeyMetricsView.vue'),
        meta: { title: '关键指标', permission: 'analysis:key:view', keepAlive: true },
      },
      {
        path: 'analysis/dupont',
        name: 'DupontView',
        component: () => import('@/views/analysis/dupont/DupontView.vue'),
        meta: { title: '杜邦分析', permission: 'analysis:dupont:view', keepAlive: true },
      },
      // AI
      {
        path: 'ai/task',
        name: 'AiTaskList',
        component: () => import('@/views/ai/task/AiTaskList.vue'),
        meta: { title: 'AI 任务', permission: 'ai:task:list', keepAlive: true },
      },
      {
        path: 'ai/anomaly',
        name: 'AnomalyList',
        component: () => import('@/views/ai/anomaly/AnomalyList.vue'),
        meta: { title: 'AI 异常', permission: 'ai:anomaly:list', keepAlive: true },
      },
    ],
  },
]

export default routes