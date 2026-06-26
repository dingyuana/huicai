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
  return request.get('/voucher-types', { params })
}

export function getAllVoucherTypes(): Promise<VoucherTypeVO[]> {
  return request.get('/voucher-types/all')
}

export function getVoucherType(id: number): Promise<VoucherTypeVO> {
  return request.get(`/voucher-types/${id}`)
}

export function createVoucherType(data: Partial<VoucherTypeVO>): Promise<void> {
  return request.post('/voucher-types', data)
}

export function updateVoucherType(id: number, data: Partial<VoucherTypeVO>): Promise<void> {
  return request.put(`/voucher-types/${id}`, data)
}

export function deleteVoucherType(id: number): Promise<void> {
  return request.delete(`/voucher-types/${id}`)
}
