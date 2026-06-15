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
  statementPendingConfirm: number
  statementUnmatched: number
  statementIgnored: number
}

export interface UnmatchedItem {
  type: 'BANK_RECEIPT_ENTERPRISE_NOT' | 'BANK_PAYMENT_ENTERPRISE_NOT' | 'ENTERPRISE_RECEIPT_BANK_NOT' | 'ENTERPRISE_PAYMENT_BANK_NOT'
  id: number
  txDate: string
  amount: number
  summary?: string
  counterAccount?: string
}

// P4.1: 5维评分
export interface ScoreResult {
  totalScore: number
  amountScore: number
  dateScore: number
  nameScore: number
  descScore: number
  refScore: number
  remark: string
}

// P4.2: 评分路由
export interface MatchResult {
  statementId: number
  journalId: number | null
  matchStatus: 'MATCHED' | 'PENDING_CONFIRM' | 'UNMATCHED'
  score: number
  remark: string
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
export function calculateScore(accountId: number, statementId: number, journalId: number): Promise<ScoreResult> {
  return request.get('/bank-reconciliation/score', { params: { accountId, statementId, journalId } })
}
export function runMatching(accountId: number, period: string): Promise<MatchResult[]> {
  return request.post('/bank-reconciliation/run-matching', null, { params: { accountId, period } })
}
export function lockReconciliation(accountId: number, period: string, operator: string, ttlSeconds = 300): Promise<boolean> {
  return request.post('/bank-reconciliation/lock', null, { params: { accountId, period, operator, ttlSeconds } })
}
export function unlockReconciliation(accountId: number, period: string, operator: string): Promise<void> {
  return request.post('/bank-reconciliation/unlock', null, { params: { accountId, period, operator } })
}
