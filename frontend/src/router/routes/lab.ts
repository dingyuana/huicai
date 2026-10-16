// SPEC-P131：实验室路由不再声明 permission。
//
// 此前这些路由带着 `permission: 'lab:*'`，但 t_menu 中从无这些权限码
// => 无角色可拥有 => hasPermission 恒 false => 403。
// 而实验室模块本来就由功能开关 `labStore.enabled` 把守（关闭即 404）。
// 叠加一个无人能拥有的权限码，等于把页面双重锁死。
//
// 判据：门禁应只有一道且可解释；凭空多出的第二道只会掩盖第一道的意图。

import type { RouteRecordRaw } from 'vue-router'

/**
 * 实验室路由 — Phase 2+ 功能，受 Feature Flag 控制
 * 仅在 labStore.enabled 为 true 时注册
 * 入口：用户菜单下拉「实验室功能」开关
 */
const routes: RouteRecordRaw[] = [
  // ─── 预算管理 ───
  {
    path: 'budget',
    name: 'BudgetList',
    component: () => import('@/views/budget/BudgetList.vue'),
    meta: { title: '预算管理', keepAlive: true },
  },
  {
    path: 'budget/edit',
    name: 'BudgetEdit',
    component: () => import('@/views/budget/BudgetEdit.vue'),
    meta: { title: '编辑预算' },
  },
  {
    path: 'budget/adjustment',
    name: 'AdjustmentList',
    component: () => import('@/views/budget/AdjustmentList.vue'),
    meta: { title: '预算调整' },
  },

  // ─── 财务分析 ───
  {
    path: 'analysis/key-metrics',
    name: 'KeyMetricsView',
    component: () => import('@/views/analysis/key-metrics/KeyMetricsView.vue'),
    meta: { title: '关键指标', keepAlive: true },
  },
  {
    path: 'analysis/dupont',
    name: 'DupontView',
    component: () => import('@/views/analysis/dupont/DupontView.vue'),
    meta: { title: '杜邦分析', keepAlive: true },
  },

  // ─── 工资薪酬（待建）───
  {
    path: 'salary',
    name: 'SalaryList',
    component: () => import('@/views/salary/SalaryList.vue'),
    meta: { title: '工资薪酬', keepAlive: true },
  },

  // ─── AI 能力（内嵌业务页，保留路由供直链）───
  {
    path: 'ai/task',
    name: 'AiTaskList',
    component: () => import('@/views/ai/task/AiTaskList.vue'),
    meta: { title: 'AI 任务', keepAlive: true },
  },
  {
    path: 'ai/anomaly',
    name: 'AnomalyList',
    component: () => import('@/views/ai/anomaly/AnomalyList.vue'),
    meta: { title: 'AI 异常', keepAlive: true },
  },
]

export default routes