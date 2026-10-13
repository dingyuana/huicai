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
    // ⚠️ request 的响应拦截器在运行期已返回 `body.data`（后端 R 结构解包），
    // 但其 TS 类型仍标注为 AxiosResponse<T>，故此处按运行期实际形态断言。
    // （依据：src/api/request.ts:38 `return body.data`）
    const res = (await request.post('/v1/enterprise/switch', null, {
      params: { enterpriseId },
    })) as unknown as { token?: string; refreshToken?: string }
    currentEnterpriseId.value = enterpriseId
    localStorage.setItem('huicai_current_enterprise_id', String(enterpriseId))

    // SPEC-P124 切片3：切换企业由服务端**重签** access 与 refresh 双 token。
    //
    // 此前企业上下文只存在于 localStorage 与 X-Enterprise-Id 请求头，
    // 服务端 token 里的 enterpriseId 永远是登录时的值 —— 一旦废止请求头，
    // 切换会在 access token 过期、走 /refresh 续期时被静默退回旧企业。
    // 故必须用返回的新 token 覆盖本地两个凭证。
    //
    // ⚠️ refreshToken 一并覆盖：它同样带 enterpriseId，只换 access 会把回退推迟到下次续期。
    if (res?.token) {
      token.value = res.token
      localStorage.setItem('huicai_token', res.token)
    }
    if (res?.refreshToken) {
      localStorage.setItem('huicai_refresh_token', res.refreshToken)
    }
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
      // SPEC-P123：企业上下文有三层语义，此处是**第三层（归属企业，最低优先级）**。
      //
      // ⚠️ 原写法 `if (info.enterpriseId !== undefined)` 是**缺陷本身**：
      //   null !== undefined 为真 ⇒ 代理会计（t_user.enterprise_id 按设计恒为 NULL）
      //   的有效「当前工作企业」会被 null 覆盖 ⇒ 刷新后被路由守卫弹回客户列表。
      //
      // 判据（§4.5 第 43 条）：**「归属企业」与「当前工作企业」是两个概念**，
      // 前者来自 t_user.enterprise_id（归属），后者是代理会计跨客户派工下的
      // 会话态选择（localStorage / X-Enterprise-Id 请求头）。
      //
      // 只有服务端**真的带了**归属企业时，才允许它覆盖本地选择；
      // 为 null 时一律保留本地已有的当前工作企业。
      if (info.enterpriseId != null) {
        currentEnterpriseId.value = info.enterpriseId
      }
      if (info.enterpriseList) enterpriseList.value = info.enterpriseList
    } catch {
      logout()
    }
  }

  /**
   * SPEC-P123：代理端**尚未选择当前客户**。
   *
   * <p>会计登录时 `t_user.enterprise_id` 按设计为 NULL（他跨客户服务，不「归属」任何一家），
   * 必须先在客户列表点「进入」选定当前客户才能进入业务页面。因此侧边栏要把业务菜单
   * 置灰并说明出路，而不是让用户点了没反应。
   *
   * <p>仅对 AGENCY 生效：企业自有用户的 `enterprise_id` 即其归属企业，不存在此状态。
   */
  const needsEnterpriseSelection = computed(
    () => isAgency.value && !currentEnterpriseId.value,
  )

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
    needsEnterpriseSelection,
    hasPermission, canAccess, login, switchEnterprise, fetchUserInfo, logout,
  }
})