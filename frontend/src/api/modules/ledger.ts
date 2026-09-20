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
  yearBeginBalance: number
  yearDebitTotal: number
  yearCreditTotal: number
}

export interface LedgerRow {
  type?: 'OPENING' | 'ENTRY' | 'CLOSING' | 'YEAR_TOTAL'
  voucherId?: number
  voucherNo?: string
  voucherDate?: string
  summary?: string
  debit: number
  credit: number
  running: number
  subjectCode?: string
  subjectName?: string
}

export interface TrialBalance {
  period: string
  balanced: boolean
  beginBalanced: boolean
  movementBalanced: boolean
  endBalanced: boolean
  totalBeginDebit: number
  totalBeginCredit: number
  totalDebitTotal: number
  totalCreditTotal: number
  totalEndDebit: number
  totalEndCredit: number
  empty?: boolean
  emptyMessage?: string
}

export interface SubjectBalanceParams {
  includeZero?: boolean
  includeNoMovement?: boolean
  subjectCodePrefix?: string
}

export interface GeneralLedgerParams {
  includeUnposted?: boolean
}

export interface SubsidiaryLedgerParams {
  startDate?: string
  endDate?: string
  includeUnposted?: boolean
}

export function getSubjectBalance(
  period: string,
  params?: SubjectBalanceParams,
): Promise<SubjectBalanceRow[]> {
  return request.get('/base/voucher/v1/ledgers/subject-balance', { params: { period, ...params } })
}

export function getGeneralLedger(
  subjectId: number,
  period: string,
  params?: GeneralLedgerParams,
): Promise<LedgerRow[]> {
  return request.get('/base/voucher/v1/ledgers/general', { params: { subjectId, period, ...params } })
}

export function getSubsidiaryLedger(
  subjectId: number,
  period: string,
  params?: SubsidiaryLedgerParams,
): Promise<LedgerRow[]> {
  return request.get('/base/voucher/v1/ledgers/subsidiary', {
    params: { subjectId, period, ...params },
  })
}

export function getTrialBalance(period: string): Promise<TrialBalance> {
  return request.get('/base/voucher/v1/ledgers/trial-balance', { params: { period } })
}

export function getMultiColumnLedger(parentSubjectCode: string, period: string): Promise<SubjectBalanceRow[]> {
  return request.get('/base/voucher/v1/ledgers/multi-column', { params: { parentSubjectCode, period } })
}

export interface QuantityAmountLedgerRow {
  voucherNo: string
  voucherDate: string
  subjectCode: string
  subjectName: string
  summary: string
  debitQuantity: number
  creditQuantity: number
  unitPrice: number
  debitAmount: number
  creditAmount: number
  closingQuantity: number
  closingAmount: number
}

export function getQuantityAmountLedger(subjectCode: string, period: string): Promise<QuantityAmountLedgerRow[]> {
  return request.get('/base/voucher/v1/ledgers/quantity-amount', { params: { subjectCode, period } })
}

export type AuxiliaryDimensionType = 'customer' | 'vendor' | 'department' | 'project' | 'employee'

export interface AuxiliaryLedgerRow {
  dimensionType: string
  dimensionValue?: number
  dimensionName?: string
  subjectId: number
  subjectCode: string
  subjectName: string
  direction: 'debit' | 'credit'
  beginBalance: number
  debitTotal: number
  creditTotal: number
  endBalance: number
}

export function getAuxiliaryLedger(
  dimensionType: AuxiliaryDimensionType,
  period: string,
  dimensionValue?: number,
): Promise<AuxiliaryLedgerRow[]> {
  return request.get('/base/voucher/v1/ledgers/auxiliary', {
    params: { dimensionType, period, ...(dimensionValue ? { dimensionValue } : {}) },
  })
}
