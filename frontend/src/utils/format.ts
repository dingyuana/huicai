/**
 * 报表金额格式化工具（P89-A：千分位 + 负数标红）
 *
 * 背景：三张报表（资产负债/利润/现金流）与科目余额表各自内联
 * `Number(v || 0).toFixed(2)`，无千分位、无负数视觉区分。本模块提供统一
 * 的格式化函数与 CSS 类名约定，供各报表页复用。
 *
 * 约定：
 * - 财务金额显示两位小数
 * - 千分位用 `toLocaleString('zh-CN', {minimumFractionDigits, maximumFractionDigits})`
 * - 负数由调用方加 `amount-negative` class 标红（CSS 见下方注释）
 */

/** 格式化金额为千分位字符串，保留 2 位小数。null/undefined/非数字 → "0.00" */
export function formatAmount(v: unknown): string {
  const n = Number(v || 0)
  if (!Number.isFinite(n)) return '0.00'
  return n.toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })
}

/** 千分位，保留 0 位小数（用于行数、笔数等非金额计数） */
export function formatInt(v: unknown): string {
  const n = Number(v || 0)
  if (!Number.isFinite(n)) return '0'
  return Math.round(n).toLocaleString('zh-CN')
}

/** 判断是否为负数（用于绑定标红 class） */
export function isNegative(v: unknown): boolean {
  const n = Number(v || 0)
  return Number.isFinite(n) && n < 0
}

/**
 * 构建金额 span 的 class 绑定对象。
 * @param bold  加粗（合计/小计行）
 * @param v     金额值（负数标红）
 * @param warn  异常提示（如现金贷方余额），优先级高于负数标红
 */
export function amountClass(bold: boolean, v: unknown, warn?: boolean): Record<string, boolean> {
  return {
    'amount-bold': !!bold,
    'amount-warn': !!warn,
    'amount-negative': !warn && isNegative(v),
  }
}

/*
 * 配套 CSS 位于 src/styles/report-amount.css（main.ts 全局引入），类名全局一致：
 *   .amount-negative 负数标红 / .amount-bold 加粗 / .amount-warn 异常提示
 */
