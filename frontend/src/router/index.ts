import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import baseRoutes from './routes/base'
import smeRoutes from './routes/sme'
import agencyRoutes from './routes/agency'
import { useAuthStore } from '@/stores/auth.store'

export const routes: RouteRecordRaw[] = [
  ...baseRoutes,
  ...smeRoutes,
  ...agencyRoutes,
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

// 路由守卫
router.beforeEach(async (to, _from, next) => {
  const authStore = useAuthStore()

  // 页面标题
  if (to.meta.title) {
    document.title = `${to.meta.title} - 慧财智能财务平台`
  }

  // 认证守卫
  if (!authStore.isLoggedIn && to.path !== '/login') {
    return next({ path: '/login', query: { redirect: to.fullPath } })
  }
  if (authStore.isLoggedIn && to.path === '/login') {
    return next({ path: '/dashboard' })
  }

  // 获取用户信息（登录后首次渲染）
  if (authStore.isLoggedIn && !authStore.userInfo) {
    await authStore.fetchUserInfo()
  }

  // 权限守卫：检查 meta.permission
  const requiredPerm = to.meta.permission as string | undefined
  if (requiredPerm && !authStore.hasPermission(requiredPerm)) {
    return next({ path: '/403' })
  }

  next()
})

export default router