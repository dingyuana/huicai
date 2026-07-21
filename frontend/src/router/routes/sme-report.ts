import type { RouteRecordRaw } from 'vue-router'

/**
 * SME 报表中心路由
 * 科目余额表、资产负债表、利润表、现金流量表
 */
const routes: RouteRecordRaw[] = [
  // ─── 科目余额表 ───
  {
    path: 'report/subject-balance',
    name: 'SubjectBalanceView',
    component: () => import('@/views/report/subject-balance/SubjectBalanceView.vue'),
    meta: { title: '科目余额表', permission: 'report:subject:list', keepAlive: true },
  },

  // ─── 资产负债表 ───
  {
    path: 'report/balance-sheet',
    name: 'BalanceSheetView',
    component: () => import('@/views/report/balance-sheet/BalanceSheetView.vue'),
    meta: { title: '资产负债表', permission: 'report:balance:view', keepAlive: true },
  },

  // ─── 利润表 ───
  {
    path: 'report/income-statement',
    name: 'IncomeStatementView',
    component: () => import('@/views/report/income-statement/IncomeStatementView.vue'),
    meta: { title: '利润表', permission: 'report:income:view', keepAlive: true },
  },

  // ─── 现金流量表 ───
  {
    path: 'report/cash-flow',
    name: 'CashFlowView',
    component: () => import('@/views/report/cash-flow/CashFlowView.vue'),
    meta: { title: '现金流量表', permission: 'report:cashflow:view', keepAlive: true },
  },
]

export default routes