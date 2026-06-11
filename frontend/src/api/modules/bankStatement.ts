import request from '@/api/request'
import type { PageResult } from '@/types/api'

export interface BankStatementVO {
  id: number
  accountId: number
  txDate: string
  txType: string
  counterAccount?: string
  amount: number
  summary?: string
  externalNo?: string
  matchedJournalId?: number
  matchStatus: string
  importedAt?: string
}

export interface MatchSuggestion {
  statementId: number
  txDate: string
  amount: number
  counterAccount?: string
  matchedJournalId?: number
  score: number
}

export const MATCH_STATUS_LABELS: Record<string, string> = {
  UNMATCHED: '未匹配',
  MATCHED: '已匹配',
  MANUAL_MATCHED: '手工匹配',
  IGNORED: '已忽略',
}

export function getBankStatementPage(params: { accountId?: number; status?: string; current?: number; size?: number }): Promise<PageResult<BankStatementVO>> {
  return request.get('/bank-statements/page', { params })
}
export function importStatementCsv(accountId: number, csvContent: string): Promise<number> {
  return request.post('/bank-statements/import-csv', csvContent, { params: { accountId }, headers: { 'Content-Type': 'text/plain' } })
}
export function autoMatchStatements(accountId: number): Promise<MatchSuggestion[]> {
  return request.get('/bank-statements/auto-match', { params: { accountId } })
}
export function confirmStatementMatch(statementId: number, journalId: number): Promise<number> {
  return request.post(`/bank-statements/${statementId}/confirm-match`, null, { params: { journalId } })
}
export function ignoreStatement(statementId: number): Promise<number> {
  return request.post(`/bank-statements/${statementId}/ignore`)
}
