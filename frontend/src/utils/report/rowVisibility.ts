/**
 * P94 REQ-091 报表行可见性规则（纯函数，供资产负债表/科目余额表/现金流量表共用）。
 *
 * 抽成独立模块的原因：可见性规则是财务口径而非展示细节，藏在 .vue 里既无法单测
 * 也容易三份实现各说各话（本项目已出现过三视图判定不一致）。
 *
 * 两个开关职责严格分离：
 *   开关1 隐藏无发生额且无余额科目 → isRowVisible，按明细行全部展示列判定
 *   开关2 隐藏报表标准空白行     → isStandardBlankRow，只管白名单骨架行
 * 两者互不联动，避免一个开关误伤另一个的语义。
 */

export type AmountLike = number | string | null | undefined

const nonZero = (v: AmountLike): boolean => Number(v ?? 0) !== 0

/** 明细行可见性：任一展示列非零即显示。年初≠0、期末=0 的行不得被隐藏。 */
export const isRowVisible = (values: AmountLike[]): boolean => values.some(nonZero)

/**
 * 悬空保护：小计非零但其明细按规则全被隐藏时，强制保留金额最大的一行。
 * 返回值与入参等长的可见性数组，不修改入参。
 */
export const guardDanglingSubtotal = <T>(
  rows: T[],
  visibleByRule: boolean[],
  subtotalIsNonZero: boolean,
  amountOf: (row: T) => number,
): boolean[] => {
  if (!subtotalIsNonZero || visibleByRule.some(Boolean) || rows.length === 0) {
    return visibleByRule
  }
  let keepIdx = 0
  let maxAbs = -1
  rows.forEach((row, i) => {
    const abs = Math.abs(amountOf(row))
    if (abs > maxAbs) {
      maxAbs = abs
      keepIdx = i
    }
  })
  return rows.map((_, i) => (i === keepIdx ? true : visibleByRule[i]))
}

/** 开关2 的标签白名单：兜底分类，非法定列报项目，全零时无列报意义。 */
export const STANDARD_BLANK_LABELS = ['其他资产', '其他负债'] as const

/** 含该关键字的行永不隐藏：勾稽异常是提示义务，不能被"清理空白行"顺手抹掉。 */
const NEVER_HIDE_KEYWORD = '勾稽'

/**
 * 法定小计/合计行永不隐藏（P92-B 不折叠口径）。
 * 即便调用方把它们标成骨架行也不隐藏——这条是报送硬要求，不能依赖调用方传参正确。
 */
const NEVER_HIDE_LABELS = [
  '流动资产合计', '非流动资产合计',
  '流动负债合计', '非流动负债合计',
  '资产合计', '资产总计',
  '负债+权益合计', '负债+所有者权益合计',
]

/**
 * 是否属"报表标准空白行"。skeleton 为调用方对报表骨架行的显式标记
 * （现金流量表的期初/期末现金行 fixed=true），白名单标签无需标记。
 */
export const isStandardBlankRow = (
  label: string,
  values: AmountLike[],
  skeleton = false,
): boolean => {
  const name = String(label ?? '').trim()
  if (name.includes(NEVER_HIDE_KEYWORD) || NEVER_HIDE_LABELS.includes(name)) return false
  if (values.some(nonZero)) return false
  return skeleton || (STANDARD_BLANK_LABELS as readonly string[]).includes(name)
}
