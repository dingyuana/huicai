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
  return request.post('/reconciliation/execute', data)
}

export function reverseReconciliation(logId: number, reason?: string): Promise<void> {
  return request.post(`/reconciliation/${logId}/reverse`, null, { params: { reason: reason || '' } })
}

export function getReconciliationRecords(sourceDocType: string, sourceDocId: number): Promise<any[]> {
  return request.get('/reconciliation/records', { params: { sourceDocType, sourceDocId } })
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
    invoices: Array<{ id: number; invoiceNo: string; amount: number; status: string }>
  } | null
  operationTrail: Array<{ operationType: string; operator: string; time: string; remark: string }>
  voucher: { id: number; voucherNo: string; status: string } | null
}

/** FIFO 自动核销结果 */
export interface AutoFifoResult {
  totalAmount: number
  allocatedAmount: number
  remainingAmount: number
  allocations: Array<{
    sourceDocId: number
    sourceDocNo: string
    targetDocId: number
    targetDocNo: string
    amount: number
  }>
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
  return request.post('/reconciliation/pre-check', data)
}

/** 核销单全链路追溯 */
export function getReconciliationTrace(id: number): Promise<ReconciliationTraceVO> {
  return request.get(`/reconciliation/${id}/trace`)
}

/** FIFO 自动核销匹配（仅匹配，不执行） */
export function autoFifoReconciliation(params: {
  partyId: number
  targetDocType: string
  amount: number
  sourceDocType: string
  sourceDocId: number
  period?: string
  summary?: string
}): Promise<any> {
  return request.post('/reconciliation/auto-fifo', null, { params })
}

/** 编号全链路追溯（按单据号/凭证号） */
export function getNumberingTrace(docNo: string): Promise<any> {
  return request.get(`/trace/by-doc-no?docNo=${docNo}`)
}

export function getReceiptRecommend(
  receiptId: number,
  params: { sourceDocType: string; customerId: number; amount: number; summary?: string; counterpartyName?: string }
): Promise<ReconciliationRecommendResult> {
  return request.post(`/reconciliation/receipt/${receiptId}/recommend`, null, { params })
}

export function getPaymentRecommend(
  paymentId: number,
  params: { sourceDocType: string; vendorId: number; amount: number; summary?: string; counterpartyName?: string }
): Promise<ReconciliationRecommendResult> {
  return request.post(`/reconciliation/payment/${paymentId}/recommend`, null, { params })
}
