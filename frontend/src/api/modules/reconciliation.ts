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

export function getReceiptRecommend(
  receiptId: number,
  params: { customerId: number; amount: number; summary?: string; counterpartyName?: string }
): Promise<ReconciliationRecommendResult> {
  return request.post(`/reconciliation/receipt/${receiptId}/recommend`, null, { params })
}

export function getPaymentRecommend(
  paymentId: number,
  params: { vendorId: number; amount: number; summary?: string; counterpartyName?: string }
): Promise<ReconciliationRecommendResult> {
  return request.post(`/reconciliation/payment/${paymentId}/recommend`, null, { params })
}
