import request from '@/api/request'

export interface Customer {
  id?: number
  code: string
  name: string
  contactPerson?: string
  phone?: string
  email?: string
  address?: string
  taxNo?: string
  bankName?: string
  bankAccount?: string
  creditLimit?: number
  creditDays?: number
  subjectId?: number
  isActive?: boolean
  remark?: string
}

export interface Vendor {
  id?: number
  code: string
  name: string
  contactPerson?: string
  phone?: string
  email?: string
  address?: string
  taxNo?: string
  bankName?: string
  bankAccount?: string
  creditLimit?: number
  creditDays?: number
  subjectId?: number
  isActive?: boolean
  remark?: string
}

export function pageCustomer(params: any): Promise<any> {
  return request.get('/customers/page', { params })
}

export function listCustomer(): Promise<Customer[]> {
  return request.get('/customers/list')
}

export function createCustomer(data: Customer): Promise<Customer> {
  return request.post('/customers', data)
}

export function updateCustomer(id: number, data: Customer): Promise<Customer> {
  return request.put(`/customers/${id}`, data)
}

export function deleteCustomer(id: number): Promise<void> {
  return request.delete(`/customers/${id}`)
}

export function pageVendor(params: any): Promise<any> {
  return request.get('/vendors/page', { params })
}

export function listVendor(): Promise<Vendor[]> {
  return request.get('/vendors/list')
}

export function createVendor(data: Vendor): Promise<Vendor> {
  return request.post('/vendors', data)
}

export function updateVendor(id: number, data: Vendor): Promise<Vendor> {
  return request.put(`/vendors/${id}`, data)
}

export function deleteVendor(id: number): Promise<void> {
  return request.delete(`/vendors/${id}`)
}

export function pageReceivable(params: any): Promise<any> {
  return request.get('/receivables/page', { params })
}

export function getReceivable(id: number): Promise<any> {
  return request.get(`/receivables/${id}`)
}

export function createReceivable(data: any): Promise<any> {
  return request.post('/receivables', data)
}

export function confirmReceivable(id: number): Promise<any> {
  return request.post(`/receivables/${id}/confirm`)
}

export function reverseReceivable(id: number): Promise<any> {
  return request.post(`/receivables/${id}/reverse`)
}

export function overdueReceivables(): Promise<any> {
  return request.get('/receivables/overdue')
}

export function getCustomer(id: number): Promise<any> {
  return request.get(`/customers/${id}`)
}

export function customerUnsettledSummary(): Promise<any> {
  return request.get('/customers/unsettled-summary')
}

export function pagePayable(params: any): Promise<any> {
  return request.get('/payables/page', { params })
}

export function getPayable(id: number): Promise<any> {
  return request.get(`/payables/${id}`)
}

export function createPayable(data: any): Promise<any> {
  return request.post('/payables', data)
}

export function confirmPayable(id: number): Promise<any> {
  return request.post(`/payables/${id}/confirm`)
}

export function reversePayable(id: number): Promise<any> {
  return request.post(`/payables/${id}/reverse`)
}

export function getVendor(id: number): Promise<any> {
  return request.get(`/vendors/${id}`)
}

export function vendorUnsettledSummary(): Promise<any> {
  return request.get('/vendors/unsettled-summary')
}

export function receivableAging(customerId?: number): Promise<any> {
  return request.get('/receivables/aging', { params: { customerId } })
}

export function payableAging(vendorId: number): Promise<any> {
  return request.get('/payables/aging', { params: { vendorId } })
}

export function pageBadDebt(params: any): Promise<any> {
  return request.get('/bad-debts/page', { params })
}

export function getBadDebt(id: number): Promise<any> {
  return request.get(`/bad-debts/${id}`)
}

export function provisionBadDebtPercentage(data: any): Promise<any> {
  return request.post('/bad-debts/provision/percentage', data)
}

export function confirmBadDebt(id: number): Promise<any> {
  return request.post(`/bad-debts/${id}/confirm`)
}

export function deleteBadDebt(id: number): Promise<void> {
  return request.delete(`/bad-debts/${id}`)
}

export function provisionBadDebtAging(period: string, ratios: Record<string, number>): Promise<any> {
  return request.post('/bad-debts/provision/aging', ratios, { params: { period } })
}

export function clearReceivables(): Promise<any> {
  return request.post('/system/clear-receivables')
}

export function clearPayables(): Promise<any> {
  return request.post('/system/clear-payables')
}

// ===== 账龄分析 (P51) =====
export function getAgingSummary(params: { period: string; customerId?: number }): Promise<any> {
  return request.get('/aging-analysis/summary', { params })
}
export function getAgingByCustomer(period: string): Promise<any> {
  return request.get('/aging-analysis/by-customer', { params: { period } })
}
export function getDueReceivables(params: { date: string; customerId?: number }): Promise<any> {
  return request.get('/aging-analysis/due-receivables', { params })
}
export function getAlerts(params: { alertLevel?: string; status?: string; customerId?: number }): Promise<any> {
  return request.get('/aging-analysis/alerts', { params })
}
export function generateAlerts(period: string): Promise<any> {
  return request.post('/aging-analysis/alerts/generate', null, { params: { period } })
}
export function dismissAlert(id: number): Promise<void> {
  return request.post(`/aging-analysis/alerts/${id}/dismiss`)
}
export function resolveAlert(id: number): Promise<void> {
  return request.post(`/aging-analysis/alerts/${id}/resolve`)
}
