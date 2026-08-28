import request from '@/api/request'
import type { PageResult } from '@/types/api'

/** P55: 批量操作结果（与后端 BankStatementService.BatchResult 对齐） */
export interface BatchResult {
  total: number
  success: number
  failed: Array<{ id: number; reason: string }>
}

export interface BankStatementVO {
  id: number
  accountId: string
  txDate: string
  txType: string
  counterAccount?: string
  amount: number
  summary?: string
  externalNo?: string
  matchedJournalId?: number
  matchStatus: string
  importedAt?: string
  direction?: string
  batchId?: string
  classification?: string
  ruleId?: number
  aiConfidence?: number
  aiSuggestedAction?: string
  aiBusinessScene?: string
  subjectLevel1?: string
  subjectLevel2?: string
  subjectLevel3?: string
  reviewStatus?: string
  reviewedBy?: number
  reviewedAt?: string
  generatedDocId?: number
  generatedVoucherId?: number
  generatedDocNo?: string
  generatedVoucherNo?: string
  generatedAt?: string
}

export interface MatchSuggestion {
  statementId: number
  txDate: string
  amount: number
  counterAccount?: string
  matchedJournalId?: number
  score: number
}

export const MATCH_STATUS_LABELS: Record<string, string> = {
  UNMATCHED: '未匹配',
  MATCHED: '已匹配',
  MANUAL_MATCHED: '手工匹配',
  IGNORED: '已忽略',
}

export const CLASSIFICATION_LABELS: Record<string, string> = {
  business_receipt: '业务收款',
  business_payment: '业务付款',
  internal_transfer: '内部转账',
  tax_withholding: '税费扣缴',
  salary_social: '薪酬与社保',
  bank_interest_fee: '银行利息与手续费',
  financing_invest: '筹资与投资活动',
  other_unknown: '其它/待认领',
}

export const REVIEW_STATUS_LABELS: Record<string, string> = {
  PENDING: '待确认',
  CONFIRMED: '已确认',
  RECLASSIFIED: '已重分类',
  classified: '已分类',
  voucher_generated: 'A已制证',
  payment_created: 'B已生单',
  manual_pending: '待人工',
  approved: '已过账',
}

export function getBankStatementPage(params: {
  accountId?: string
  status?: string
  classification?: string
  reviewStatus?: string
  direction?: string
  counterAccount?: string
  summary?: string
  keyword?: string
  minAmount?: number
  maxAmount?: number
  startDate?: string
  endDate?: string
  current?: number
  size?: number
}): Promise<PageResult<BankStatementVO>> {
  return request.get('/sme/cash/v1/bank-statements/page', { params })
}
export function getClassificationCounts(accountId: string, reviewStatus?: string): Promise<Record<string, number>> {
  return request.get('/sme/cash/v1/bank-statements/classification-counts', { params: { accountId, reviewStatus } })
}
export function getBankStatementDetail(id: number): Promise<BankStatementVO> {
  return request.get(`/sme/cash/v1/bank-statements/${id}`)
}
export function importStatementCsv(accountId: string, csvContent: string): Promise<number> {
  return request.post('/sme/cash/v1/bank-statements/import-csv', csvContent, { params: { accountId }, headers: { 'Content-Type': 'text/plain' } })
}
export function importStatementExcel(accountId: string, file: File): Promise<{ total: number; success: number; classified: number; errors: Array<{ row: number; message: string }>; batchId: string }> {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/sme/cash/v1/bank-statements/import-excel', formData, { params: { accountId }, headers: { 'Content-Type': 'multipart/form-data' } })
}
export function previewStatementExcel(accountId: string, file: File): Promise<{
  total: number; valid: number; errors: any[]; batchId: string; previews: any[]; headers?: string[]
}> {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/sme/cash/v1/bank-statements/preview-excel', formData, { params: { accountId }, headers: { 'Content-Type': 'multipart/form-data' } })
}

/** 带自定义列映射的 Excel 预览 */
export function previewStatementExcelWithMapping(accountId: string, file: File, columnMapping: Record<string, string>): Promise<{
  total: number; valid: number; errors: any[]; batchId: string; previews: any[]; headers?: string[]
}> {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('columnMappingJson', JSON.stringify(columnMapping))
  return request.post('/sme/cash/v1/bank-statements/preview-excel', formData, { params: { accountId }, headers: { 'Content-Type': 'multipart/form-data' } })
}

/** 解析 Excel 表头 */
export function parseExcelHeaders(file: File): Promise<{
  headers: string[]; fields: Array<{ field: string; label: string; required: boolean }>
}> {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/sme/cash/v1/bank-statements/parse-headers', formData, { headers: { 'Content-Type': 'multipart/form-data' } })
}
export function confirmStatementImport(batchId: string): Promise<{ total: number; success: number; classified: number; batchId: string }> {
  return request.post('/sme/cash/v1/bank-statements/confirm-import', null, { params: { batchId } })
}
export function classifyStatement(id: number): Promise<BankStatementVO> {
  return request.post(`/sme/cash/v1/bank-statements/${id}/classify`)
}
export function reviewStatement(id: number): Promise<BankStatementVO> {
  return request.post(`/sme/cash/v1/bank-statements/${id}/review`)
}
export function batchReviewStatements(ids: number[]): Promise<BatchResult> {
  return request.post('/sme/cash/v1/bank-statements/batch-review', ids)
}
export function batchConfirmStatements(ids: number[]): Promise<BatchResult> {
  return request.post('/sme/cash/v1/bank-statements/batch-review', ids)
}
export function auditStatement(id: number): Promise<BankStatementVO> {
  return request.post(`/sme/cash/v1/bank-statements/${id}/audit`)
}
export function batchAuditStatements(ids: number[]): Promise<BatchResult> {
  return request.post('/sme/cash/v1/bank-statements/batch-audit', ids)
}
export function approveStatement(id: number): Promise<void> {
  return request.post(`/sme/cash/v1/bank-statements/${id}/approve`)
}
export function autoMatchStatements(accountId: string): Promise<MatchSuggestion[]> {
  return request.get('/sme/cash/v1/bank-statements/auto-match', { params: { accountId } })
}
export function confirmStatementMatch(statementId: number, journalId: number): Promise<number> {
  return request.post(`/sme/cash/v1/bank-statements/${statementId}/confirm-match`, null, { params: { journalId } })
}
export function ignoreStatement(statementId: number): Promise<number> {
  return request.post(`/sme/cash/v1/bank-statements/${statementId}/ignore`)
}
export function deleteStatement(id: number): Promise<void> {
  return request.delete(`/sme/cash/v1/bank-statements/${id}`)
}
export function updateStatementClassification(id: number, classification: string): Promise<BankStatementVO> {
  return request.put(`/sme/cash/v1/bank-statements/${id}/classification`, { classification })
}
export function processManualStatement(id: number, targetType: string, paymentType?: string): Promise<BankStatementVO> {
  return request.post(`/sme/cash/v1/bank-statements/${id}/process-manual`, null, {
    params: { targetType, paymentType },
  })
}
export function previewDraftStatement(id: number): Promise<any[]> {
  return request.get(`/sme/cash/v1/bank-statements/${id}/preview-draft`)
}