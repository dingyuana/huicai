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
    meta: { title: '预算管理', permission: 'lab:budget', keepAlive: true },
  },
  {
    path: 'budget/edit',
    name: 'BudgetEdit',
    component: () => import('@/views/budget/BudgetEdit.vue'),
    meta: { title: '编辑预算', permission: 'lab:budget' },
  },
  {
    path: 'budget/adjustment',
    name: 'AdjustmentList',
    component: () => import('@/views/budget/AdjustmentList.vue'),
    meta: { title: '预算调整', permission: 'lab:budget' },
  },

  // ─── 财务分析 ───
  {
    path: 'analysis/key-metrics',
    name: 'KeyMetricsView',
    component: () => import('@/views/analysis/key-metrics/KeyMetricsView.vue'),
    meta: { title: '关键指标', permission: 'lab:analysis', keepAlive: true },
  },
  {
    path: 'analysis/dupont',
    name: 'DupontView',
    component: () => import('@/views/analysis/dupont/DupontView.vue'),
    meta: { title: '杜邦分析', permission: 'lab:analysis', keepAlive: true },
  },

  // ─── 工资薪酬（待建）───
  {
    path: 'salary',
    name: 'SalaryList',
    component: () => import('@/views/salary/SalaryList.vue'),
    meta: { title: '工资薪酬', permission: 'lab:salary', keepAlive: true },
  },

  // ─── AI 能力（内嵌业务页，保留路由供直链）───
  {
    path: 'ai/task',
    name: 'AiTaskList',
    component: () => import('@/views/ai/task/AiTaskList.vue'),
    meta: { title: 'AI 任务', permission: 'lab:ai', keepAlive: true },
  },
  {
    path: 'ai/anomaly',
    name: 'AnomalyList',
    component: () => import('@/views/ai/anomaly/AnomalyList.vue'),
    meta: { title: 'AI 异常', permission: 'lab:ai', keepAlive: true },
  },
]

export default routes