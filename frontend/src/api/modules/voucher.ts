import request from '@/api/request'
import type { PageResult } from '@/types/api'

/** 分录视图 */
export interface EntryVO {
  id?: number
  subjectId: number
  subjectCode?: string
  subjectName?: string
  debit: number
  credit: number
  summary?: string
  assistJson?: string
  sortOrder?: number
}

/** 凭证视图 */
export interface VoucherVO {
  id: number
  voucherNo: string
  period: string
  voucherTypeId: number
  voucherTypeName?: string
  voucherTypeCode?: string
  status: string
  totalDebit: number
  totalCredit: number
  summary?: string
  source?: string
  attachmentIds?: string
  createdBy?: number
  createdByName?: string
  createdAt?: string
  updatedBy?: number
  updatedAt?: string
  submittedBy?: number
  submittedByName?: string
  submittedAt?: string
  auditedBy?: number
  auditedByName?: string
  auditedAt?: string
  postedBy?: number
  postedByName?: string
  postedAt?: string
  reversedFrom?: number
  entries?: EntryVO[]
}

/** 分录 DTO */
export interface EntryDTO {
  id?: number
  subjectId: number
  debit: number
  credit: number
  summary?: string
  assistJson?: string
  sortOrder?: number
}

/** 创建/更新凭证 DTO */
export interface VoucherCreateDTO {
  id?: number
  period: string
  voucherTypeId: number
  summary?: string
  attachmentIds?: string
  entries: EntryDTO[]
}

/** 凭证查询参数 */
export interface VoucherQueryDTO {
  period?: string
  status?: string
  voucherTypeId?: number
  keyword?: string
  current?: number
  size?: number
}

/** 批量操作参数 */
export interface VoucherStatusDTO {
  ids: number[]
}

/**
 * 状态映射
 */
export const VOUCHER_STATUS_MAP: Record<string, string> = {
  DRAFT: '草稿',
  SUBMITTED: '已提交',
  AUDITED: '已审核',
  POSTED: '已记账',
}

export const VOUCHER_STATUS_OPTIONS = [
  { label: '草稿', value: 'DRAFT' },
  { label: '已提交', value: 'SUBMITTED' },
  { label: '已审核', value: 'AUDITED' },
  { label: '已记账', value: 'POSTED' },
]

/** 分页查询凭证 */
export function getVoucherPage(params: VoucherQueryDTO): Promise<PageResult<VoucherVO>> {
  return request.post('/vouchers/page', params)
}

/** 获取凭证详情 */
export function getVoucher(id: number): Promise<VoucherVO> {
  return request.get(`/vouchers/${id}`)
}

/** 创建凭证 */
export function createVoucher(data: VoucherCreateDTO): Promise<VoucherVO> {
  return request.post('/vouchers', data)
}

/** 修改凭证 */
export function updateVoucher(id: number, data: VoucherCreateDTO): Promise<VoucherVO> {
  return request.put(`/vouchers/${id}`, data)
}

/** 删除凭证 */
export function deleteVoucher(id: number): Promise<void> {
  return request.delete(`/vouchers/${id}`)
}

/** 提交凭证 */
export function submitVoucher(id: number): Promise<void> {
  return request.post(`/vouchers/${id}/submit`)
}

/** 批量提交 */
export function batchSubmitVouchers(data: VoucherStatusDTO): Promise<void> {
  return request.post('/vouchers/batch-submit', data)
}

/** 审核凭证 */
export function auditVoucher(id: number): Promise<void> {
  return request.post(`/vouchers/${id}/audit`)
}

/** 批量审核 */
export function batchAuditVouchers(data: VoucherStatusDTO): Promise<void> {
  return request.post('/vouchers/batch-audit', data)
}

/** 记账 */
export function postVoucher(id: number): Promise<void> {
  return request.post(`/vouchers/${id}/post`)
}

/** 批量记账 */
export function batchPostVouchers(data: VoucherStatusDTO): Promise<void> {
  return request.post('/vouchers/batch-post', data)
}

/** 红冲凭证 */
export function reverseVoucher(id: number): Promise<VoucherVO> {
  return request.post(`/vouchers/${id}/reverse`)
}
