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

export function updateEnterprise(id: number, data: EnterpriseCreateDTO): Promise<EnterpriseVO> {
  return request.put(`/v1/agency/enterprises/${id}`, data)
}

export function deleteEnterprise(id: number): Promise<void> {
  return request.delete(`/v1/agency/enterprises/${id}`)
}

export function activateEnterprise(id: number): Promise<EnterpriseVO> {
  return request.post(`/v1/agency/enterprises/${id}/activate`)
}

export function suspendEnterprise(id: number): Promise<EnterpriseVO> {
  return request.post(`/v1/agency/enterprises/${id}/suspend`)
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

// ========== Sprint 6: 代理用户管理 ==========

export interface AgencyUserVO {
  id: number
  agencyId: number
  userId: number
  username: string
  realName: string
  agencyRole: string
  status: string
  enterpriseCount?: number
  createdAt: string
}

export interface AgencyUserCreateDTO {
  username: string
  password: string
  realName: string
  agencyRole: string
  agencyId: number
}

export function getAgencyUsers(params?: { keyword?: string }): Promise<AgencyUserVO[]> {
  return request.get('/v1/agency/users', { params })
}

export function createAgencyUser(data: AgencyUserCreateDTO): Promise<AgencyUserVO> {
  return request.post('/v1/agency/users', data)
}

export function suspendAgencyUser(id: number): Promise<void> {
  return request.post(`/v1/agency/users/${id}/suspend`)
}

export function reactivateAgencyUser(id: number): Promise<void> {
  return request.post(`/v1/agency/users/${id}/reactivate`)
}

export function terminateAgencyUser(id: number): Promise<void> {
  return request.post(`/v1/agency/users/${id}/terminate`)
}

// ========== Sprint 6: 客户分配管理 ==========

export interface AssignmentVO {
  id: number
  agencyUserId: number
  enterpriseId: number
  enterpriseName: string
  assignedAt: string
}

export interface AssignmentCreateDTO {
  agencyUserId: number
  enterpriseId: number
}

export function getAssignments(agencyUserId: number): Promise<AssignmentVO[]> {
  return request.get('/v1/agency/assignments', { params: { agencyUserId } })
}

export function assignEnterprise(data: AssignmentCreateDTO): Promise<void> {
  return request.post('/v1/agency/assignments', data)
}

export function unassignEnterprise(assignmentId: number): Promise<void> {
  return request.delete(`/v1/agency/assignments/${assignmentId}`)
}

// ========== Sprint 8: 主管仪表盘 ==========

export function getDashboard(): Promise<{ data: any }> {
  return request.get('/v1/agency/dashboard')
}

export function getAccountantDetail(userId: number): Promise<{ data: any }> {
  return request.get(`/v1/agency/dashboard/accountant/${userId}`)
}
