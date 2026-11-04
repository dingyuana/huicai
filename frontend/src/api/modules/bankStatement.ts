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
  voucher_generated: '已制证',
  payment_created: '待制证',
  manual_pending: '待人工',
  approved: '已过账',
}

/**
 * 查询类端点共用的过滤条件（SPEC-P151）。
 *
 * <p>{@code period}（YYYYMM）与 {@code startDate/endDate} 二选一即可；
 * 后端 period <b>优先于</b> 日期区间。对账单表本身没有期间列（只有 tx_date），
 * 收窄由后端把 period 展开成月首月末完成 —— 前端不要自己算，两端只留一个口径。</p>
 */
export interface BankStatementQueryFilter {
  accountId?: string
  status?: string
  classification?: string
  reviewStatus?: string
  scope?: string
  period?: string
  direction?: string
  counterAccount?: string
  summary?: string
  keyword?: string
  minAmount?: number
  maxAmount?: number
  startDate?: string
  endDate?: string
}

export function getBankStatementPage(params: BankStatementQueryFilter & {
  current?: number
  size?: number
}): Promise<PageResult<BankStatementVO>> {
  return request.get('/sme/cash/v1/bank-statements/page', { params })
}
export function getClassificationCounts(params: {
  accountId: string
  reviewStatus?: string
  scope?: string
  /** YYYYMM；后端展开成月首月末，优先于 startDate/endDate */
  period?: string
  direction?: string
  counterAccount?: string
  summary?: string
  keyword?: string
  minAmount?: number
  maxAmount?: number
  startDate?: string
  endDate?: string
}): Promise<Record<string, number>> {
  return request.get('/sme/cash/v1/bank-statements/classification-counts', { params })
}

export interface BankStatementSummary {
  count: number
  income: number
  expense: number
  net: number
}

export function getBankStatementSummary(params: BankStatementQueryFilter): Promise<BankStatementSummary> {
  return request.get('/sme/cash/v1/bank-statements/summary', { params })
}

export interface BankStatementMonthGroup {
  month: string
  count: number
  totalAmount: number
  items: BankStatementVO[]
}

export function getBankStatementGrouped(params: BankStatementQueryFilter): Promise<BankStatementMonthGroup[]> {
  return request.get('/sme/cash/v1/bank-statements/grouped', { params })
}

/**
 * 分类视图的分组结果（SPEC-P151）。
 * <p>未分类（classification 为空）在后端归到 other_unknown，
 * 且<b>恒排末位</b> —— 它是「还没定性的那堆」，不是另一类业务。</p>
 */
export interface BankStatementCategoryGroup {
  classification: string
  count: number
  income: number
  expense: number
  items: BankStatementVO[]
}

/**
 * 按业务分类分组查询（分类视图）。
 *
 * <p>⚠️ 与 page 的差别：分组视图<b>不接受 classification</b>（分组维度就是它），
 * 且<b>不分页</b> —— 分页会把一组劈成两半，数据量靠期间收窄兜住。</p>
 */
export function getBankStatementGroupedByCategory(params: Omit<BankStatementQueryFilter, 'classification'>):
  Promise<BankStatementCategoryGroup[]> {
  return request.get('/sme/cash/v1/bank-statements/grouped-by-category', { params })
}

export function getStatusCounts(params: {
  accountId: string
  scope?: string
  period?: string
  startDate?: string
  endDate?: string
}): Promise<Record<string, number>> {
  return request.get('/sme/cash/v1/bank-statements/status-counts', { params })
}

export function getPendingSettlementCount(accountId?: string): Promise<number> {
  return request.get('/sme/cash/v1/bank-statements/pending-settlement-count', { params: { accountId } })
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