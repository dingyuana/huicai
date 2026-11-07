import { describe, it, expect } from 'vitest'
import { periodRange, prevPeriod, yearStartPeriod } from '@/utils/period'

/**
 * SPEC-P151：periodRange 是银行流水「默认只看当前账期」的口径来源，
 * 必须与后端 PeriodRange（YearMonth.atDay(1)/atEndOfMonth）严格一致，
 * 否则前端显示本月、后端按别的区间查（两端漂移）。
 */
describe('period utils — periodRange（SPEC-P151 账期口径）', () => {
  it('普通月份：202407 → 07-01 ~ 07-31', () => {
    expect(periodRange('202407')).toEqual({ start: '2024-07-01', end: '2024-07-31' })
  })

  it('闰年二月：202402 → 02-29（与后端 YearMonth.atEndOfMonth 一致）', () => {
    expect(periodRange('202402')).toEqual({ start: '2024-02-01', end: '2024-02-29' })
  })

  it('平年二月：202302 → 02-28（闰年口径错误会多查一天）', () => {
    expect(periodRange('202302')).toEqual({ start: '2023-02-01', end: '2023-02-28' })
  })

  it('31 天月份：202401 / 202412 收在月末', () => {
    expect(periodRange('202401')).toEqual({ start: '2024-01-01', end: '2024-01-31' })
    expect(periodRange('202412')).toEqual({ start: '2024-12-01', end: '2024-12-31' })
  })

  it('月份个位补零：202403 → 03（开始日期前导零不可省）', () => {
    expect(periodRange('202403')).toEqual({ start: '2024-03-01', end: '2024-03-31' })
  })

  it('非法输入返回 null（空串/截断/月份越界），不退化成全量扫描', () => {
    expect(periodRange('')).toBeNull()
    expect(periodRange(null)).toBeNull()
    expect(periodRange(undefined)).toBeNull()
    expect(periodRange('2024')).toBeNull()
    expect(periodRange('2024073')).toBeNull()
    expect(periodRange('202413')).toBeNull()
    expect(periodRange('202400')).toBeNull()
    expect(periodRange('abcdef')).toBeNull()
  })

  it('与 prevPeriod/yearStartPeriod 口径自洽：区间起点落在同一期间内', () => {
    const r = periodRange('202407')!
    expect(r.start.endsWith('-01')).toBe(true)
    expect(prevPeriod('202407')).toBe('202406')
    expect(yearStartPeriod('202407')).toBe('202401')
  })
})
