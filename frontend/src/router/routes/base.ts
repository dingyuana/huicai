import type { RouteRecordRaw } from 'vue-router'
import AppLayout from '@/layouts/AppLayout.vue'

/**
 * 底座路由 — 所有企业通用的财务骨架
 */
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
      // ─── 首页 ───
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/DashboardView.vue'),
        meta: { title: '首页', keepAlive: true },
      },

      // ─── 系统管理 ───
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
      {
        path: 'system/dept',
        name: 'DeptList',
        component: () => import('@/views/system/dept/DeptList.vue'),
        meta: { title: '部门管理', permission: 'system:dept:list', keepAlive: true },
      },
      {
        path: 'system/audit-log',
        name: 'AuditLogList',
        component: () => import('@/views/system/audit-log/AuditLogList.vue'),
        meta: { title: '操作日志', permission: 'system:audit:list', keepAlive: true },
      },
      {
        path: 'system/clear-data',
        name: 'ClearDataView',
        component: () => import('@/views/system/clear-data/ClearDataView.vue'),
        meta: { title: '数据维护', keepAlive: true },
      },
      {
        path: 'system/classification-rule',
        name: 'ClassificationRuleList',
        component: () => import('@/views/system/classification-rule/ClassificationRuleList.vue'),
        meta: { title: '分类规则', keepAlive: true },
      },

      // ─── 基础数据 ───
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
        path: 'basis/summary-lib',
        name: 'SummaryLibList',
        component: () => import('@/views/system/summary-lib/SummaryLibList.vue'),
        meta: { title: '常用摘要', permission: 'summary:lib:list', keepAlive: true },
      },
      {
        path: 'basis/config',
        name: 'SysConfigView',
        component: () => import('@/views/system/config/SysConfigView.vue'),
        meta: { title: '系统参数', permission: 'sys:config:list', keepAlive: true },
      },

      // ─── 凭证引擎 ───
      {
        path: 'finance/voucher',
        name: 'VoucherList',
        component: () => import('@/views/finance/voucher/VoucherList.vue'),
        meta: { title: '凭证管理', permission: 'voucher:list', keepAlive: true },
      },
      {
        path: 'finance/voucher-setup',
        name: 'VoucherSetupView',
        component: () => import('@/views/finance/voucher-setup/VoucherSetupView.vue'),
        meta: { title: '凭证设置', permission: 'voucher:type:list', keepAlive: true },
      },
      {
        path: 'finance/voucher/edit',
        name: 'VoucherEdit',
        component: () => import('@/views/finance/voucher/VoucherEdit.vue'),
        meta: { title: '编辑凭证', permission: 'voucher:update' },
      },
      {
        path: 'finance/voucher/detail',
        name: 'VoucherDetail',
        component: () => import('@/views/finance/voucher/VoucherDetail.vue'),
        meta: { title: '凭证详情', permission: 'voucher:list' },
      },

      // ─── 账簿查询 ───
      {
        path: 'finance/ledger',
        name: 'LedgerView',
        component: () => import('@/views/finance/ledger/LedgerView.vue'),
        meta: { title: '账簿查询', permission: 'ledger:list', keepAlive: true },
      },

      // ─── 期末结账 ───
      {
        path: 'finance/period-close',
        name: 'PeriodClose',
        component: () => import('@/views/finance/period-close/PeriodCloseView.vue'),
        meta: { title: '期末结账', permission: 'period:close', keepAlive: true },
      },
      {
        path: 'finance/carryover-guide',
        name: 'CarryOverGuide',
        component: () => import('@/views/finance/period-close/CarryOverGuide.vue'),
        meta: { title: '结转向导', permission: 'period:carryover:guide', keepAlive: true },
      },

      // ─── 期初建账 ───
      {
        path: 'finance/beginning-balance',
        name: 'BeginningBalanceView',
        component: () => import('@/views/finance/beginning-balance/BeginningBalanceView.vue'),
        meta: { title: '期初建账', permission: 'beginning:balance:init', keepAlive: true },
      },
    ],
  },
]

export default routes