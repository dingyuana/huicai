import { describe, it, expect } from 'vitest'
import {
  isRowVisible,
  guardDanglingSubtotal,
  isStandardBlankRow,
  STANDARD_BLANK_LABELS,
} from '@/utils/report/rowVisibility'

describe('rowVisibility — 明细行可见性（任一展示列非零即显示）', () => {
  it('年初非零、期末为零的明细行必须可见（旧实现只看期末会误藏）', () => {
    expect(isRowVisible([80000, 0])).toBe(true)
    expect(isRowVisible([0, 0])).toBe(false)
  })

  it('四列口径（科目余额表）任一列非零即显示', () => {
    expect(isRowVisible([0, 0, 0, 0])).toBe(false)
    expect(isRowVisible([0, 0, 0, 5])).toBe(true)
    expect(isRowVisible([12, 0, 0, 0])).toBe(true)
  })

  it('null/undefined/空串按 0 处理，不抛错', () => {
    expect(isRowVisible([null, undefined, '', 0])).toBe(false)
    expect(isRowVisible([null, 3])).toBe(true)
  })
})

describe('rowVisibility — 悬空保护（小计非零却无明细）', () => {
  const rows = [
    { code: '1601', amt: 100 },
    { code: '1602', amt: -5000 },
    { code: '1603', amt: 0 },
  ]
  const amountOf = (r: { amt: number }) => r.amt

  it('明细全被隐藏且小计非零时，强制保留金额最大的明细行', () => {
    const result = guardDanglingSubtotal(rows, [false, false, false], true, amountOf)
    expect(result).toEqual([false, true, false])
  })

  it('负向：绝不允许出现「有合计无明细」', () => {
    const result = guardDanglingSubtotal(rows, [false, false, false], true, amountOf)
    expect(result.some(Boolean)).toBe(true)
  })

  it('小计为零时不做保护，原判定原样返回', () => {
    expect(guardDanglingSubtotal(rows, [false, false, false], false, amountOf))
      .toEqual([false, false, false])
  })

  it('已有可见明细时不改动判定', () => {
    expect(guardDanglingSubtotal(rows, [true, false, false], true, amountOf))
      .toEqual([true, false, false])
  })

  it('空明细行数组不越界', () => {
    expect(guardDanglingSubtotal([], [], true, amountOf)).toEqual([])
  })
})

describe('rowVisibility — 标准空白行（与明细过滤互不联动）', () => {
  it('白名单标签全零时属标准空白行', () => {
    expect(isStandardBlankRow('其他资产', [0, 0])).toBe(true)
    expect(isStandardBlankRow('其他负债', [0, 0])).toBe(true)
    expect(STANDARD_BLANK_LABELS).toContain('其他资产')
  })

  it('白名单标签有值时不是空白行', () => {
    expect(isStandardBlankRow('其他资产', [0, 1234])).toBe(false)
  })

  it('非白名单标签即使全零也不归本开关管（归明细过滤）', () => {
    expect(isStandardBlankRow('1002 银行存款', [0, 0])).toBe(false)
    expect(isStandardBlankRow('流动资产合计', [0, 0])).toBe(false)
    expect(isStandardBlankRow('流动资产合计', [0, 0], true)).toBe(false)
  })

  it('骨架行仅在显式标记 isSkeleton 时才算标准空白', () => {
    expect(isStandardBlankRow('五、期末现金及现金等价物余额', [0, 0])).toBe(false)
    expect(isStandardBlankRow('五、期末现金及现金等价物余额', [0, 0], true)).toBe(true)
  })

  it('勾稽提示行永显：即便全零且带骨架标记也不隐藏', () => {
    expect(isStandardBlankRow('⚠ 勾稽差异（期末现金 vs 科目余额）', [0, 0], true)).toBe(false)
    expect(isStandardBlankRow('勾稽校验', [0, 0], true)).toBe(false)
  })

  it('三分小计永不隐藏（负向：P92-B 不折叠口径不得被破坏）', () => {
    for (const label of ['流动资产合计', '非流动资产合计', '其他资产']) {
      const hidden = isStandardBlankRow(label, [0, 0], label !== '其他资产')
      expect(hidden, `${label} 不应被骨架开关隐藏`).toBe(label === '其他资产')
    }
  })
})
