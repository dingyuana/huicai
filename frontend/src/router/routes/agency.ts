import type { RouteRecordRaw } from 'vue-router'
import AppLayout from '@/layouts/AppLayout.vue'

const routes: RouteRecordRaw[] = [
  {
    path: '/agency',
    component: AppLayout,
    children: [
      {
        path: 'enterprise-list',
        name: 'AgencyEnterpriseList',
        component: () => import('@/views/agency/EnterpriseList.vue'),
        meta: { title: '客户列表' },
      },
      {
        path: 'batch-operation',
        name: 'AgencyBatchOperation',
        component: () => import('@/views/agency/BatchOperation.vue'),
        meta: { title: '批量操作' },
      },
    ],
  },
]

export default routes
