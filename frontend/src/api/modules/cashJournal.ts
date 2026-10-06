import request from '@/api/request'

export interface CashJournal {
  // 2026-10-06（P102 批次4）：本接口原先声明的 docNo/docDate/subjectName/status 四个字段
  // —— **在 t_cash_journal 上一个都不存在**（真实列是 journal_no / journal_date，且无
  // subject_name / status 列），且页面 CashJournalList.vue 从未使用它们 ⇒ 属陈旧死接口。
  // 页面真实读的是 journalNo/journalDate/summary/debit/credit/balance/voucherId，
  // 全部是真实列。故把接口改成页面真实在用的字段（与 Employee 的 department 同型处置）。
  id?: number
  period?: string
  journalDate?: string
  journalNo?: string
  summary?: string
  subjectId?: number
  oppositeSubjectId?: number
  debit?: number
  credit?: number
  balance?: number
  source?: string
  voucherId?: number
}

export function pageCashJournal(params: any): Promise<any> {
  return request.get('/sme/cash/v1/cash-journals/page', { params })
}

export function getCashJournal(id: number): Promise<CashJournal> {
  return request.get(`/sme/cash/v1/cash-journals/${id}`)
}

export function createCashJournal(data: CashJournal): Promise<CashJournal> {
  return request.post('/sme/cash/v1/cash-journals', data)
}

export function updateCashJournal(id: number, data: CashJournal): Promise<CashJournal> {
  return request.put(`/sme/cash/v1/cash-journals/${id}`, data)
}

export function deleteCashJournal(id: number): Promise<void> {
  return request.delete(`/sme/cash/v1/cash-journals/${id}`)
}

export function generateVoucherCashJournal(id: number): Promise<any> {
  return request.post(`/sme/cash/v1/cash-journals/${id}/generate-voucher`)
}