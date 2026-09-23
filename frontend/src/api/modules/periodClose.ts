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

// ===== P84 结账工作台 =====

export interface SequenceVoucher {
  voucherId: number
  voucherNo: string
  voucherTypeName: string | null
  totalDebit: number
  totalCredit: number
  entryCount: number
  status: string
  createdAt: string
}

/** 单步生成结果：GENERATED / SKIPPED / FAILED */
export interface CarryoverStepResult {
  step: 'DEPR' | 'CLOSE' | 'DISTRIB'
  stepName: string
  voucherId: number | null
  status: 'GENERATED' | 'SKIPPED' | 'FAILED'
  reason: string | null
  vouchers: SequenceVoucher[]
}

/** 生成三步结转序列（DEPR→CLOSE→DISTRIB，各自 DRAFT，失败步骤标记而非中断） */
export function generateSequence(period: string): Promise<CarryoverStepResult[]> {
  return request.get('/base/voucher/v1/period-close/generate-sequence', { params: { period } })
}

/** 一键人工审核记账：DRAFT→SUBMITTED→AUDITED→POSTED，后端同事务 */
export function batchReviewPost(voucherIds: number[]): Promise<void> {
  return request.post('/base/voucher/v1/period-close/batch-review-post', { voucherIds })
}
