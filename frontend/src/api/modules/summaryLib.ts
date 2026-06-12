import request from '@/api/request'
import type { PageResult } from '@/types/api'

export interface SummaryLibVO {
  id: number
  summaryCode: string
  summaryText: string
  category: string
  sortOrder: number
  isActive: boolean
  createdAt: string
}

export function getSummaryLibPage(params: { page: number; size: number; keyword?: string; category?: string }): Promise<PageResult<SummaryLibVO>> {
  return request.get('/summary-lib', { params })
}

export function getAllSummaryLib(): Promise<SummaryLibVO[]> {
  return request.get('/summary-lib/all')
}

export function getSummaryLib(id: number): Promise<SummaryLibVO> {
  return request.get(`/summary-lib/${id}`)
}

export function createSummaryLib(data: Partial<SummaryLibVO>): Promise<void> {
  return request.post('/summary-lib', data)
}

export function updateSummaryLib(id: number, data: Partial<SummaryLibVO>): Promise<void> {
  return request.put(`/summary-lib/${id}`, data)
}

export function deleteSummaryLib(id: number): Promise<void> {
  return request.delete(`/summary-lib/${id}`)
}
