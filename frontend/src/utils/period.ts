import dayjs from 'dayjs'
import { getCurrentPeriod } from '@/api/modules/agency'

/** 期间格式 YYYYMM */
const PERIOD_RE = /^\d{6}$/

/** 上一期间。期间格式非法时返回 null（调用方据此隐藏对比列）。
 *  用原生 Date 构造，规避 dayjs 未启用 customParseFormat 插件时
 *  `dayjs('20260901','YYYYMMDD')` 解析不可靠的问题。 */
export function prevPeriod(period: string): string | null {
  if (!PERIOD_RE.test(period)) return null
  const year = Number(period.slice(0, 4))
  const month = Number(period.slice(4, 6))
  if (month < 1 || month > 12) return null
  return month === 1 ? `${year - 1}12` : `${year}${String(month - 1).padStart(2, '0')}`
}

/** 年初期间（同年份的 01 月）。期间格式非法返回 null */
export function yearStartPeriod(period: string): string | null {
  if (!PERIOD_RE.test(period)) return null
  return period.slice(0, 4) + '01'
}

/**
 * 期间 → 该月首末日（YYYY-MM-DD 闭区间）。格式非法返回 null。
 *
 * <p>SPEC-P151：不存 period 列的单据表（如 {@code t_bank_statement} 只有 {@code tx_date}）
 * 要「只看当前账期」，必须由期间算出日期区间。口径与后端
 * {@code PeriodRange}（YearMonth.atDay(1)/atEndOfMonth）严格一致，
 * 否则前端显示「本月」、后端按别的区间查，是又一处两端漂移。</p>
 */
export function periodRange(period: string | null | undefined): { start: string; end: string } | null {
  if (!period || !PERIOD_RE.test(period)) return null
  const year = Number(period.slice(0, 4))
  const month = Number(period.slice(4, 6))
  if (month < 1 || month > 12) return null
  const mm = String(month).padStart(2, '0')
  // 下月第 0 天 = 本月最后一天；用原生 Date，与 dayjs 插件配置无关
  const lastDay = new Date(year, month, 0).getDate()
  return { start: `${year}-${mm}-01`, end: `${year}-${mm}-${String(lastDay).padStart(2, '0')}` }
}

/**
 * 获取企业默认期间（P57）：优先取企业当前期间，失败回退当前月。
 * 用于替代各页面写死 dayjs().format('YYYYMM') 的默认期间。
 */
export async function resolveDefaultPeriod(): Promise<string> {
  try {
    const vo = await getCurrentPeriod()
    if (vo && vo.currentPeriod) {
      return vo.currentPeriod
    }
  } catch (e) {
    // 未切换企业/接口异常时回退当前月，保证页面可用
  }
  return dayjs().format('YYYYMM')
}

/**
 * 获取企业最早未完成结账的期间（报表默认期间）。
 * 回退策略：接口异常或该字段为空 -> resolveDefaultPeriod()。
 */
export async function resolveEarliestUnclosedPeriod(): Promise<string> {
  try {
    const vo = await getCurrentPeriod()
    if (vo && vo.earliestUnclosedPeriod) {
      return vo.earliestUnclosedPeriod
    }
  } catch (e) {
    // 未切换企业/接口异常时按默认期间回退
  }
  return resolveDefaultPeriod()
}

/**
 * 获取企业最近已结账的期间（利润表等期间报表默认期间）。
 * 回退策略：无已结账期间或接口异常 -> resolveDefaultPeriod()。
 */
export async function resolveLatestClosedPeriod(): Promise<string> {
  try {
    const vo = await getCurrentPeriod()
    if (vo && vo.latestClosedPeriod) {
      return vo.latestClosedPeriod
    }
  } catch (e) {
    // 未切换企业/接口异常时按默认期间回退
  }
  return resolveDefaultPeriod()
}
