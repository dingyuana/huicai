import request from '@/api/request'

export function health() {
  return request.get('/system/health')
}

export function login(data: { username: string; password: string }) {
  return request.post('/auth/login', data)
}

export function getUserInfo() {
  return request.get('/auth/user-info')
}