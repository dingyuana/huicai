import request from '@/api/request'
import type { PageResult } from '@/types/api'

export interface VoucherTypeVO {
  id: number
  code: string
  name: string
  sortOrder: number
  numberingRule: string
  isActive: boolean
  remark: string
  createdAt: string
}

export function getVoucherTypePage(params: { page: number; size: number }): Promise<PageResult<VoucherTypeVO>> {
  return request.get('/v1/voucher-types', { params })
}

export function getAllVoucherTypes(): Promise<VoucherTypeVO[]> {
  return request.get('/v1/voucher-types/all')
}

export function getVoucherType(id: number): Promise<VoucherTypeVO> {
  return request.get(`/v1/voucher-types/${id}`)
}

export function createVoucherType(data: Partial<VoucherTypeVO>): Promise<void> {
  return request.post('/v1/voucher-types', data)
}

export function updateVoucherType(id: number, data: Partial<VoucherTypeVO>): Promise<void> {
  return request.put(`/v1/voucher-types/${id}`, data)
}

export function deleteVoucherType(id: number): Promise<void> {
  return request.delete(`/v1/voucher-types/${id}`)
}
