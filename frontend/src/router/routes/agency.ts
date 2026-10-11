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
        // SPEC-P120：批量结账/强制完成属跨客户管理动作，且后端
        // BatchOperationController 四个端点均无角色守卫。仅经理可入。
        meta: { title: '批量操作', agencyRole: 'AGENCY_ADMIN' },
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
      {
        path: 'service-progress',
        name: 'AgencyServiceProgress',
        component: () => import('@/views/agency/ServiceProgressView.vue'),
        // SPEC-P120：展示全部会计的跨客户工作量，含 force-done 写入口。
        // 会计/助理访问会看到无关客户与他人进度，仅经理可入。
        meta: { title: '服务进度与工作量', agencyRole: 'AGENCY_ADMIN' },
      },
    ],
  },
]

export default routes
