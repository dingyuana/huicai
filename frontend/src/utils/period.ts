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
