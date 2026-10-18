import request from '@/api/request'

export interface ArapSettlement {
  id: number
  settlementNo: string
  settlementType: string  // RECEIVE / PAY（chk_settlement_type，原注释写的 RECEIVABLE/PAYABLE 是错的）
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
  sourceDocType?: string  // 铁律 #9 溯源
  sourceDocId?: number
  remark?: string        // V175 补列：此前前端在用而后端无此列，备注提交后无处落库、详情恒显 '-'
  // ⚠️ `createdBy / createdAt / updatedAt` 已删除：它们是审计列，
  // 且 grep SettlementPanel.vue 的**引用行数全为 0**（页面一个都不读）
  // ⇒ 既非合法展示字段，也不该出现在契约里。依据 AGENTS §4.5 第 38 条：
  // **字段的敏感性不能按列名归类，必须对着前端 interface 的实际声明核对。**
}

/**
 * 核销单明细 —— 字段集 = `t_arap_settlement_entry` 的真实列，由 `ArapSettlementEntryVO` 逐字段对应。
 *
 * ⚠️ 原声明的 `receivableId / payableId` 已删除：二者是 P34 起废弃的字段
 * （Entity 上标了 `@TableField(exist = false)`，DB 无此列），改用 `businessDocId`。
 * ⚠️ `SettlementPanel.vue` 的「核销依据」表格渲染 `sourceDocNo / targetDocNo /
 * beforeBalance / afterBalance` 四列，后端**均无对应数据**（详见 ArapSettlementEntryVO 注释）
 * ⇒ 属功能未实现，本接口不声明，不用兜底值伪装成 0（AGENTS §4.5 第 39 条）。
 */
export interface ArapSettlementEntry {
  id: number
  settlementId: number
  businessDocId?: number
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
  return request.get('/sme/arap/v1/arap-settlements/page', { params })
}

export function getSettlementDetail(id: number): Promise<ArapSettlement> {
  return request.get(`/sme/arap/v1/arap-settlements/${id}`)
}

export function getSettlementEntries(id: number): Promise<ArapSettlementEntry[]> {
  return request.get(`/sme/arap/v1/arap-settlements/${id}/entries`)
}

export function createSettlement(data: {
  settlementType: string
  partyId: number
  totalAmount: number
  remark?: string
}): Promise<ArapSettlement> {
  return request.post('/sme/arap/v1/arap-settlements', data)
}

export function confirmSettlement(id: number): Promise<ArapSettlement> {
  return request.post(`/sme/arap/v1/arap-settlements/${id}/confirm`)
}

export function deleteSettlement(id: number): Promise<void> {
  return request.delete(`/sme/arap/v1/arap-settlements/${id}`)
}

export function generateSettlementVoucher(id: number): Promise<ArapSettlement> {
  return request.post(`/sme/arap/v1/arap-settlements/${id}/generate-voucher`)
}

// Reconciliation Log APIs
export function getReconRecords(sourceDocType: string, sourceDocId: number): Promise<ReconciliationLog[]> {
  return request.get('/sme/arap/v1/reconciliation/records', { params: { sourceDocType, sourceDocId } })
}

export function reverseRecon(logId: number, reason?: string): Promise<void> {
  return request.post(`/sme/arap/v1/reconciliation/${logId}/reverse`, null, { params: { reason: reason || '' } })
}

export function pageReconLogs(params: {
  sourceDocType?: string
  current?: number
  size?: number
}): Promise<any> {
  return request.get('/sme/arap/v1/reconciliation/logs/page', { params })
}

// ====== 核销审批 ======

export function approveReconciliation(id: number): Promise<any> {
  return request.post(`/sme/arap/v1/reconciliation/${id}/approve`)
}

export function rejectReconciliation(id: number, reason?: string): Promise<void> {
  return request.post(`/sme/arap/v1/reconciliation/${id}/reject`, null, { params: { reason: reason || '' } })
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
  return request.get('/sme/arap/v1/reconciliation/exceptions/page', { params })
}

export function resolveException(id: number, remark?: string): Promise<void> {
  return request.post(`/sme/arap/v1/reconciliation/exceptions/${id}/resolve`, null, { params: { remark: remark || '' } })
}

export function ignoreException(id: number, reason: string): Promise<void> {
  return request.post(`/sme/arap/v1/reconciliation/exceptions/${id}/ignore`, null, { params: { reason } })
}

export function retryException(id: number): Promise<any> {
  return request.post(`/sme/arap/v1/reconciliation/exceptions/${id}/retry`, null, { params: { userId: 0 } })
}
