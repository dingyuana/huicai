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

export function pagePayable(params: any): Promise<any> {
  return request.get('/payables/page', { params })
}

export function receivableAging(customerId?: number): Promise<any> {
  return request.get('/receivables/aging', { params: { customerId } })
}

export function payableAging(vendorId: number): Promise<any> {
  return request.get('/payables/aging', { params: { vendorId } })
}

export function provisionBadDebtAging(period: string, ratios: Record<string, number>): Promise<any> {
  return request.post('/bad-debts/provision/aging', ratios, { params: { period } })
}
