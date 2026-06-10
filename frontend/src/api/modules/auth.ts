import request from '@/api/request'

/** 登录 */
export function login(account: string, password: string): Promise<any> {
  return request.post('/auth/login', { account, password })
}

/** 获取当前用户信息 */
export function getUserInfo(): Promise<any> {
  return request.get('/auth/userinfo')
}