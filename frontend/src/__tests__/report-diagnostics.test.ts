import { describe, it, expect } from 'vitest'
import { toDiagnosticLines, toDiagnosticAlertText, formatDiagnostic } from '@/utils/report/diagnostics'

describe('报表诊断条整形（纯建议，不改数）', () => {
  it('带规则前缀 + 标题 + 明细', () => {
    expect(formatDiagnostic({ ruleId: 'R_CASH_DROP', title: '期末现金骤降', detail: '100000 → 40000' }))
      .toBe('[现金骤降] 期末现金骤降：100000 → 40000')
  })

  it('未知规则 id 原样显示，不吞掉', () => {
    expect(formatDiagnostic({ ruleId: 'R_FUTURE', title: '未来规则' })).toBe('[R_FUTURE] 未来规则')
  })

  it('过滤既无标题也无明细的空诊断', () => {
    const lines = toDiagnosticLines([{ ruleId: 'R_X' }, { ruleId: 'R_REVENUE_ZERO', title: '零收入' }])

    expect(lines).toEqual(['[零收入高费用] 零收入'])
  })

  it('空输入返回空数组（调用方据此不渲染黄条）', () => {
    expect(toDiagnosticLines(null)).toEqual([])
    expect(toDiagnosticLines(undefined)).toEqual([])
    expect(toDiagnosticAlertText([])).toBe('')
  })

  it('多条诊断换行拼接', () => {
    const text = toDiagnosticAlertText([
      { ruleId: 'R_REVENUE_ZERO', title: '零收入', detail: '费用 3000' },
      { ruleId: 'R_OPENING_DISCONTINUITY', title: '期初不连续', detail: '4001 差 100000' },
    ])

    expect(text.split('\n')).toHaveLength(2)
    expect(text).toContain('4001 差 100000')
  })
})
