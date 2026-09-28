import { describe, it, expect } from 'vitest'
import { buildSubjectTree, isTreeConserved } from '@/utils/report/subjectTree'

const row = (id: number, code: string, parentId: number | null, end: number, level = 2) => ({
  subjectId: id,
  subjectCode: code,
  subjectName: `科目${code}`,
  parentId,
  level,
  isLeaf: true,
  endBalance: end,
})

describe('buildSubjectTree — 余额表组树', () => {
  it('按 parentId 挂到父级，根节点按科目编码升序', () => {
    const tree = buildSubjectTree([
      row(2, '100202', 1, 300),
      row(1, '1002', null, 1000),
      row(3, '100201', 1, 700),
    ])

    expect(tree).toHaveLength(1)
    expect(tree[0].subjectCode).toBe('1002')
    expect(tree[0].children!.map(c => c.subjectCode)).toEqual(['100201', '100202'])
  })

  it('父级不在结果集时提升为根，绝不丢行（P92B-BD3 精神）', () => {
    const rows = [row(10, '100201', 999, 500), row(11, '100202', 999, 200)]

    const tree = buildSubjectTree(rows)

    expect(tree).toHaveLength(2)
    expect(tree.map(t => t.subjectCode).sort()).toEqual(['100201', '100202'])
  })

  it('parentId 为空的科目为根', () => {
    const tree = buildSubjectTree([row(1, '1002', null, 10), row(2, '2202', null, 20)])

    expect(tree).toHaveLength(2)
  })

  it('自引用（parentId 指向自己）不死循环', () => {
    const tree = buildSubjectTree([row(5, '1002', 5, 100)])

    expect(tree).toHaveLength(1)
    expect(tree[0].children).toHaveLength(0)
  })

  it('兼容 snake_case 载荷（报表端点 SQL 直出），不得因键名不同而丢行', () => {
    // 回归：曾只认 camelCase，导致 snake_case 载荷全部 key 为空串、互相覆盖只剩 1 行
    const rows = [
      { subject_id: '1', code: '1002', parent_id: null, end_balance: 1000, level: 1 },
      { subject_id: '2', code: '100201', parent_id: '1', end_balance: 1000, level: 2 },
      { subject_id: '3', code: '2202', parent_id: null, end_balance: 20, level: 1 },
    ] as any

    const tree = buildSubjectTree(rows)

    expect(tree).toHaveLength(2)
    expect(tree.map(t => t.subjectCode)).toEqual(['1002', '2202'])
    expect(tree[0].children!.map(c => c.subjectCode)).toEqual(['100201'])
  })

  it('空输入返回空树', () => {
    expect(buildSubjectTree([])).toEqual([])
  })
})

describe('isTreeConserved — 树守恒校验', () => {
  it('父级期末 == 子级之和则守恒', () => {
    const tree = buildSubjectTree([
      row(1, '1002', null, 1000, 1),
      row(2, '100201', 1, 700),
      row(3, '100202', 1, 300),
    ])

    expect(isTreeConserved(tree)).toBe(true)
  })

  it('父级期末 != 子级之和则不守恒（负向）', () => {
    const tree = buildSubjectTree([
      row(1, '1002', null, 999, 1),
      row(2, '100201', 1, 700),
    ])

    expect(isTreeConserved(tree)).toBe(false)
  })

  it('多层嵌套同样校验（父级值为子级汇总时守恒）', () => {
    const tree = buildSubjectTree([
      row(1, '1002', null, 1000, 1),
      row(2, '100201', 1, 1000, 2),
      row(3, '10020101', 2, 1000, 3),
    ])

    expect(isTreeConserved(tree)).toBe(true)
  })
})
