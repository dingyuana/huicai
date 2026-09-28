import { describe, it, expect } from 'vitest'
import { formatAuxItem, groupAuxBySubject } from '@/utils/report/auxFormat'

describe('formatAuxItem — 辅助核算项通用展示（不假设 schema）', () => {
  it('对象 JSON 拼成键值对', () => {
    expect(formatAuxItem('{"vendorId":11,"vendorName":"甲供应商"}'))
      .toBe('vendorId: 11 / vendorName: 甲供应商')
  })

  it('键名变化时自动跟随，不返回空', () => {
    expect(formatAuxItem('{"supplierCode":"S-01","supplierName":"乙供应商"}'))
      .toBe('supplierCode: S-01 / supplierName: 乙供应商')
  })

  it('非 JSON 字符串原样返回', () => {
    expect(formatAuxItem('not-json')).toBe('not-json')
  })

  it('空值返回空串', () => {
    expect(formatAuxItem(null)).toBe('')
    expect(formatAuxItem('')).toBe('')
  })

  it('过滤 null 与空串值', () => {
    expect(formatAuxItem('{"a":1,"b":null,"c":""}')).toBe('a: 1')
  })
})

describe('groupAuxBySubject — 按科目聚合', () => {
  it('同一科目多个辅助项聚成多行', () => {
    const map = groupAuxBySubject([
      { subject_code: '1123', assist_json: '{"vendorId":11}' },
      { subject_code: '1123', assist_json: '{"vendorId":22}' },
      { subject_code: '2203', assist_json: '{"customerId":33}' },
    ])

    expect(map.get('1123')).toHaveLength(2)
    expect(map.get('2203')).toHaveLength(1)
  })

  it('兼容 camelCase 键（balance 模块 VO 形态）', () => {
    const map = groupAuxBySubject([{ subjectCode: '1123', assistJson: '{"vendorId":11}' }])

    expect(map.get('1123')).toHaveLength(1)
  })

  it('空输入返回空 Map', () => {
    expect(groupAuxBySubject([]).size).toBe(0)
  })
})
