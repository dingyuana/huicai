/**
 * SPEC-P138 —— 结账工作台准入判定（纯函数）。
 *
 * <p>缺陷背景：原 `canClose` 要求 `allVouchers.length > 0`，而三步结转全 SKIPPED 时
 * 该数组为空（P137 语义：无数据属正常业务跳过）⇒ 空数据账套永远无法结账。</p>
 *
 * <p>现口径：后端检查通过 + 本次会话已执行结转序列 + 无 FAILED 步 + 已生成凭证全部记账。
 * 0 张凭证（全跳过）视为满足；后端 `checkBeforeClose` 仍是最终权威。</p>
 */

export interface CloseVoucherLike {
  status: string
}

export function canClosePeriod(
  checkPassed: boolean | undefined,
  sequenceRan: boolean,
  vouchers: ReadonlyArray<CloseVoucherLike>,
): boolean {
  if (checkPassed !== true) return false
  if (!sequenceRan) return false
  return vouchers.every(v => v.status === 'POSTED')
}

export function closeHint(canClose: boolean, voucherCount: number): string {
  if (!canClose) return '仍有未记账凭证或检查未通过, 结账将被拦截'
  if (voucherCount === 0) return '本期无结转凭证需生成, 结账检查已通过, 可执行结账'
  return '全部结转凭证已记账, 可执行结账'
}
