/**
 * P97/REQ-097 科目余额表组树（纯函数，供余额表组树与单测共用）。
 *
 * 为什么放独立模块：组树里有两条容易静默丢数的规则（父级不在结果集、科目被逻辑删除），
 * 写在 .vue 里既无法单测也容易被后续改动踩坏。
 *
 * 数据前提：后端只返回**有余额行**的科目，父科目未必出现在结果集中；
 * 父级缺失时必须把该行提升为根，否则整棵子树会从报表上消失。
 */

export interface BalanceRow {
  subjectId: number | string
  subjectCode: string
  subjectName: string
  parentId?: number | string | null
  level?: number | null
  isLeaf?: boolean | null
  endBalance?: number | string | null
  beginBalance?: number | string | null
  debitTotal?: number | string | null
  creditTotal?: number | string | null
  children?: BalanceRow[]
}

const key = (v: number | string | null | undefined): string => (v == null ? '' : String(v))

/**
 * 取字段时同时兼容 camelCase 与 snake_case。
 * 必须兼容：科目余额表页面走报表端点（SQL 直出 Map，键为 subject_id/parent_id/code），
 * 而 balance 模块端点返回 VO（camelCase）。若只认一种形态，另一种会全部落空——
 * 曾因只认 camelCase 导致所有行 key 为空串互相覆盖，整张表只剩 1 行。
 */
const pick = (row: Record<string, unknown>, camel: string, snake: string): unknown =>
  row[camel] !== undefined ? row[camel] : row[snake]

/** 科目编码兜底排序：与后端 ORDER BY code 一致（字典序） */
const byCode = (a: BalanceRow, b: BalanceRow) =>
  String(a.subjectCode ?? '').localeCompare(String(b.subjectCode ?? ''))

/**
 * 组树：
 * 1. 父级存在于结果集 → 挂到父级 children
 * 2. 父级不存在（含 parentId 为空、指向已删除科目）→ 提升为根，绝不丢行
 * 3. 同一父级下按科目编码升序，根节点亦然，保证展示顺序稳定
 */
export const buildSubjectTree = (rows: BalanceRow[]): BalanceRow[] => {
  const nodes = new Map<string, BalanceRow>()
  for (const r of rows || []) {
    const raw = r as unknown as Record<string, unknown>
    const node: BalanceRow = {
      ...r,
      subjectId: (pick(raw, 'subjectId', 'subject_id') ?? r.subjectId) as string,
      subjectCode: String(pick(raw, 'subjectCode', 'code') ?? r.subjectCode ?? ''),
      parentId: (pick(raw, 'parentId', 'parent_id') ?? null) as string | null,
      endBalance: (pick(raw, 'endBalance', 'end_balance') ?? 0) as number,
      children: [],
    }
    nodes.set(key(node.subjectId), node)
  }
  const roots: BalanceRow[] = []
  for (const node of nodes.values()) {
    const parent = node.parentId == null ? null : nodes.get(key(node.parentId))
    if (parent && parent !== node) {
      parent.children!.push(node)
    } else {
      roots.push(node)
    }
  }
  const sortRec = (list: BalanceRow[]) => {
    list.sort(byCode)
    list.forEach(n => n.children && sortRec(n.children))
  }
  sortRec(roots)
  return roots
}

/**
 * 树是否守恒：父级期末余额 == 子级（含孙级）期末余额之和（容差 0.01）。
 *
 * 判据为「每一级父级 == 其直接子级之和」，即假定每级都存本级汇总值。
 * 注意：这是**自检诊断**，不是系统硬保证——父级是否有余额行取决于 t_subject_balance 数据形态
 * （当前库多为末级科目才有余额行，此时无子节点、校验平凡通过）。为 false 说明存在未汇总或
 * 重复计入，须人工核对，不可据此自动改数（铁律 #1）。
 */
export const isTreeConserved = (rows: BalanceRow[], tolerance = 0.01): boolean => {
  const walk = (list: BalanceRow[]): boolean =>
    (list || []).every(node => {
      if (!node.children?.length) return true
      const sum = node.children.reduce((acc, c) => acc + Number(c.endBalance || 0), 0)
      return Math.abs(Number(node.endBalance || 0) - sum) < tolerance && walk(node.children)
    })
  return walk(rows || [])
}

/** el-table 树形表格所需配置 */
export const TREE_PROPS = { children: 'children', hasChildren: 'hasChildren' }
