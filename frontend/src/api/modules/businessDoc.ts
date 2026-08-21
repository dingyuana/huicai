import request from '@/api/request'
import type { PageResult } from '@/types/api'

export interface BusinessDocEntry {
  id?: number
  expenseType?: string
  subjectId?: number
  subjectCode?: string
  subjectName?: string
  amount: number
  invoiceNo?: string
  assistJson?: string
  summary?: string
  sortOrder?: number
}

export interface BusinessDocVO {
  id: number
  docNo: string
  docType: string
  docDate: string
  period: string
  amount: number
  status: string
  supplierId?: number
  supplierName?: string
  customerId?: number
  customerName?: string
  applicantId?: number
  deptId?: number
  summary?: string
  source?: string
  attachmentIds?: string
  voucherId?: number
  voucherNo?: string
  enrichedSummary?: string
  settledAmount?: number
  unsettledAmount?: number
  dueDate?: string
  sourceDocNo?: string
  createdByName?: string
  createdAt?: string
  submittedByName?: string
  submittedAt?: string
  approvedByName?: string
  approvedAt?: string
  entries: BusinessDocEntry[]
}

export const CUSTOMER_DOC_TYPES = ['RECEIPT', 'INVOICE_OUT', 'OTHER_RECEIVABLE']
export const SUPPLIER_DOC_TYPES = ['PAYMENT', 'EXPENSE', 'INVOICE_IN', 'OTHER_PAYABLE']
export const NO_COUNTERPARTY_DOC_TYPES = ['TRANSFER', 'EXPENSE', 'SALARY']

export interface BusinessDocDTO {
  id?: number
  docNo?: string
  docType: string
  docDate: string
  period: string
  amount: number
  supplierId?: number
  customerId?: number
  applicantId?: number
  deptId?: number
  summary?: string
  attachmentIds?: string
  settlementAccountId?: number
  entries: BusinessDocEntry[]
}

export interface BusinessDocQuery {
  docType?: string
  docTypes?: string[]
  status?: string
  period?: string
  keyword?: string
  startDate?: string
  endDate?: string
  amountMin?: number
  amountMax?: number
  current?: number
  size?: number
}

export const DOC_TYPE_LABELS: Record<string, string> = {
  RECEIPT: '收款单',
  PAYMENT: '付款单',
  EXPENSE: '费用报销单',
  INVOICE_IN: '应付单（采购）',
  INVOICE_OUT: '应收单（销售）',
  OTHER_RECEIVABLE: '应收单据',
  OTHER_PAYABLE: '应付单据',
  TRANSFER: '转账单',
  SALARY: '工资单',
}

export const DOC_STATUS_LABELS: Record<string, string> = {
  DRAFT: '草稿',
  SUBMITTED: '已提交',
  APPROVED: '已审批',
  VOUCHERED: '已生成凭证',
  PARTIALLY_RECONCILED: '部分核销',
  FULLY_RECONCILED: '已核销',
  REVERSED: '已冲销',
  CLOSED: '已关闭',
  REJECTED: '已驳回',
}

export function getBusinessDocPage(params: BusinessDocQuery): Promise<PageResult<BusinessDocVO>> {
  return request.post('/sme/arap/v1/business-docs/page', params)
}
export function getBusinessDoc(id: number): Promise<BusinessDocVO> {
  return request.get(`/sme/arap/v1/business-docs/${id}`)
}
export function createBusinessDoc(data: BusinessDocDTO): Promise<BusinessDocVO> {
  return request.post('/sme/arap/v1/business-docs', data)
}
export function updateBusinessDoc(id: number, data: BusinessDocDTO): Promise<BusinessDocVO> {
  return request.put(`/sme/arap/v1/business-docs/${id}`, data)
}
export function deleteBusinessDoc(id: number): Promise<void> {
  return request.delete(`/sme/arap/v1/business-docs/${id}`)
}
export function submitBusinessDoc(id: number): Promise<void> {
  return request.post(`/sme/arap/v1/business-docs/${id}/submit`)
}
export function approveBusinessDoc(id: number): Promise<void> {
  return request.post(`/sme/arap/v1/business-docs/${id}/approve`)
}
export function rejectBusinessDoc(id: number): Promise<void> {
  return request.post(`/sme/arap/v1/business-docs/${id}/reject`)
}
export function generateVoucherFromDoc(id: number): Promise<BusinessDocVO> {
  return request.post(`/sme/arap/v1/business-docs/${id}/generate-voucher`)
}
export function reverseBusinessDoc(id: number): Promise<BusinessDocVO> {
  return request.post(`/sme/arap/v1/business-docs/${id}/reverse`)
}
