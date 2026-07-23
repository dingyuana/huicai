import request from '@/api/request'
import type { PageResult } from '@/types/api'

export interface EnterpriseVO {
  id: number
  enterpriseCode: string
  enterpriseName: string
  taxId: string
  mode: string
  agencyId: number
  status: string
  seedDataDone: boolean
  createdAt: string
}

export interface EnterpriseCreateDTO {
  enterpriseCode: string
  enterpriseName: string
  taxId?: string
  agencyId?: number
}

export interface BatchResult {
  total: number
  success: number
  failed: number
  details: { id: number; success: boolean; message: string }[]
}

export function getEnterpriseList(agencyId: number, page = 1, size = 10): Promise<PageResult<EnterpriseVO>> {
  return request.get('/v1/agency/enterprises/page', { params: { agencyId, page, size } })
}

export function createEnterprise(data: EnterpriseCreateDTO): Promise<EnterpriseVO> {
  return request.post('/v1/agency/enterprises', data)
}

export function activateEnterprise(id: number): Promise<EnterpriseVO> {
  return request.put(`/v1/agency/enterprises/${id}/activate`)
}

export function suspendEnterprise(id: number): Promise<EnterpriseVO> {
  return request.put(`/v1/agency/enterprises/${id}/suspend`)
}

export function batchImport(files: FormData, enterpriseId: number): Promise<BatchResult> {
  return request.post('/v1/agency/batch/import', files, {
    headers: { 'Content-Type': 'multipart/form-data' },
    params: { enterpriseId },
  })
}

export function batchAuditVouchers(voucherIds: number[], enterpriseId: number): Promise<BatchResult> {
  return request.post('/v1/agency/batch/audit-vouchers', voucherIds, { params: { enterpriseId } })
}

export function batchClose(enterpriseIds: number[], period: string): Promise<BatchResult> {
  return request.post('/v1/agency/batch/close', enterpriseIds, { params: { period } })
}
