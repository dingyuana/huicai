import type { RouteRecordRaw } from 'vue-router'

/**
 * SME 工资薪酬路由（SPEC-P143 / REQ-2026-160）
 * 菜单行由 V197 种入（menu-salary-bill → /salary/bill），permission 列 salary:list。
 */
const routes: RouteRecordRaw[] = [
  {
    path: 'salary/bill',
    name: 'SalaryBillList',
    component: () => import('@/views/salary/SalaryList.vue'),
    meta: { title: '工资单', permission: 'salary:list', keepAlive: true },
  },
]

export default routes
