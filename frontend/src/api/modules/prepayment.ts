import request from '@/api/request'

export interface Prepayment {
  id?: number
  /** 单据编号：来自 doc_id → t_business_doc.doc_no；手工建单的预付款为 null（无 doc_id） */
  prepayNo?: string
  vendorId?: number
  vendorName?: string
  customerId?: number
  customerName?: string
  amount: number
  /** 已核销金额。此前 interface 写的是 appliedAmount，但 t_prepayment 没有该列，
   *  真实列名是 settled_amount ⇒ 该字段长期读到 undefined，显示恒为 0。 */
  settledAmount?: number
  /** 未核销金额（页面原本用 amount - appliedAmount 现算，等价于本列） */
  unsettledAmount?: number
  period?: string
  txDate?: string
  summary?: string
  status?: string
  sourceDocType?: string
  sourceDocId?: number
}

export function pagePrepayment(params: any): Promise<any> {
  return request.get('/sme/arap/v1/prepayment/page', { params })
}

export function getPrepayment(id: number): Promise<Prepayment> {
  return request.get(`/sme/arap/v1/prepayment/${id}`)
}

export function createPrepayment(data: Partial<Prepayment>): Promise<Prepayment> {
  return request.post('/sme/arap/v1/prepayment', data)
}

export function confirmPrepayment(id: number): Promise<Prepayment> {
  return request.post(`/sme/arap/v1/prepayment/${id}/confirm`)
}

export function applyToPayable(prepayId: number, payableId: number, params?: any): Promise<any> {
  return request.post(`/sme/arap/v1/prepayment/${prepayId}/apply-to-payable/${payableId}`, null, { params })
}

export function applyToReceivable(prepayId: number, receivableId: number, params?: any): Promise<any> {
  return request.post(`/sme/arap/v1/prepayment/${prepayId}/apply-to-receivable/${receivableId}`, null, { params })
}

export function reversePrepayment(id: number, params: any): Promise<any> {
  return request.post(`/sme/arap/v1/prepayment/${id}/reverse`, null, { params })
}

export function getOpenPrepayments(vendorId: number): Promise<Prepayment[]> {
  return request.get(`/sme/arap/v1/prepayment/open/${vendorId}`)
}

export function getOpenPrepaymentsForCustomer(customerId: number): Promise<Prepayment[]> {
  return request.get(`/sme/arap/v1/prepayment/open-customer/${customerId}`)
}

// ===== 预收预付余额汇总 (P78) =====
export interface PrepaymentBalancePartyVO {
  partyId: number
  partyName: string | null
  openingUnsettled: number
  currentCreated: number
  currentApplied: number
  currentReversed: number
  closingUnsettled: number
}

export interface PrepaymentBalanceSummaryVO {
  period: string
  partyType: string | null
  consistent: boolean
  preReceiptTotal: number
  prePaymentTotal: number
  preReceipts: PrepaymentBalancePartyVO[]
  prePayments: PrepaymentBalancePartyVO[]
}

export function getPrepaymentBalanceSummary(
  params: { period: string; partyType?: string; partyId?: number }
): Promise<PrepaymentBalanceSummaryVO> {
  return request.get('/sme/arap/v1/prepayment/balance-summary', { params })
}
