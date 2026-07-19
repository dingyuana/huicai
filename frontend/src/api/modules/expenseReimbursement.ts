import request from '@/api/request'

export interface ExpenseReimbursement {
  id?: number
  employeeId?: number
  employeeName?: string
  docNo?: string
  docDate?: string
  amount?: number
  category?: string
  summary?: string
  status?: string
  voucherId?: number
  approver?: string
  approveTime?: string
  rejectReason?: string
  attachmentIds?: number[]
}

export function pageExpenseReimbursement(params: any): Promise<any> {
  return request.get('/sme/arap/v1/expense-reimbursements/page', { params })
}

export function listExpenseReimbursement(): Promise<any> {
  return request.get('/sme/arap/v1/expense-reimbursements/list')
}

export function getExpenseReimbursement(id: number): Promise<any> {
  return request.get(`/sme/arap/v1/expense-reimbursements/${id}`)
}

export function createExpenseReimbursement(data: ExpenseReimbursement): Promise<any> {
  return request.post('/sme/arap/v1/expense-reimbursements', data)
}

export function updateExpenseReimbursement(id: number, data: ExpenseReimbursement): Promise<any> {
  return request.put(`/sme/arap/v1/expense-reimbursements/${id}`, data)
}

export function submitExpenseReimbursement(id: number): Promise<any> {
  return request.post(`/sme/arap/v1/expense-reimbursements/${id}/submit`)
}

export function approveExpenseReimbursement(id: number, approver?: string): Promise<any> {
  return request.post(`/sme/arap/v1/expense-reimbursements/${id}/approve`, null, { params: { approver } })
}

export function rejectExpenseReimbursement(id: number, reason: string, approver?: string): Promise<any> {
  return request.post(`/sme/arap/v1/expense-reimbursements/${id}/reject`, null, { params: { reason, approver } })
}

export function generateVoucherExpenseReimbursement(id: number, voucherId: number): Promise<any> {
  return request.post(`/sme/arap/v1/expense-reimbursements/${id}/generate-voucher`, null, { params: { voucherId } })
}

export function autoVoucherExpenseReimbursement(id: number): Promise<any> {
  return request.post(`/sme/arap/v1/expense-reimbursements/${id}/auto-voucher`)
}