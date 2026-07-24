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
    hasPermission, login, switchEnterprise, fetchUserInfo, logout,
  }
})