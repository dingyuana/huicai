import type { RouteRecordRaw } from 'vue-router'

/**
 * SME 业务单据路由
 * 聚合：业务单据、银行账户、银行日记账、银行对账单、现金日记账、票据管理
 * 内嵌 Tab：核销工作台、费用报销
 */
const routes: RouteRecordRaw[] = [
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

  // ─── 核销工作台（作为 Tab 内嵌在业务单据页，但保留独立路由供直链）───
  {
    path: 'arap/reconciliation-workbench',
    name: 'ReconciliationWorkbench',
    component: () => import('@/views/arap/reconciliation-workbench/ReconciliationWorkbench.vue'),
    meta: { title: '核销工作台', permission: 'arap:reconciliation:workbench', keepAlive: true },
  },

  // ─── 费用报销（作为 Tab 内嵌在业务单据页）───
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
]

export default routes