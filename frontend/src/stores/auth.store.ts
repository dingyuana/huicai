import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { login as loginApi, getUserInfo, type LoginParams, type UserInfo } from '@/api/modules/auth'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('huicai_token') || '')
  const userInfo = ref<UserInfo | null>(null)
  const permissions = ref<string[]>([])

  const isLoggedIn = computed(() => !!token.value)

  /** 是否为超级管理员（角色 id=1 = ADMIN） */
  const isSuperAdmin = computed(() => {
    return userInfo.value?.roles?.includes(1) ?? false
  })

  function hasPermission(perm: string): boolean {
    if (!perm) return true
    return permissions.value.includes(perm)
  }

  async function login(credentials: LoginParams) {
    const result = await loginApi(credentials)
    token.value = result.token
    userInfo.value = result.userInfo
    permissions.value = result.userInfo.permissions
    localStorage.setItem('huicai_token', result.token)
    if (result.refreshToken) {
      localStorage.setItem('huicai_refresh_token', result.refreshToken)
    }
  }

  async function fetchUserInfo() {
    try {
      const info = await getUserInfo()
      userInfo.value = info
      permissions.value = info.permissions
    } catch {
      logout()
    }
  }

  function logout() {
    token.value = ''
    userInfo.value = null
    permissions.value = []
    localStorage.removeItem('huicai_token')
    localStorage.removeItem('huicai_refresh_token')
    window.location.href = '/login'
  }

  return { token, userInfo, permissions, isLoggedIn, isSuperAdmin, hasPermission, login, fetchUserInfo, logout }
})