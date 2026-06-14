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

export function reverseRecon(logId: number): Promise<void> {
  return request.post(`/reconciliation/${logId}/reverse`)
}

export function pageReconLogs(params: {
  sourceDocType?: string
  current?: number
  size?: number
}): Promise<any> {
  return request.get('/reconciliation/logs/page', { params })
}
