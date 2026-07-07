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
  return request.get('/bank-accounts/page', { params })
}
export function getActiveBankAccounts(): Promise<BankAccountVO[]> {
  return request.get('/bank-accounts/active')
}
export function getBankAccount(id: string): Promise<BankAccountVO> {
  return request.get(`/bank-accounts/${id}`)
}
export function createBankAccount(data: Partial<BankAccountVO>): Promise<BankAccountVO> {
  return request.post('/bank-accounts', data)
}
export function updateBankAccount(id: string, data: Partial<BankAccountVO>): Promise<BankAccountVO> {
  return request.put(`/bank-accounts/${id}`, data)
}
export function deleteBankAccount(id: string): Promise<void> {
  return request.delete(`/bank-accounts/${id}`)
}
