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
  return request.get('/configs', { params })
}

export function getAllConfigs(): Promise<SysConfigVO[]> {
  return request.get('/configs/all')
}

export function getConfigValues(keys: string[]): Promise<Record<string, string>> {
  return request.get('/configs/values', { params: { keys } })
}

export function getConfig(id: number): Promise<SysConfigVO> {
  return request.get(`/configs/${id}`)
}

export function createConfig(data: Partial<SysConfigVO>): Promise<void> {
  return request.post('/configs', data)
}

export function updateConfig(id: number, data: Partial<SysConfigVO>): Promise<void> {
  return request.put(`/configs/${id}`, data)
}

export function deleteConfig(id: number): Promise<void> {
  return request.delete(`/configs/${id}`)
}
