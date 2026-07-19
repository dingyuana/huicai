import request from '@/api/request'

export interface Prepayment {
  id?: number
  prepayNo?: string
  vendorId?: number
  vendorName?: string
  customerId?: number
  customerName?: string
  amount: number
  appliedAmount?: number
  period?: string
  txDate?: string
  summary?: string
  status?: string
  sourceDocType?: string
  sourceDocId?: number
  createdBy?: string
  createdAt?: string
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
