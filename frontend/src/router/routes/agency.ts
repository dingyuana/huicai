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
      {
        path: 'accountant-list',
        name: 'AgencyAccountantList',
        component: () => import('@/views/agency/AccountantList.vue'),
        meta: { title: '会计管理', agencyRole: 'AGENCY_ADMIN' },
      },
      {
        path: 'assignment-manage',
        name: 'AgencyAssignmentManage',
        component: () => import('@/views/agency/AssignmentManage.vue'),
        meta: { title: '客户分配', agencyRole: 'AGENCY_ADMIN' },
      },
      {
        path: 'dashboard',
        name: 'AgencyDashboard',
        component: () => import('@/views/agency/AgencyDashboard.vue'),
        meta: { title: '主管仪表盘', agencyRole: 'AGENCY_ADMIN' },
      },
      {
        path: 'accountant-detail/:userId',
        name: 'AgencyAccountantDetail',
        component: () => import('@/views/agency/AccountantDetail.vue'),
        meta: { title: '会计详情', agencyRole: 'AGENCY_ADMIN' },
      },
    ],
  },
]

export default routes
