import type { RouteRecordRaw } from 'vue-router'
import AppLayout from '@/layouts/AppLayout.vue'

/**
 * Agency 路由 — 代账公司批量处理引擎（待实现）
 * 占位文件，后续添加多客户账套管理、批量操作、CRM 等路由
 */
const routes: RouteRecordRaw[] = [
  {
    path: '/',
    component: AppLayout,
    children: [],
  },
]

export default routes