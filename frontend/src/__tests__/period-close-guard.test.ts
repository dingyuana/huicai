import { describe, it, expect } from 'vitest'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { canClosePeriod, closeHint } from '@/utils/periodClose'

/**
 * SPEC-P138 —— 结账准入守卫（AT-138-1 ~ AT-138-6）。
 *
 * <p>缺陷背景：`canClose` 曾要求 `allVouchers.length > 0`，而三步结转全 SKIPPED 时
 * 该数组为空 ⇒ 空数据账套**永远无法结账**，与 P137「无数据 = 正常业务跳过」矛盾。</p>
 *
 * <p>本守卫锁定两件事：
 * <ol>
 *   <li><b>行为</b>：0 张结转凭证必须允许结账；未执行序列 / 检查未通过 / 存在未记账凭证必须拦截；</li>
 *   <li><b>防回退</b>：组件必须经纯函数判定，禁止把 `length > 0` 条件写回内联表达式。</li>
 * </ol>
 */
describe('SPEC-P138 结账准入', () => {
  it('allSkippedAllowsClose', () => {
    // Given 检查通过、序列已执行、三步全 SKIPPED（0 张凭证）
    // When 计算结账准入
    // Then 必须允许结账
    expect(canClosePeriod(true, true, [])).toBe(true)
  })

  it('checkFailedBlocksClose', () => {
    expect(canClosePeriod(false, true, [])).toBe(false)
    expect(canClosePeriod(undefined, true, [])).toBe(false)
  })

  it('sequenceNotRunBlocksClose', () => {
    // 保留原意图：不允许跳过第 2 步（生成结转序列）直接结账
    expect(canClosePeriod(true, false, [])).toBe(false)
  })

  it('unpostedVoucherBlocksClose', () => {
    expect(canClosePeriod(true, true, [{ status: 'DRAFT' }, { status: 'POSTED' }])).toBe(false)
    expect(canClosePeriod(true, true, [{ status: 'POSTED' }, { status: 'POSTED' }])).toBe(true)
  })

  it('closeHintBranches', () => {
    expect(closeHint(false, 0)).toContain('仍有未记账凭证')
    expect(closeHint(true, 0)).toContain('无结转凭证需生成')
    expect(closeHint(true, 2)).toContain('全部结转凭证已记账')
  })

  it('componentUsesHelperGuard', () => {
    const src = readFileSync(
      resolve(__dirname, '../views/finance/period-close/PeriodCloseView.vue'),
      'utf8',
    )
    expect(src).toContain('canClosePeriod')
    expect(src).not.toMatch(/allVouchers\.value\.length\s*>\s*0/)
  })
})
