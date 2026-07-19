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
  return request.get('/v1/customers/page', { params })
}

export function listCustomer(): Promise<Customer[]> {
  return request.get('/v1/customers/list')
}

export function createCustomer(data: Customer): Promise<Customer> {
  return request.post('/v1/customers', data)
}

export function updateCustomer(id: number, data: Customer): Promise<Customer> {
  return request.put(`/v1/customers/${id}`, data)
}

export function deleteCustomer(id: number): Promise<void> {
  return request.delete(`/v1/customers/${id}`)
}

export function pageVendor(params: any): Promise<any> {
  return request.get('/v1/vendors/page', { params })
}

export function listVendor(): Promise<Vendor[]> {
  return request.get('/v1/vendors/list')
}

export function createVendor(data: Vendor): Promise<Vendor> {
  return request.post('/v1/vendors', data)
}

export function updateVendor(id: number, data: Vendor): Promise<Vendor> {
  return request.put(`/v1/vendors/${id}`, data)
}

export function deleteVendor(id: number): Promise<void> {
  return request.delete(`/v1/vendors/${id}`)
}

export function pageReceivable(params: any): Promise<any> {
  return request.get('/sme/arap/v1/receivables/page', { params })
}

export function getReceivable(id: number): Promise<any> {
  return request.get(`/sme/arap/v1/receivables/${id}`)
}

export function createReceivable(data: any): Promise<any> {
  return request.post('/sme/arap/v1/receivables', data)
}

export function confirmReceivable(id: number): Promise<any> {
  return request.post(`/sme/arap/v1/receivables/${id}/confirm`)
}

export function reverseReceivable(id: number): Promise<any> {
  return request.post(`/sme/arap/v1/receivables/${id}/reverse`)
}

export function overdueReceivables(): Promise<any> {
  return request.get('/sme/arap/v1/receivables/overdue')
}

export function getCustomer(id: number): Promise<any> {
  return request.get(`/v1/customers/${id}`)
}

export function customerUnsettledSummary(): Promise<any> {
  return request.get('/v1/customers/unsettled-summary')
}

export function pagePayable(params: any): Promise<any> {
  return request.get('/sme/arap/v1/payables/page', { params })
}

export function getPayable(id: number): Promise<any> {
  return request.get(`/sme/arap/v1/payables/${id}`)
}

export function createPayable(data: any): Promise<any> {
  return request.post('/sme/arap/v1/payables', data)
}

export function confirmPayable(id: number): Promise<any> {
  return request.post(`/sme/arap/v1/payables/${id}/confirm`)
}

export function reversePayable(id: number): Promise<any> {
  return request.post(`/sme/arap/v1/payables/${id}/reverse`)
}

export function getVendor(id: number): Promise<any> {
  return request.get(`/v1/vendors/${id}`)
}

export function vendorUnsettledSummary(): Promise<any> {
  return request.get('/v1/vendors/unsettled-summary')
}

export function receivableAging(customerId?: number): Promise<any> {
  return request.get('/sme/arap/v1/receivables/aging', { params: { customerId } })
}

export function payableAging(vendorId: number): Promise<any> {
  return request.get('/sme/arap/v1/payables/aging', { params: { vendorId } })
}

export function pageBadDebt(params: any): Promise<any> {
  return request.get('/sme/arap/v1/bad-debts/page', { params })
}

export function getBadDebt(id: number): Promise<any> {
  return request.get(`/sme/arap/v1/bad-debts/${id}`)
}

export function provisionBadDebtPercentage(data: any): Promise<any> {
  return request.post('/sme/arap/v1/bad-debts/provision/percentage', data)
}

export function confirmBadDebt(id: number): Promise<any> {
  return request.post(`/sme/arap/v1/bad-debts/${id}/confirm`)
}

export function deleteBadDebt(id: number): Promise<void> {
  return request.delete(`/sme/arap/v1/bad-debts/${id}`)
}

export function provisionBadDebtAging(period: string, ratios: Record<string, number>): Promise<any> {
  return request.post('/sme/arap/v1/bad-debts/provision/aging', ratios, { params: { period } })
}

export function clearReceivables(): Promise<any> {
  return request.post('/v1/system/clear-receivables')
}

export function clearPayables(): Promise<any> {
  return request.post('/v1/system/clear-payables')
}

// ===== 账龄分析 (P51) =====
export function getAgingSummary(params: { period: string; customerId?: number }): Promise<any> {
  return request.get('/sme/arap/v1/aging-analysis/summary', { params })
}
export function getAgingByCustomer(period: string): Promise<any> {
  return request.get('/sme/arap/v1/aging-analysis/by-customer', { params: { period } })
}
export function getDueReceivables(params: { date: string; customerId?: number }): Promise<any> {
  return request.get('/sme/arap/v1/aging-analysis/due-receivables', { params })
}
export function getAlerts(params: { alertLevel?: string; status?: string; customerId?: number }): Promise<any> {
  return request.get('/sme/arap/v1/aging-analysis/alerts', { params })
}
export function generateAlerts(period: string): Promise<any> {
  return request.post('/sme/arap/v1/aging-analysis/alerts/generate', null, { params: { period } })
}
export function dismissAlert(id: number): Promise<void> {
  return request.post(`/sme/arap/v1/aging-analysis/alerts/${id}/dismiss`)
}
export function resolveAlert(id: number): Promise<void> {
  return request.post(`/sme/arap/v1/aging-analysis/alerts/${id}/resolve`)
}

// ===== 坏账准备 P43 扩展 =====
export function getBadDebtScheme(): Promise<any> {
  return request.get('/sme/arap/v1/bad-debts/scheme')
}
export function updateBadDebtScheme(data: Record<string, number>): Promise<void> {
  return request.put('/sme/arap/v1/bad-debts/scheme', data)
}
export function writeOffBadDebt(data: { sourceType: string; sourceId: number; writeOffAmount: number; reason: string }): Promise<any> {
  return request.post('/sme/arap/v1/bad-debts/write-off', data)
}
export function recoveryBadDebt(data: { sourceId: number; amount: number }): Promise<any> {
  return request.post('/sme/arap/v1/bad-debts/recovery', data)
}

// ===== 客户对账 (P52) =====
export function generateStatements(data: { customerIds: number[]; period: string }): Promise<any> {
  return request.post('/sme/arap/v1/customer-statements/generate', data)
}
export function getStatement(id: number): Promise<any> {
  return request.get(`/sme/arap/v1/customer-statements/${id}`)
}
export function pageStatements(params: any): Promise<any> {
  return request.get('/sme/arap/v1/customer-statements/page', { params })
}
export function sendStatement(id: number): Promise<void> {
  return request.post(`/sme/arap/v1/customer-statements/${id}/send`)
}
export function confirmStatement(id: number): Promise<void> {
  return request.post(`/sme/arap/v1/customer-statements/${id}/confirm`)
}
export function disputeStatement(id: number, data: any): Promise<void> {
  return request.post(`/sme/arap/v1/customer-statements/${id}/dispute`, data)
}
export function pageOutstandingItems(params: any): Promise<any> {
  return request.get('/sme/arap/v1/outstanding-items/page', { params })
}
export function resolveOutstandingItem(id: number): Promise<void> {
  return request.post(`/sme/arap/v1/outstanding-items/${id}/resolve`)
}
export function cancelOutstandingItem(id: number): Promise<void> {
  return request.post(`/sme/arap/v1/outstanding-items/${id}/cancel`)
}
export function pageDisputes(params: any): Promise<any> {
  return request.get('/sme/arap/v1/disputes/page', { params })
}
export function resolveDispute(id: number): Promise<void> {
  return request.post(`/sme/arap/v1/disputes/${id}/resolve`)
}

// ===== 付款计划 (P53 M2) =====
export function generatePaymentPlan(params: { period?: string; vendorId?: number }): Promise<any> {
  return request.get('/sme/arap/v1/payment-plans', { params })
}

// ===== 采购退货 (P53 M3) =====
export function createPurchaseReturn(data: { originalDocNo: string; vendorId: number; returnAmount: number; taxAmount?: number; reason?: string }): Promise<any> {
  return request.post('/sme/arap/v1/purchase-returns', data)
}
export function getPurchaseReturn(id: number): Promise<any> {
  return request.get(`/sme/arap/v1/purchase-returns/${id}`)
}

// ===== 可用预付款查询 (P53 M4) =====
export function getAvailablePrepayment(params: { vendorId: number; amount?: number }): Promise<any> {
  return request.get('/sme/arap/v1/prepayment/available', { params })
}
