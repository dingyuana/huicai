import type { RouteRecordRaw } from 'vue-router'
import AppLayout from '@/layouts/AppLayout.vue'

/**
 * SME 路由 — 中小微企业业务模块
 */
const routes: RouteRecordRaw[] = [
  {
    path: '/',
    component: AppLayout,
    children: [
      // ─── 业务单据 ───
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

      // ─── 银行账户 ───
      {
        path: 'finance/bank-account',
        name: 'BankAccountList',
        component: () => import('@/views/finance/bank-account/BankAccountList.vue'),
        meta: { title: '银行账户', permission: 'bank:account:list', keepAlive: true },
      },

      // ─── 银行日记账 ───
      {
        path: 'finance/bank-journal',
        name: 'BankJournalList',
        component: () => import('@/views/finance/bank-journal/BankJournalList.vue'),
        meta: { title: '银行日记账', permission: 'bank:journal:list', keepAlive: true },
      },

      // ─── 银行对账单 ───
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

      // ─── 银行对账 ───
      {
        path: 'finance/bank-reconciliation',
        name: 'ReconciliationView',
        component: () => import('@/views/finance/bank-reconciliation/ReconciliationView.vue'),
        meta: { title: '银行对账', permission: 'bank:reconciliation:list', keepAlive: true },
      },

      // ─── 现金日记账 ───
      {
        path: 'finance/cash-journal',
        name: 'CashJournalList',
        component: () => import('@/views/finance/cash-journal/CashJournalList.vue'),
        meta: { title: '现金日记账', permission: 'cash:journal:list', keepAlive: true },
      },

      // ─── 票据管理 ───
      {
        path: 'finance/ticket',
        name: 'TicketList',
        component: () => import('@/views/finance/ticket/TicketList.vue'),
        meta: { title: '票据管理', permission: 'ticket:list', keepAlive: true },
      },

      // ─── 固定资产 ───
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

      // ─── 应收应付/核销 ───
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
      {
        path: 'arap/payment-plan',
        name: 'PaymentPlan',
        component: () => import('@/views/arap/payment-plan/PaymentPlanList.vue'),
        meta: { title: '付款计划', permission: 'arap:payment:plan', keepAlive: true },
      },
      {
        path: 'arap/purchase-return',
        name: 'PurchaseReturn',
        component: () => import('@/views/arap/purchase-return/PurchaseReturnList.vue'),
        meta: { title: '采购退货', permission: 'arap:purchase:return:list', keepAlive: true },
      },

      // ─── 费用报销 ───
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

      // ─── 税务管理 ───
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

      // ─── 预算管理 ───
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

      // ─── 报表 ───
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

      // ─── 财务分析 ───
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

      // ─── AI 横切层 ───
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