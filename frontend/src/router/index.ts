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

// 组装 SME 完整路由
function buildSmeRoutes(): RouteRecordRaw[] {
  const labStore = useLabStore()

  const routes: RouteRecordRaw[] = [
    ...smeBaseRoutes,
    ...smeBusinessRoutes,
    ...smeTaxRoutes,
    ...smeAssetRoutes,
    ...smeReportRoutes,
  ]

  // 实验室路由：仅在 Feature Flag 开启时注册
  if (labStore.enabled) {
    routes.push(...labRoutes)
  }

  return routes
}

export const routes: RouteRecordRaw[] = [
  ...buildSmeRoutes(),
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

  // 已登录访问登录页 -> 首页
  if (to.path === '/login') {
    return next({ path: '/dashboard' })
  }

  // 权限校验
  const requiredPerm = to.meta.permission as string | undefined
  if (requiredPerm && !authStore.hasPermission(requiredPerm)) {
    return next({ path: '/403' })
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