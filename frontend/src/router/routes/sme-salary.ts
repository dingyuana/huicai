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
  {
    // SPEC-P157 R4：员工自助只读页（V201 menu-salary 下 btn-salary-self-view → salary:self:view）
    path: 'salary/my',
    name: 'MySalaryView',
    component: () => import('@/views/salary/MySalaryView.vue'),
    meta: { title: '我的工资单', permission: 'salary:self:view', keepAlive: true },
  },
]

export default routes
