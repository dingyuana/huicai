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

export function getReconciliationRecommend(statementId: number): Promise<ReconciliationRecommendResult> {
  return request.get(`/bank-statements/${statementId}/reconciliation-recommend`)
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

export function reverseReconciliation(logId: number): Promise<void> {
  return request.post(`/reconciliation/${logId}/reverse`)
}

export function getReconciliationRecords(sourceDocType: string, sourceDocId: number): Promise<any[]> {
  return request.get('/reconciliation/records', { params: { sourceDocType, sourceDocId } })
}
