import request from '@/api/request'

export interface ExpenseReimbursement {
  id?: number
  employeeId: number
  expenseType: string
  amount: number
  summary: string
  remark?: string
  status?: string
  attachmentIds?: number[]
}

export function pageExpense(params: any): Promise<any> {
  return request.get('/sme/arap/v1/expense-reimbursements/page', { params })
}

export function getExpense(id: number): Promise<ExpenseReimbursement> {
  return request.get(`/sme/arap/v1/expense-reimbursements/${id}`)
}

export function createExpense(data: ExpenseReimbursement): Promise<ExpenseReimbursement> {
  return request.post('/sme/arap/v1/expense-reimbursements', data)
}

export function updateExpense(id: number, data: ExpenseReimbursement): Promise<ExpenseReimbursement> {
  return request.put(`/sme/arap/v1/expense-reimbursements/${id}`, data)
}

export function submitExpense(id: number): Promise<ExpenseReimbursement> {
  return request.post(`/sme/arap/v1/expense-reimbursements/${id}/submit`)
}

export function approveExpense(id: number, approver?: string): Promise<ExpenseReimbursement> {
  return request.post(`/sme/arap/v1/expense-reimbursements/${id}/approve`, null, { params: { approver } })
}

export function rejectExpense(id: number, reason: string): Promise<ExpenseReimbursement> {
  return request.post(`/sme/arap/v1/expense-reimbursements/${id}/reject`, null, { params: { reason } })
}

export function generateVoucher(id: number, voucherId: number): Promise<ExpenseReimbursement> {
  return request.post(`/sme/arap/v1/expense-reimbursements/${id}/generate-voucher`, null, { params: { voucherId } })
}

export function autoVoucher(id: number): Promise<ExpenseReimbursement> {
  return request.post(`/sme/arap/v1/expense-reimbursements/${id}/auto-voucher`)
}