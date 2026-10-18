import request from '@/api/request'

/**
 * SPEC-P129 §1②：企业「主数据就绪度」探测。
 *
 * <p>就绪度是<b>派生事实</b>（由「有没有」算出），刻意不落库 —— 一旦固化成列或状态，
 * 就会与实际配置漂移（删掉最后一个银行账号后列里仍是 1，永远显示「已配置」）。
 * P128 的 enterprise_id=0 死数据正是这类固化的恶果。
 */
export interface EnterpriseReadiness {
  enterpriseId: number
  enterpriseName: string
  /** 基本信息已完成（建档即 true） */
  basicInfoDone: boolean
  /** 是否有启用的银行账号 —— 影响收款单/付款单/银行对账/自动制证 */
  bankAccount: boolean
  /** 是否已配置凭证类型 —— 影响凭证生成 */
  voucherType: boolean
}

export function getReadiness(enterpriseId: number) {
  return request.get('/v1/enterprise/readiness', {
    params: { enterpriseId },
  }) as Promise<EnterpriseReadiness>
}

/** 该企业是否已有启用的银行账号（失败按「未配置」处理：面板是引导，不该因此报错） */
export async function hasBankAccount(enterpriseId: number): Promise<boolean> {
  try {
    const r = await getReadiness(enterpriseId)
    return !!r?.bankAccount
  } catch {
    return false
  }
}

/** 该企业是否已配置凭证类型 */
export async function hasVoucherType(enterpriseId: number): Promise<boolean> {
  try {
    const r = await getReadiness(enterpriseId)
    return !!r?.voucherType
  } catch {
    return false
  }
}
