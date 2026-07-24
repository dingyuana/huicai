import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import smeBaseRoutes from './routes/sme-base'
import smeBusinessRoutes from './routes/sme-business'
import smeTaxRoutes from './routes/sme-tax'
import smeAssetRoutes from './routes/sme-asset'
import smeReportRoutes from './routes/sme-report'
import labRoutes from './routes/lab'
import agencyRoutes from './routes/agency'
import { useAuthStore } from '@/stores/auth.store'
import { useLabStore } from '@/stores/lab.store'

// 基础 SME 路由（不含实验室路由）
const baseSmeRoutes: RouteRecordRaw[] = [
  ...smeBusinessRoutes,
  ...smeTaxRoutes,
  ...smeAssetRoutes,
  ...smeReportRoutes,
]

export const routes: RouteRecordRaw[] = [
  ...smeBaseRoutes, // 包含 /login、/403、404 和 / 路由（带 children）
  ...agencyRoutes, // Agency 分支：当前为空，Phase 2 实现
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

// 路由守卫
router.beforeEach(async (to, _from, next) => {
  const authStore = useAuthStore()

  // 公开路由
  if (to.meta.layout === 'blank') {
    return next()
  }

  // 未登录重定向登录页
  if (!authStore.token) {
    if (to.path !== '/login') {
      return next({ path: '/login', query: { redirect: to.fullPath } })
    }
    return next()
  }

  // 已登录访问登录页 -> 按 userType 分发首页
  if (to.path === '/login') {
    const ut = authStore.userType
    if (ut === 'SUPER_ADMIN') return next({ path: '/dashboard' })
    if (ut === 'AGENCY') {
      if (!authStore.currentEnterpriseId) return next({ path: '/agency/enterprise-list' })
      return next({ path: '/dashboard' })
    }
    return next({ path: '/dashboard' })
  }

  // S-26: AGENCY 用户访问业务路由时，检查是否已选择企业
  if (authStore.isAgency && !authStore.currentEnterpriseId) {
    const allowedPaths = ['/agency/enterprise-list', '/agency/batch-operation', '/403', '/404']
    if (!allowedPaths.includes(to.path)) {
      return next({ path: '/agency/enterprise-list' })
    }
  }

  // 页面刷新后重新获取用户信息（token存在但userInfo为空）
  if (authStore.token && !authStore.userInfo) {
    try {
      await authStore.fetchUserInfo()
    } catch {
      return next({ path: '/login' })
    }
  }

  // 权限校验（SUPER_ADMIN 跳过）
  if (!authStore.isSuperAdmin) {
    const requiredPerm = to.meta.permission as string | undefined
    if (requiredPerm && !authStore.hasPermission(requiredPerm)) {
      return next({ path: '/403' })
    }
  }

  // 实验室路由保护：Flag 关闭时访问实验室路由 -> 404
  const labStore = useLabStore()
  const isLabRoute = labRoutes.some(r => to.matched.some(m => m.path === r.path))
  if (isLabRoute && !labStore.enabled) {
    return next({ path: '/404' })
  }

  next()
})

export default router