import request from '@/api/request'

export interface RecommendItem {
  targetDocId: number
  targetDocNo: string
  targetDocType: string
  originalAmount: number
  unsettledAmount: number
  matchScore: number
  matchLevel: string
  suggestedAmount: number
}

export interface RecommendResult {
  sourceDocType: string
  sourceDocId: number
  counterpartyName: string
  sourceAmount: number
  items: RecommendItem[]
}

export interface ReconciliationRecommendResult {
  message: string
  items: RecommendItem[]
}

export function executeReconciliation(data: {
  sourceDocType: string
  sourceDocId: number
  targetDocType: string
  targetDocId: number
  amount: number
  matchScore: number
  matchMethod: string
  customerId?: number
  vendorId?: number
  period?: string
  remark?: string
}): Promise<any> {
  return request.post('/sme/arap/v1/reconciliation/execute', data)
}

export function reverseReconciliation(logId: number, reason?: string): Promise<void> {
  return request.post(`/sme/arap/v1/reconciliation/${logId}/reverse`, null, { params: { reason: reason || '' } })
}

export function getReconciliationRecords(sourceDocType: string, sourceDocId: number): Promise<any[]> {
  return request.get('/sme/arap/v1/reconciliation/records', { params: { sourceDocType, sourceDocId } })
}

export interface PreCheckItem {
  checkName: string
  passed: boolean
  message: string
}

export interface PreCheckResult {
  allPassed: boolean
  checks: PreCheckItem[]
}

/** 全链路追溯 VO */
export interface ReconciliationTraceVO {
  traceId: string
  settlement: {
    id: number
    settlementNo: string
    amount: number
    status: string
    createdAt: string
  } | null
  upstream: {
    bankTransaction: { id: number; transactionNo: string; amount: number; counterAccount: string } | null
    receipt: { id: number; docNo: string; amount: number; status: string } | null
  } | null
  downstream: {
    businessDocs: Array<{ id: number; docNo: string; docType: string; amount: number; settledAmount: number; unsettledAmount: number }>
    invoices: Array<{ id: number; invoiceNo: string; amount: number; status: string; invoiceType: string }>
  } | null
  operationTrail: Array<{ operationType: string; operator: string; time: string; remark: string }>
  voucher: { id: number; voucherNo: string; status: string } | null
}

/** FIFO 自动核销预览项（dry-run，不落库） */
export interface ReconciliationFifoPreview {
  sourceDocId: number
  sourceDocNo: string
  sourceDocType: string
  sourceAmount: number
  sourceUnsettledAmount: number
  targetDocId: number
  targetDocNo: string
  targetDocType: string
  targetAmount: number
  targetUnsettledAmount: number
  amount: number
  matchScore?: number
  matchLevel?: string
  partyId?: number
  partyName?: string
}

export function preCheckReconciliation(data: {
  sourceDocType: string
  sourceDocId: number
  targetDocType: string
  targetDocId: number
  amount: number
  customerId?: number
  vendorId?: number
  period?: string
}): Promise<PreCheckResult> {
  return request.post('/sme/arap/v1/reconciliation/pre-check', data)
}

/** 核销单全链路追溯 */
export function getReconciliationTrace(id: number): Promise<ReconciliationTraceVO> {
  return request.get(`/sme/arap/v1/reconciliation/${id}/trace`)
}

/** FIFO 自动核销匹配（dry-run 预览，不落库） */
export function autoFifoReconciliation(params: {
  partyId: number
  targetDocType: string
  amount: number
  sourceDocType: string
  sourceDocId: number
  period?: string
  summary?: string
}): Promise<ReconciliationFifoPreview[]> {
  return request.post('/sme/arap/v1/reconciliation/auto-fifo', params)
}

/** 批量执行核销（人工确认预览后调用，一次性落库） */
export function batchExecuteReconciliation(requests: Array<{
  sourceDocType: string
  sourceDocId: number
  targetDocType: string
  targetDocId: number
  amount: number
  matchScore: number
  matchMethod: string
  customerId?: number
  vendorId?: number
  period?: string
  remark?: string
}>): Promise<any[]> {
  return request.post('/sme/arap/v1/reconciliation/batch-execute', requests)
}

/** 编号全链路追溯（按单据号/凭证号） */
export function getNumberingTrace(docNo: string): Promise<any> {
  return request.get(`/base/voucher/v1/vouchers/trace?no=${docNo}`)
}

export function getReceiptRecommend(
  receiptId: number,
  params: { sourceDocType: string; customerId: number; amount: number; summary?: string; counterpartyName?: string }
): Promise<ReconciliationRecommendResult> {
  return request.post(`/sme/arap/v1/reconciliation/receipt/${receiptId}/recommend`, null, { params })
}

export function getPaymentRecommend(
  paymentId: number,
  params: { sourceDocType: string; vendorId: number; amount: number; summary?: string; counterpartyName?: string }
): Promise<ReconciliationRecommendResult> {
  return request.post(`/sme/arap/v1/reconciliation/payment/${paymentId}/recommend`, null, { params })
}
