import request from '@/api/request'

export interface SubjectBalanceRow {
  subjectId: number
  subjectCode: string
  subjectName: string
  direction: string
  beginBalance: number
  debitTotal: number
  creditTotal: number
  endBalance: number
}

export interface LedgerRow {
  type?: 'OPENING' | 'ENTRY' | 'CLOSING'
  voucherId?: number
  summary?: string
  debit: number
  credit: number
  running: number
  assistJson?: string
  subjectCode?: string
  subjectName?: string
}

export interface TrialBalance {
  period: string
  beginBalanced: boolean
  movementBalanced: boolean
  endBalanced: boolean
  totalBeginDebit: number
  totalBeginCredit: number
  totalDebitTotal: number
  totalCreditTotal: number
  totalEndDebit: number
  totalEndCredit: number
  balanced: boolean
}

export function getSubjectBalance(period: string): Promise<SubjectBalanceRow[]> {
  return request.get('/ledgers/subject-balance', { params: { period } })
}

export function getGeneralLedger(subjectId: number, period: string): Promise<LedgerRow[]> {
  return request.get('/ledgers/general', { params: { subjectId, period } })
}

export function getSubsidiaryLedger(subjectId: number, period: string): Promise<LedgerRow[]> {
  return request.get('/ledgers/subsidiary', { params: { subjectId, period } })
}

export function getTrialBalance(period: string): Promise<TrialBalance> {
  return request.get('/ledgers/trial-balance', { params: { period } })
}
