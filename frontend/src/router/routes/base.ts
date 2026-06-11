import type { RouteRecordRaw } from 'vue-router'
import AppLayout from '@/layouts/AppLayout.vue'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/LoginView.vue'),
    meta: { title: '登录', layout: 'blank' },
  },
  {
    path: '/403',
    name: 'Forbidden',
    component: () => import('@/views/error/403.vue'),
    meta: { title: '无权限', layout: 'blank' },
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/error/404.vue'),
    meta: { title: '页面不存在', layout: 'blank' },
  },
  {
    path: '/',
    component: AppLayout,
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/DashboardView.vue'),
        meta: { title: '首页', keepAlive: true },
      },
      {
        path: 'system/user',
        name: 'UserList',
        component: () => import('@/views/system/user/UserList.vue'),
        meta: { title: '用户管理', permission: 'system:user:list', keepAlive: true },
      },
      {
        path: 'system/role',
        name: 'RoleList',
        component: () => import('@/views/system/role/RoleList.vue'),
        meta: { title: '角色管理', permission: 'system:role:list', keepAlive: true },
      },
      {
        path: 'system/menu',
        name: 'MenuList',
        component: () => import('@/views/system/menu/MenuList.vue'),
        meta: { title: '菜单管理', permission: 'system:menu:list', keepAlive: true },
      },
      // M2 基础数据管理
      {
        path: 'basis/subject',
        name: 'SubjectList',
        component: () => import('@/views/system/subject/SubjectList.vue'),
        meta: { title: '科目管理', permission: 'subjects:manage', keepAlive: true },
      },
      {
        path: 'basis/period',
        name: 'PeriodList',
        component: () => import('@/views/system/period/PeriodList.vue'),
        meta: { title: '会计期间', permission: 'periods:manage', keepAlive: true },
      },
      {
        path: 'basis/voucher-type',
        name: 'VoucherTypeList',
        component: () => import('@/views/system/voucher-type/VoucherTypeList.vue'),
        meta: { title: '凭证类型', permission: 'voucher:type:list', keepAlive: true },
      },
      {
        path: 'basis/summary-lib',
        name: 'SummaryLibList',
        component: () => import('@/views/system/summary-lib/SummaryLibList.vue'),
        meta: { title: '常用摘要', permission: 'summary:lib:list', keepAlive: true },
      },
      {
        path: 'basis/config',
        name: 'SysConfigList',
        component: () => import('@/views/system/config/SysConfigList.vue'),
        meta: { title: '系统参数', permission: 'sys:config:list', keepAlive: true },
      },
      {
        path: 'finance/voucher',
        name: 'VoucherList',
        component: () => import('@/views/finance/voucher/VoucherList.vue'),
        meta: { title: '凭证管理', permission: 'voucher:list', keepAlive: true },
      },
      {
        path: 'finance/voucher/edit',
        name: 'VoucherEdit',
        component: () => import('@/views/finance/voucher/VoucherEdit.vue'),
        meta: { title: '编辑凭证', permission: 'voucher:edit' },
      },
      {
        path: 'finance/voucher/detail',
        name: 'VoucherDetail',
        component: () => import('@/views/finance/voucher/VoucherDetail.vue'),
        meta: { title: '凭证详情', permission: 'voucher:list' },
      },
      {
        path: 'finance/ledger',
        name: 'LedgerView',
        component: () => import('@/views/finance/ledger/LedgerView.vue'),
        meta: { title: '账簿查询', permission: 'ledger:list', keepAlive: true },
      },
      {
        path: 'finance/period-close',
        name: 'PeriodClose',
        component: () => import('@/views/finance/period-close/PeriodCloseView.vue'),
        meta: { title: '期末结账', permission: 'period:close', keepAlive: true },
      },
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
        meta: { title: '编辑单据', permission: 'doc:edit' },
      },
      {
        path: 'finance/business-doc/detail',
        name: 'BusinessDocDetail',
        component: () => import('@/views/finance/business-doc/BusinessDocDetail.vue'),
        meta: { title: '单据详情', permission: 'doc:list' },
      },
    ],
  },
]

export default routes