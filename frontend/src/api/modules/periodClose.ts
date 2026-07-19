import request from '@/api/request'

export interface CloseCheckResult {
  passed: boolean
  issues: string[]
  trialBalance: {
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
  }
}

export function checkClose(period: string): Promise<CloseCheckResult> {
  return request.get('/base/voucher/v1/period-close/check', { params: { period } })
}

export function profitCarryover(period: string): Promise<number> {
  return request.post('/base/voucher/v1/period-close/profit-carryover', null, { params: { period } })
}

export function closePeriod(period: string): Promise<void> {
  return request.post('/base/voucher/v1/period-close/close', null, { params: { period } })
}

export function reopenPeriod(period: string): Promise<void> {
  return request.post('/base/voucher/v1/period-close/reopen', null, { params: { period } })
}
