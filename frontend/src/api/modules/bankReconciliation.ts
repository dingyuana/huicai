import request from '@/api/request'

export interface Adjustment {
  accountId: number
  accountName: string
  accountNo: string
  period: string
  enterpriseBalance: number
  bankBalance: number
  diff: number
  balanced: boolean
}

export interface ReconciliationSummary {
  enterpriseTotal: number
  enterpriseReconciled: number
  enterpriseUnreconciled: number
  statementTotal: number
  statementMatched: number
  statementUnmatched: number
  statementIgnored: number
}

export interface UnmatchedItem {
  type: 'ENTERPRISE_ONLY' | 'BANK_ONLY'
  id: number
  txDate: string
  amount: number
  summary?: string
  counterAccount?: string
}

export function getAdjustment(accountId: number, period: string): Promise<Adjustment> {
  return request.get('/bank-reconciliation/adjustment', { params: { accountId, period } })
}
export function getReconciliationSummary(accountId: number, period: string): Promise<ReconciliationSummary> {
  return request.get('/bank-reconciliation/summary', { params: { accountId, period } })
}
export function getUnmatchedItems(accountId: number, period: string): Promise<UnmatchedItem[]> {
  return request.get('/bank-reconciliation/unmatched', { params: { accountId, period } })
}
