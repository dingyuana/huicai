import request from '@/api/request'
import type { PageResult } from '@/types/api'

/** 会计期间 */
export interface PeriodVO {
  id: number
  year: number
  month: number
  periodCode: string
  startDate: string
  endDate: string
  status: string
  createdAt: string
}

/** 期间创建参数 */
export interface PeriodCreateParam {
  year: number
  month: number
  startDate: string
  endDate: string
}

export interface PeriodUpdateParam {
  startDate?: string
  endDate?: string
}

export function getPeriodPage(params: { page: number; size: number }): Promise<PageResult<PeriodVO>> {
  return request.get('/v1/periods', { params })
}

export function getAllPeriods(): Promise<PeriodVO[]> {
  return request.get('/v1/periods/all')
}

export function getPeriod(id: number): Promise<PeriodVO> {
  return request.get(`/v1/periods/${id}`)
}

export function createPeriod(data: PeriodCreateParam): Promise<void> {
  return request.post('/v1/periods', data)
}

export function updatePeriod(id: number, data: PeriodUpdateParam): Promise<void> {
  return request.put(`/v1/periods/${id}`, data)
}

export function deletePeriod(id: number): Promise<void> {
  return request.delete(`/v1/periods/${id}`)
}

export function openPeriod(id: number): Promise<void> {
  return request.post(`/v1/periods/${id}/open`)
}

export function closePeriod(id: number): Promise<void> {
  return request.post(`/v1/periods/${id}/close`)
}

export function lockPeriod(id: number): Promise<void> {
  return request.post(`/v1/periods/${id}/lock`)
}

export function unlockPeriod(id: number): Promise<void> {
  return request.post(`/v1/periods/${id}/unlock`)
}
