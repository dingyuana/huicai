import request from '@/api/request'

export interface Budget {
  id?: number
  budgetNo: string
  period: string
  budgetType: string
  totalAmount?: number
  status?: string
  remark?: string
  entries?: BudgetEntry[]
}

export interface BudgetEntry {
  id?: number
  budgetId?: number
  subjectId: number
  deptId?: number
  periodMonth?: number
  amount: number
  controlType?: string
  usedAmount?: number
}

export function pageBudget(params: any): Promise<any> {
  return request.get('/budgets/page', { params })
}

export function getBudget(id: number): Promise<Budget> {
  return request.get(`/budgets/${id}`)
}

export function createBudget(data: Budget): Promise<Budget> {
  return request.post('/budgets', data)
}

export function submitBudget(id: number): Promise<Budget> {
  return request.post(`/budgets/${id}/submit`)
}

export function approveBudget(id: number): Promise<Budget> {
  return request.post(`/budgets/${id}/approve`)
}

export function activateBudget(id: number): Promise<Budget> {
  return request.post(`/budgets/${id}/activate`)
}

export function checkBudget(subjectId: number, period: string, amount: number): Promise<any> {
  return request.get('/budgets/check', { params: { subjectId, period, amount } })
}

export function executionAnalysis(period: string): Promise<any> {
  return request.get('/budgets/execution', { params: { period } })
}
