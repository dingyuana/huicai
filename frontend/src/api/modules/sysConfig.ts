import request from '@/api/request'
import type { PageResult } from '@/types/api'

export interface SysConfigVO {
  id: number
  configKey: string
  configValue: string
  configType: string
  description: string
  isActive: boolean
  createdAt: string
}

export function getConfigPage(params: { page: number; size: number; keyword?: string; configType?: string }): Promise<PageResult<SysConfigVO>> {
  return request.get('/v1/configs', { params })
}

export function getAllConfigs(): Promise<SysConfigVO[]> {
  return request.get('/v1/configs/all')
}

export function getConfigValues(keys: string[]): Promise<Record<string, string>> {
  return request.get('/v1/configs/values', { params: { keys } })
}

export function getConfig(id: number): Promise<SysConfigVO> {
  return request.get(`/v1/configs/${id}`)
}

export function createConfig(data: Partial<SysConfigVO>): Promise<void> {
  return request.post('/v1/configs', data)
}

export function updateConfig(id: number, data: Partial<SysConfigVO>): Promise<void> {
  return request.put(`/v1/configs/${id}`, data)
}

export function deleteConfig(id: number): Promise<void> {
  return request.delete(`/v1/configs/${id}`)
}
