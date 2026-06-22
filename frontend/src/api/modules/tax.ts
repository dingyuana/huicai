import request from '@/api/request'

export interface TaxType {
  id?: number
  code: string
  name: string
  taxCategory: string
  rate: number
  isActive?: boolean
  remark?: string
}

export interface InputInvoice {
  id?: number
  invoiceNo: string
  invoiceDate: string
  period?: string
  vendorId?: number
  vendorName?: string
  amount: number
  taxRate: number
  taxAmount?: number
  totalAmount?: number
  invoiceType: string
  certificationStatus?: string
  deductionPeriod?: string
  deductionAmount?: number
  remark?: string
}

export interface OutputInvoice {
  id?: number
  invoiceNo: string
  invoiceDate: string
  period?: string
  customerId?: number
  customerName?: string
  amount: number
  taxRate: number
  taxAmount?: number
  totalAmount?: number
  invoiceType: string
  status?: string
  remark?: string
}

export function pageTaxType(params: any): Promise<any> {
  return request.get('/tax/types/page', { params })
}

export function listTaxType(): Promise<TaxType[]> {
  return request.get('/tax/types/list')
}

export function createTaxType(data: TaxType): Promise<TaxType> {
  return request.post('/tax/types', data)
}

export function deleteTaxType(id: number): Promise<void> {
  return request.delete(`/tax/types/${id}`)
}

export function pageInputInvoice(params: any): Promise<any> {
  return request.get('/tax/input-invoices/page', { params })
}

export function createInputInvoice(data: InputInvoice): Promise<InputInvoice> {
  return request.post('/tax/input-invoices', data)
}

export function certifyInputInvoice(id: number, deductionPeriod?: string): Promise<InputInvoice> {
  return request.post(`/tax/input-invoices/${id}/certify`, null, { params: { deductionPeriod } })
}

export function inputInvoiceSummary(period: string): Promise<any> {
  return request.get('/tax/input-invoices/summary', { params: { period } })
}

export function pageOutputInvoice(params: any): Promise<any> {
  return request.get('/tax/output-invoices/page', { params })
}

export function getOutputInvoice(id: number): Promise<OutputInvoice> {
  return request.get(`/tax/output-invoices/${id}`)
}

export function deleteOutputInvoice(id: number): Promise<void> {
  return request.delete(`/tax/output-invoices/${id}`)
}

// ====== 销项发票状态机 (P21-a) ======
export function submitForReview(id: number): Promise<void> {
  return request.post(`/tax/output-invoices/${id}/submit-review`)
}

export function confirmOutputInvoice(id: number): Promise<void> {
  return request.post(`/tax/output-invoices/${id}/confirm`)
}

export function rejectOutputInvoice(id: number, reason: string): Promise<void> {
  return request.post(`/tax/output-invoices/${id}/reject`, null, { params: { reason } })
}

export function revertOutputInvoice(id: number): Promise<void> {
  return request.post(`/tax/output-invoices/${id}/revert`)
}

export function voidOutputInvoice(id: number, reason: string): Promise<void> {
  return request.post(`/tax/output-invoices/${id}/void`, null, { params: { reason } })
}

export function createOutputInvoice(data: OutputInvoice): Promise<OutputInvoice> {
  return request.post('/tax/output-invoices', data)
}

export function outputInvoiceSummary(period?: string): Promise<any> {
  const params: any = {}
  if (period) params.period = period
  return request.get('/tax/output-invoices/summary', { params })
}

export function calculateVat(period: string): Promise<any> {
  return request.get('/tax/vat/calculate', { params: { period } })
}
