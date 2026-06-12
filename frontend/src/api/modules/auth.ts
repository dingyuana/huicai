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
}

export interface LoginResult {
  token: string
  refreshToken: string
  tokenType: string
  userInfo: UserInfo
}

export function login(data: LoginParams): Promise<LoginResult> {
  return request.post('/auth/login', data)
}

export function getUserInfo(): Promise<UserInfo> {
  return request.get('/auth/userinfo')
}