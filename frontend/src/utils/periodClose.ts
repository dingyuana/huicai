/**
 * SPEC-P138 —— 结账工作台准入判定（纯函数）。
 *
 * <p>缺陷背景：原 `canClose` 要求 `allVouchers.length > 0`，而三步结转全 SKIPPED 时
 * 该数组为空（P137 语义：无数据属正常业务跳过）⇒ 空数据账套永远无法结账。</p>
 *
 * <p>现口径：后端检查通过 + 本次会话已执行结转序列 + 无 FAILED 步 + 已生成凭证全部记账。
 * 0 张凭证（全跳过）视为满足；后端 `checkBeforeClose` 仍是最终权威。</p>
 *
 * <h3>SPEC-P149 增补：已结账期间不得重复结转</h3>
 * <p>用户报障「期间已结账，结账前检查仍显示通过并允许继续走结转序列」。
 * 已结账期间的正确动作是**反结账**，重复结转会在已锁定期间上再生成一套结转凭证。
 * 故新增 {@link isPeriodAlreadyClosed} 与 {@link alreadyClosedNotice}，
 * 由工作台在**执行检查前**拦截并给出可执行指引。</p>
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

/**
 * 期间是否已结账（SPEC-P149）。
 *
 * <p>🔴 只认 `closed` 这一个值。后端 `t_period.status` 实测取值为
 * `open` / `closed`（大小写固定），故**不做模糊匹配**——模糊匹配会把
 * 未知状态误判成“已结账”而阻断正常结账，属更坏的失败模式。</p>
 */
export function isPeriodAlreadyClosed(status: string | null | undefined): boolean {
  return (status ?? '').trim().toLowerCase() === 'closed'
}

/**
 * 已结账期间的提示文案（SPEC-P149 AT-149-2）。
 *
 * <p>必须同时讲清三件事：①已结账 ②不能重复结转 ③只能反结账。
 * 只写“已结账”而不给出口，用户仍会反复点结账。</p>
 */
export function alreadyClosedNotice(period: string | null | undefined): string {
  const p = period ? `期间 ${period} ` : '该期间'
  return `${p}已结账, 不能重复结转。如需修改本期数据, 请先执行「反结账」。`
}

/**
 * 期间状态中文文案（SPEC-P150）。
 *
 * <p>列表页与提示语共用同一份口径：此前文案散落在组件里，
 * 新增「为什么按钮不可用」提示时必然出现「状态叫法不一致」。</p>
 */
export function periodStatusLabel(status: string | null | undefined): string {
  const s = (status ?? '').trim().toLowerCase()
  if (s === 'open') return '开启'
  if (s === 'closed') return '已结账'
  if (s === 'locked') return '已锁定'
  return s ? `未知(${status})` : '未知'
}

/**
 * 「反结账」为何不可点击 + 怎么才能点（SPEC-P150）。
 *
 * <p>🔴 缺陷背景：按钮原本只在 {@code status === 'closed'} 时才渲染，
 * 于是未结账（或结账因后端静默失败而未生效）的期间<b>整颗按钮消失</b>，
 * 用户只能报「反结账按钮没了」，无从知晓是「不该结账」还是「结账没成功」。
 * 现在按钮常驻，不可用时置灰并给出原因与出路（§4.5「为什么 + 怎么才能点」）。</p>
 */
export function reopenBlockedHint(status: string | null | undefined): string {
  const label = periodStatusLabel(status)
  const s = (status ?? '').trim().toLowerCase()
  if (s === 'locked') {
    return `当前状态：${label}。已锁定期间不能直接反结账，请先解锁该期间。`
  }
  return `仅「已结账」期间可反结账，当前状态：${label}。请先在「期末结账」工作台完成结账。`
}
