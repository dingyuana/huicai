import type { RouteRecordRaw } from 'vue-router'
import AppLayout from '@/layouts/AppLayout.vue'
import smeBusinessRoutes from './sme-business'
import smeTaxRoutes from './sme-tax'
import smeAssetRoutes from './sme-asset'
import smeReportRoutes from './sme-report'
import labRoutes from './lab'

/**
 * SME 基础路由 — 所有 SME 用户通用的财务骨架
 * 对应原 base.ts，重命名以体现归属
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

      // ─── 基础数据 ───
      {
        path: 'basis/account-and-summary',
        name: 'AccountAndSummaryView',
        component: () => import('@/views/basis/AccountAndSummaryView.vue'),
        meta: { title: '科目摘要', permission: 'subjects:manage', keepAlive: true },
      },
      {
        path: 'basis/period',
        name: 'PeriodList',
        component: () => import('@/views/system/period/PeriodList.vue'),
        meta: { title: '会计期间', permission: 'periods:manage', keepAlive: true },
      },
      {
        path: 'basis/party',
        name: 'PartyList',
        component: () => import('@/views/basis/party/PartyList.vue'),
        meta: { title: '客商档案', permission: 'party:list', keepAlive: true },
      },
      {
        path: 'system/classification-rule',
        name: 'ClassificationRuleList',
        component: () => import('@/views/system/classification-rule/ClassificationRuleList.vue'),
        meta: { title: '分类规则', keepAlive: true },
      },
      {
        path: 'basis/config',
        name: 'SysConfigList',
        component: () => import('@/views/system/config/SysConfigList.vue'),
        meta: { title: '系统参数', permission: 'sys:config:list', keepAlive: true },
      },
      {
        path: 'system/clear-data',
        name: 'ClearDataView',
        component: () => import('@/views/system/clear-data/ClearDataView.vue'),
        meta: { title: '数据维护', keepAlive: true },
      },
      {
        path: 'finance/voucher-setup',
        name: 'VoucherSetupView',
        component: () => import('@/views/finance/voucher-setup/VoucherSetupView.vue'),
        meta: { title: '凭证设置', permission: 'voucher:type:list', keepAlive: true },
      },

      // ─── 财务核心 ───
      {
        path: 'finance/voucher',
        name: 'VoucherList',
        component: () => import('@/views/finance/voucher/VoucherList.vue'),
        meta: { title: '凭证管理', permission: 'voucher:list', keepAlive: true },
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
        path: 'finance/beginning-balance',
        name: 'BeginningBalanceView',
        component: () => import('@/views/finance/beginning-balance/BeginningBalanceView.vue'),
        meta: { title: '期初建账', permission: 'beginning:balance:init', keepAlive: true },
      },
      {
        path: 'finance/carryover-guide',
        name: 'CarryOverGuide',
        component: () => import('@/views/finance/period-close/CarryOverGuide.vue'),
        meta: { title: '结转向导', permission: 'period:carryover:guide', keepAlive: true },
      },

      // ─── 业务单据 ───
      ...smeBusinessRoutes,

      // ─── 税务发票 ───
      ...smeTaxRoutes,

      // ─── 资产管理 ───
      ...smeAssetRoutes,

      // ─── 财务报表 ───
      ...smeReportRoutes,

      // ─── 实验室路由（始终注册，通过路由守卫控制访问）───
      ...labRoutes,
    ],
  },
]

export default routes