import request from '@/api/request'

export interface ArapSettlement {
  id: number
  settlementNo: string
  settlementType: string  // RECEIVABLE / PAYABLE
  settlementDate: string
  period: string
  partyId: number
  partyType: string      // CUSTOMER / VENDOR
  totalAmount: number
  discountAmount: number
  voucherId?: number
  status: string         // DRAFT / CONFIRMED / VOUCHERED / REVERSED
  customerName?: string
  vendorName?: string
  remark?: string
  createdBy?: number
  createdAt?: string
  updatedAt?: string
}

export interface ArapSettlementEntry {
  id: number
  settlementId: number
  receivableId?: number
  payableId?: number
  settledAmount: number
  discountAmount: number
}

export interface ReconciliationLog {
  id: number
  tenantId: number
  sourceDocType: string    // receipt / payment / bank_txn
  sourceDocId: number
  targetDocType: string    // INVOICE_OUT / INVOICE_IN
  targetDocId: number
  allocatedAmount: number
  discountAmount: number
  matchScore: number
  matchMethod: string      // AUTO / MANUAL
  status: string           // CONFIRMED / CANCELLED
  remark?: string
  createdBy?: number
  createdAt: string
}

// Settlement APIs
export function pageSettlements(params: {
  settlementType?: string
  status?: string
  current?: number
  size?: number
}): Promise<any> {
  return request.get('/arap-settlements/page', { params })
}

export function getSettlementDetail(id: number): Promise<ArapSettlement> {
  return request.get(`/arap-settlements/${id}`)
}

export function createSettlement(data: {
  settlementType: string
  partyId: number
  totalAmount: number
  remark?: string
}): Promise<ArapSettlement> {
  return request.post('/arap-settlements', data)
}

export function confirmSettlement(id: number): Promise<ArapSettlement> {
  return request.post(`/arap-settlements/${id}/confirm`)
}

export function deleteSettlement(id: number): Promise<void> {
  return request.delete(`/arap-settlements/${id}`)
}

// Reconciliation Log APIs
export function getReconRecords(sourceDocType: string, sourceDocId: number): Promise<ReconciliationLog[]> {
  return request.get('/reconciliation/records', { params: { sourceDocType, sourceDocId } })
}

export function reverseRecon(logId: number, reason?: string): Promise<void> {
  return request.post(`/reconciliation/${logId}/reverse`, null, { params: { reason: reason || '' } })
}

export function pageReconLogs(params: {
  sourceDocType?: string
  current?: number
  size?: number
}): Promise<any> {
  return request.get('/reconciliation/logs/page', { params })
}

// ====== 核销审批 ======

export function approveReconciliation(id: number): Promise<any> {
  return request.post(`/reconciliation/${id}/approve`)
}

export function rejectReconciliation(id: number, reason?: string): Promise<void> {
  return request.post(`/reconciliation/${id}/reject`, null, { params: { reason: reason || '' } })
}

// ====== 核销异常池 ======

export interface ReconciliationException {
  id: number
  tenantId: number
  sourceDocType: string
  sourceDocId: number
  targetDocType?: string
  targetDocId?: number
  partyId?: number
  partyType?: string
  amount: number
  unsettledAmount?: number
  exceptionType: string
  exceptionReason?: string
  matchSuggestion?: string
  status: string  // OPEN / RESOLVED / IGNORED
  retryCount?: number
  assignedTo?: number
  resolvedBy?: number
  resolvedAt?: string
  remark?: string
  createdBy?: number
  createdAt: string
  updatedAt?: string
}

export function pageReconciliationExceptions(params: {
  status?: string
  exceptionType?: string
  current?: number
  size?: number
}): Promise<any> {
  return request.get('/reconciliation/exceptions/page', { params })
}

export function resolveException(id: number, remark?: string): Promise<void> {
  return request.post(`/reconciliation/exceptions/${id}/resolve`, null, { params: { remark: remark || '' } })
}

export function ignoreException(id: number, reason: string): Promise<void> {
  return request.post(`/reconciliation/exceptions/${id}/ignore`, null, { params: { reason } })
}

export function retryException(id: number): Promise<any> {
  return request.post(`/reconciliation/exceptions/${id}/retry`, null, { params: { userId: 0 } })
}
