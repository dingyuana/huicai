import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { login as loginApi, getUserInfo, type LoginParams, type UserInfo } from '@/api/modules/auth'
import request from '@/api/request'
import router from '@/router'

export interface EnterpriseSimple {
  id: number
  enterpriseName: string
  taxId: string
  status: string
  seedDataDone: boolean
}

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('huicai_token') || '')
  const userInfo = ref<UserInfo | null>(null)
  const permissions = ref<string[]>([])

  // S-26: 多租户状态
  const currentEnterpriseId = ref<number | null>(
    Number(localStorage.getItem('huicai_current_enterprise_id')) || null
  )
  const userType = ref<string>('')
  const agencyId = ref<number | null>(null)
  const agencyRole = ref<string>('')
  const enterpriseList = ref<EnterpriseSimple[]>([])

  const isLoggedIn = computed(() => !!token.value)

  /** 是否为超级管理员 */
  const isSuperAdmin = computed(() => userType.value === 'SUPER_ADMIN')

  /** 是否为代账公司用户 */
  const isAgency = computed(() => userType.value === 'AGENCY')

  // S-26: 代理内角色判断
  /** 是否为代账公司经理 */
  const isAgencyAdmin = computed(() => agencyRole.value === 'AGENCY_ADMIN')
  /** 是否为代账公司会计 */
  const isAccountant = computed(() => agencyRole.value === 'ACCOUNTANT')
  /** 是否为代账公司审核员 */
  const isReviewer = computed(() => agencyRole.value === 'REVIEWER')
  /** 是否为代账公司助理 */
  const isAssistant = computed(() => agencyRole.value === 'ASSISTANT')

  function hasPermission(perm: string): boolean {
    if (!perm) return true
    return permissions.value.includes(perm)
  }

  /**
   * 菜单/路由是否应展示 —— 与路由守卫**同一套口径**。
   *
   * <p>SPEC-P121：此前侧边栏 58 个菜单项**全部无条件渲染**，只按 userType 分组；
   * 而路由守卫按 `meta.permission` 拦截 ⇒ 用户「看得见却点不开」，
   * 点了只会被送进 /403，感知为「点了没反应」。
   *
   * <p>⚠️ 关键设计：**从路由表反查 `meta.permission`**，而不是在菜单里
   *   手写权限码字符串。后者必然与路由脱节（改路由忘改菜单 = 又一个恒绿缺陷）。
   *
   * @param path 路由 path，如 '/finance/voucher'
   * @param requiredPerm 路由声明的权限码；无权限码的路由一律放行
   */
  function canAccess(path: string, requiredPerm?: string): boolean {
    // 优先用调用方传入的权限码（与路由 meta 一致）
    let perm = requiredPerm
    if (!perm) {
      // 从路由表反查，避免在菜单里手写权限码字符串而与路由脱节
      const resolved = router.getRoutes().find((r) => r.path === path)
      perm = resolved?.meta?.permission as string | undefined
    }
    // 未声明 permission 的路由不拦（与 router/index.ts 的守卫一致）
    if (!perm) return true
    return permissions.value.includes(perm)
  }

  async function login(credentials: LoginParams) {
    const result = await loginApi(credentials)
    token.value = result.token
    userInfo.value = result.userInfo
    permissions.value = result.userInfo.permissions

    // S-26: 填充多租户字段
    userType.value = result.userType || 'ENTERPRISE'
    agencyId.value = result.agencyId || null
    agencyRole.value = result.agencyRole || ''
    enterpriseList.value = result.enterpriseList || []
    if (result.enterpriseId) {
      currentEnterpriseId.value = result.enterpriseId
      localStorage.setItem('huicai_current_enterprise_id', String(result.enterpriseId))
    }

    localStorage.setItem('huicai_token', result.token)
    if (result.refreshToken) {
      localStorage.setItem('huicai_refresh_token', result.refreshToken)
    }
  }

  async function switchEnterprise(enterpriseId: number) {
    await request.post('/v1/enterprise/switch', null, { params: { enterpriseId } })
    currentEnterpriseId.value = enterpriseId
    localStorage.setItem('huicai_current_enterprise_id', String(enterpriseId))
  }

  async function fetchUserInfo() {
    try {
      const info = await getUserInfo()
      userInfo.value = info
      permissions.value = info.permissions
      // S-26: 恢复多租户字段（刷新页面时从 userinfo 接口获取）
      if (info.userType) userType.value = info.userType
      if (info.agencyId !== undefined) agencyId.value = info.agencyId
      if (info.agencyRole !== undefined) agencyRole.value = info.agencyRole
      if (info.enterpriseId !== undefined) currentEnterpriseId.value = info.enterpriseId
      if (info.enterpriseList) enterpriseList.value = info.enterpriseList
    } catch {
      logout()
    }
  }

  function logout() {
    token.value = ''
    userInfo.value = null
    permissions.value = []
    currentEnterpriseId.value = null
    userType.value = ''
    agencyId.value = null
    agencyRole.value = ''
    enterpriseList.value = []
    localStorage.removeItem('huicai_token')
    localStorage.removeItem('huicai_refresh_token')
    localStorage.removeItem('huicai_current_enterprise_id')
    router.replace('/login').finally(() => {
      window.location.replace('/login')
    })
  }

  return {
    token, userInfo, permissions,
    currentEnterpriseId, userType, agencyId, agencyRole, enterpriseList,
    isLoggedIn, isSuperAdmin, isAgency,
    isAgencyAdmin, isAccountant, isReviewer, isAssistant,
    hasPermission, canAccess, login, switchEnterprise, fetchUserInfo, logout,
  }
})