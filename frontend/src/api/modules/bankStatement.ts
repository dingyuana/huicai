import request from '@/api/request'
import type { PageResult } from '@/types/api'

export interface BankStatementVO {
  id: number
  accountId: number
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
  reviewStatus?: string
  reviewedBy?: number
  reviewedAt?: string
  generatedDocId?: number
  generatedVoucherId?: number
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
  bank_fee: '银行手续费',
  interest_income: '利息收入',
  business_receipt: '业务收款',
  business_payment: '业务付款',
  internal_transfer: '内部转账',
  tax_payment: '税费扣缴',
  social_security: '社保缴费',
  insurance_fee: '保险费用',
  salary_payment: '工资发放',
  pending: '待认领',
}

export const REVIEW_STATUS_LABELS: Record<string, string> = {
  PENDING: '待确认',
  CONFIRMED: '已确认',
  RECLASSIFIED: '已重分类',
}

export function getBankStatementPage(params: { accountId?: number; status?: string; current?: number; size?: number }): Promise<PageResult<BankStatementVO>> {
  return request.get('/bank-statements/page', { params })
}
export function getClassificationCounts(accountId: number, reviewStatus?: string): Promise<Record<string, number>> {
  return request.get('/bank-statements/classification-counts', { params: { accountId, reviewStatus } })
}
export function getBankStatementDetail(id: number): Promise<BankStatementVO> {
  return request.get(`/bank-statements/${id}`)
}
export function importStatementCsv(accountId: number, csvContent: string): Promise<number> {
  return request.post('/bank-statements/import-csv', csvContent, { params: { accountId }, headers: { 'Content-Type': 'text/plain' } })
}
export function importStatementExcel(accountId: number, file: File): Promise<{ total: number; success: number; classified: number; errors: Array<{ row: number; message: string }>; batchId: string }> {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/bank-statements/import-excel', formData, { params: { accountId }, headers: { 'Content-Type': 'multipart/form-data' } })
}
export function previewStatementExcel(accountId: number, file: File): Promise<{
  total: number; valid: number; errors: any[]; batchId: string; previews: any[]; headers?: string[]
}> {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/bank-statements/preview-excel', formData, { params: { accountId }, headers: { 'Content-Type': 'multipart/form-data' } })
}

/** 带自定义列映射的 Excel 预览 */
export function previewStatementExcelWithMapping(accountId: number, file: File, columnMapping: Record<string, string>): Promise<{
  total: number; valid: number; errors: any[]; batchId: string; previews: any[]; headers?: string[]
}> {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('columnMappingJson', JSON.stringify(columnMapping))
  return request.post('/bank-statements/preview-excel', formData, { params: { accountId }, headers: { 'Content-Type': 'multipart/form-data' } })
}

/** 解析 Excel 表头 */
export function parseExcelHeaders(file: File): Promise<{
  headers: string[]; fields: Array<{ field: string; label: string; required: boolean }>
}> {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/bank-statements/parse-headers', formData, { headers: { 'Content-Type': 'multipart/form-data' } })
}
export function confirmStatementImport(batchId: string): Promise<{ total: number; success: number; classified: number; batchId: string }> {
  return request.post('/bank-statements/confirm-import', null, { params: { batchId } })
}
export function classifyStatement(id: number): Promise<BankStatementVO> {
  return request.post(`/bank-statements/${id}/classify`)
}
export function reviewStatement(id: number): Promise<BankStatementVO> {
  return request.post(`/bank-statements/${id}/review`)
}
export function batchReviewStatements(ids: number[]): Promise<number> {
  return request.post('/bank-statements/batch-review', ids)
}
export function batchConfirmStatements(ids: number[]): Promise<{ confirmed: number; vouchers_created: number; docs_created: number; failed: number }> {
  return request.post('/bank-statements/batch-confirm', ids)
}
export function autoMatchStatements(accountId: number): Promise<MatchSuggestion[]> {
  return request.get('/bank-statements/auto-match', { params: { accountId } })
}
export function confirmStatementMatch(statementId: number, journalId: number): Promise<number> {
  return request.post(`/bank-statements/${statementId}/confirm-match`, null, { params: { journalId } })
}
export function ignoreStatement(statementId: number): Promise<number> {
  return request.post(`/bank-statements/${statementId}/ignore`)
}
export function deleteStatement(id: number): Promise<void> {
  return request.delete(`/bank-statements/${id}`)
}
export function updateStatementClassification(id: number, classification: string): Promise<BankStatementVO> {
  return request.put(`/bank-statements/${id}/classification`, { classification })
}