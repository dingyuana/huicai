import axios from 'axios'
import { ElMessage } from 'element-plus'
import type { ApiResponse } from '@/types/api'

const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 30000,
  headers: { 'Content-Type': 'application/json' },
})

// 请求拦截器：注入 JWT + X-Enterprise-Id
request.interceptors.request.use((config) => {
  const token = localStorage.getItem('huicai_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  // S-26: 企业上下文 header
  const enterpriseId = localStorage.getItem('huicai_current_enterprise_id')
  if (enterpriseId) {
    config.headers['X-Enterprise-Id'] = enterpriseId
  }
  return config
})

// 响应拦截器：统一错误处理
request.interceptors.response.use(
  (response) => {
    // 后端 R 结构：{ code, msg, data }
    const body = response.data as ApiResponse
    if (body.code !== 200) {
      ElMessage.error(body.msg || '请求失败')
      return Promise.reject(new Error(body.msg))
    }
    return body.data
  },
  (error) => {
    if (error.response) {
      const { status, data } = error.response
      if (status === 401) {
        localStorage.removeItem('huicai_token')
        localStorage.removeItem('huicai_refresh_token')
        localStorage.removeItem('huicai_current_enterprise_id')
        window.location.href = '/login'
        return Promise.reject(error)
      }
      if (status === 403) {
        ElMessage.error('权限不足，请联系管理员')
        return Promise.reject(error)
      }
      // S-26: 跨租户/企业暂停错误
      const code = data?.code
      if (code === 20003) {
        ElMessage.warning('请先选择客户企业')
        window.location.href = '/agency/enterprise-list'
        return Promise.reject(error)
      }
      if (code === 20005) {
        ElMessage.error('该企业已被暂停，无法操作')
        return Promise.reject(error)
      }
      const msg = data?.msg || data?.error || '请求失败'
      ElMessage.error(msg)
    } else {
      ElMessage.error('网络异常，请检查连接')
    }
    return Promise.reject(error)
  },
)

export default request