import type { Ref } from 'vue'
import dayjs from 'dayjs'

const PERIOD_RE = /^\d{6}$/

/**
 * 期间导航 composable：基于 dayjs 做 ±1 月运算，直接写回传入的 ref。
 *
 * 无效期间（不匹配 /^\d{6}$/ 或 dayjs 解析失败）不跳转，返回原值，
 * 由调用方决定是否仍需 emit。
 *
 * @param currentPeriod 当前期间 ref（YYYYMM 字符串）
 * @returns prevPeriod() / nextPeriod()，均返回跳转后的期间字符串
 */
export function usePeriodNavigation(currentPeriod: Ref<string>) {
  function shift(delta: number): string {
    const cur = currentPeriod.value
    if (!PERIOD_RE.test(cur)) return cur
    const d = dayjs(cur, 'YYYYMM')
    if (!d.isValid()) return cur
    const next = d.add(delta, 'month').format('YYYYMM')
    currentPeriod.value = next
    return next
  }

  function prevPeriod(): string {
    return shift(-1)
  }

  function nextPeriod(): string {
    return shift(1)
  }

  return { prevPeriod, nextPeriod }
}
