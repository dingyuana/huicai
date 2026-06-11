import request from '@/api/request'
import type { PageResult } from '@/types/api'

export interface BankJournalVO {
  id: number
  accountId: number
  txDate: string
  period: string
  txType: string
  counterAccount?: string
  amount: number
  summary?: string
  businessDocId?: number
  voucherId?: number
  isReconciled: boolean
  createdAt?: string
}

export const TX_TYPE_LABELS: Record<string, string> = {
  INCOME: '收入',
  EXPENSE: '支出',
  TRANSFER_IN: '转入',
  TRANSFER_OUT: '转出',
}

export function getBankJournalPage(params: { accountId?: number; period?: string; txType?: string; current?: number; size?: number }): Promise<PageResult<BankJournalVO>> {
  return request.get('/bank-journals/page', { params })
}
export function createBankJournal(data: Partial<BankJournalVO>): Promise<BankJournalVO> {
  return request.post('/bank-journals', data)
}
export function updateBankJournal(id: number, data: Partial<BankJournalVO>): Promise<BankJournalVO> {
  return request.put(`/bank-journals/${id}`, data)
}
export function deleteBankJournal(id: number): Promise<void> {
  return request.delete(`/bank-journals/${id}`)
}
export function generateVoucherFromJournal(id: number): Promise<number> {
  return request.post(`/bank-journals/${id}/generate-voucher`)
}
export function getJournalBalance(accountId: number): Promise<number> {
  return request.get('/bank-journals/balance', { params: { accountId } })
}
