import request from '@/api/request'

export interface LoginParams {
  username: string
  password: string
}

export interface UserInfo {
  id: number
  username: string
  realName: string
  nickname: string
  email: string
  phone: string
  avatar: string
  deptId: number
  roles: number[]
  permissions: string[]
  // S-26: 多租户字段
  userType?: string
  agencyId?: number | null
  agencyRole?: string
  // SPEC-P124 切片4：归属企业 与 当前工作企业 是两个概念，不可再共用一个字段名。
  // 代理会计的 homeEnterpriseId 按设计为 null（跨客户服务，不归属任何一家）。
  homeEnterpriseId?: number | null
  currentEnterpriseId?: number | null
  /** 是否需要先选择当前客户（仅代理端可能为 true）；让原因由后端给出 */
  enterpriseSelectionRequired?: boolean
  enterpriseList?: EnterpriseSimple[]
}

export interface EnterpriseSimple {
  id: number
  enterpriseName: string
  taxId: string
  status: string
  seedDataDone: boolean
}

export interface LoginResult {
  token: string
  refreshToken: string
  tokenType: string
  userType: string
  agencyRole?: string
  homeEnterpriseId: number | null
  currentEnterpriseId: number | null
  enterpriseSelectionRequired: boolean
  agencyId: number | null
  enterpriseList: EnterpriseSimple[]
  userInfo: UserInfo
}

export function login(data: LoginParams): Promise<LoginResult> {
  return request.post('/v1/auth/login', data)
}

export function getUserInfo(): Promise<UserInfo> {
  return request.get('/v1/auth/userinfo')
}