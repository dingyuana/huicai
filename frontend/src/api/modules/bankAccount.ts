import request from '@/api/request'
import type { PageResult } from '@/types/api'

export interface BankAccountVO {
  id: string
  accountNo: string
  accountName: string
  bankName?: string
  currency?: string
  subjectId?: string
  balance: number
  isActive: boolean
  remark?: string
  createdAt?: string
}

export function getBankAccountPage(params: { keyword?: string; current?: number; size?: number }): Promise<PageResult<BankAccountVO>> {
  return request.get('/sme/cash/v1/bank-accounts/page', { params })
}
export function getActiveBankAccounts(): Promise<BankAccountVO[]> {
  return request.get('/sme/cash/v1/bank-accounts/active')
}
export function getBankAccount(id: string): Promise<BankAccountVO> {
  return request.get(`/sme/cash/v1/bank-accounts/${id}`)
}
export function createBankAccount(data: Partial<BankAccountVO>): Promise<BankAccountVO> {
  return request.post('/sme/cash/v1/bank-accounts', data)
}
export function updateBankAccount(id: string, data: Partial<BankAccountVO>): Promise<BankAccountVO> {
  return request.put(`/sme/cash/v1/bank-accounts/${id}`, data)
}
export function deleteBankAccount(id: string): Promise<void> {
  return request.delete(`/sme/cash/v1/bank-accounts/${id}`)
}
