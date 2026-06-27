import request from '@/api/request'

export interface CashJournal {
  id?: number
  docNo?: string
  docDate?: string
  summary?: string
  subjectId?: number
  subjectName?: string
  debit?: number
  credit?: number
  balance?: number
  status?: string
  voucherId?: number
}

export function pageCashJournal(params: any): Promise<any> {
  return request.get('/cash-journals/page', { params })
}

export function getCashJournal(id: number): Promise<CashJournal> {
  return request.get(`/cash-journals/${id}`)
}

export function createCashJournal(data: CashJournal): Promise<CashJournal> {
  return request.post('/cash-journals', data)
}

export function updateCashJournal(id: number, data: CashJournal): Promise<CashJournal> {
  return request.put(`/cash-journals/${id}`, data)
}

export function deleteCashJournal(id: number): Promise<void> {
  return request.delete(`/cash-journals/${id}`)
}

export function generateVoucherCashJournal(id: number): Promise<any> {
  return request.post(`/cash-journals/${id}/generate-voucher`)
}