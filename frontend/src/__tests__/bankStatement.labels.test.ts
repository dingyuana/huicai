import { describe, it, expect } from 'vitest'
import { REVIEW_STATUS_LABELS } from '@/api/modules/bankStatement'

describe('P73 银行流水状态命名去歧义', () => {
  it('BDD: payment_created 显示"待核销"而非"已生单"', () => {
    expect(REVIEW_STATUS_LABELS['payment_created']).toBe('待核销')
  })

  it('BDD: voucher_generated 显示"已制证"', () => {
    expect(REVIEW_STATUS_LABELS['voucher_generated']).toBe('已制证')
  })

  it('BDD: 其余状态命名保持不变', () => {
    expect(REVIEW_STATUS_LABELS['PENDING']).toBe('待确认')
    expect(REVIEW_STATUS_LABELS['CONFIRMED']).toBe('已确认')
    expect(REVIEW_STATUS_LABELS['approved']).toBe('已过账')
    expect(REVIEW_STATUS_LABELS['manual_pending']).toBe('待人工')
  })
})
