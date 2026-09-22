import dayjs from 'dayjs'
import { getCurrentPeriod } from '@/api/modules/agency'

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
