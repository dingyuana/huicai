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
  enterpriseId?: number | null
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
  enterpriseId: number | null
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