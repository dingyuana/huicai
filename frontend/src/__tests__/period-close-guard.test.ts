/**
 * @vitest-environment node
 *
 * 🔴 本文件是**源码扫描型**守卫（读 .vue/.ts 源文件做断言），依赖 `node:fs` / `node:path`。
 * 全局 `environment: 'happy-dom'` 会把 node 内置模块 externalize 成空对象，
 * 实测 `resolve is not a function` / `readFileSync is not a function` ⇒ 整文件挂掉。
 * 逐文件切到 node 环境，既修复本文件，也不影响全局配置与其他组件测试。
 */
import { describe, it, expect } from 'vitest'
import { readFileSync } from 'node:fs'

import { canClosePeriod, closeHint, isPeriodAlreadyClosed, alreadyClosedNotice } from '@/utils/periodClose'

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
/**
 * 🔴 路径解析踩坑登记（实测两次）：
 * ①happy-dom 环境下 ESM 没有 `__dirname`；
 * ②`node:path` 的 `resolve`、`node:url` 的 `fileURLToPath` 被 vitest 模块解析
 *   拦截后**不是函数**（`resolve is not a function` / `fileURLToPath is not a function`）。
 * 故只保留可用的 `readFileSync`，路径以 `process.cwd()`（vitest 在 frontend/ 下运行）为基准拼接。
 */
const VIEW_PATH = `${process.cwd()}/src/views/finance/period-close/PeriodCloseView.vue`

function viewSource(): string {
  return readFileSync(VIEW_PATH, 'utf8')
}

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

  // ===== SPEC-P149：已结账期间不得重复结转 =====

  it('AT-149-1 alreadyClosedDetection', () => {
    // Given 期间状态来自后端 t_period.status
    // When 判定是否已结账
    // Then 只认 closed，且大小写/空白容错；未知值一律视为未结账
    expect(isPeriodAlreadyClosed('closed')).toBe(true)
    expect(isPeriodAlreadyClosed(' CLOSED ')).toBe(true)
    expect(isPeriodAlreadyClosed('open')).toBe(false)
    expect(isPeriodAlreadyClosed('')).toBe(false)
    expect(isPeriodAlreadyClosed(null)).toBe(false)
    expect(isPeriodAlreadyClosed(undefined)).toBe(false)
    // 🔴 反证：未知状态不得误判为已结账（否则正常结账被永久阻断）
    expect(isPeriodAlreadyClosed('pending')).toBe(false)
    expect(isPeriodAlreadyClosed('closing')).toBe(false)
  })

  it('AT-149-2 noticeExplainsAllThreeFacts', () => {
    // 提示必须同时讲清：①已结账 ②不能重复结转 ③只能反结账
    const text = alreadyClosedNotice('202407')
    expect(text).toContain('202407')
    expect(text).toContain('已结账')
    expect(text).toContain('不能重复结转')
    expect(text).toContain('反结账')
  })

  it('AT-149-3 checkBlockedBeforeRequest', () => {
    const src = viewSource()
    // 已结账判断必须出现在 checkClose 调用之前（不发请求就拦截）
    const checkFn = src.slice(src.indexOf('async function onCheck'))
    const guardIdx = checkFn.indexOf('alreadyClosed.value')
    const apiIdx = checkFn.indexOf('await checkClose(')
    expect(guardIdx).toBeGreaterThan(-1)
    expect(apiIdx).toBeGreaterThan(-1)
    expect(guardIdx).toBeLessThan(apiIdx)
  })

  it('AT-149-4 closeAlsoGuarded', () => {
    // 纵深防御：绕过检查阶段直达结账也必须被拦
    const src = viewSource()
    const closeFn = src.slice(src.indexOf('async function onClose'))
    expect(closeFn).toContain('alreadyClosed.value')
  })

  it('AT-149-5 dialogOffersReopenAndRefreshesStatus', () => {
    const src = viewSource()
    // 弹窗必须给出可执行出口（去反结账），而非只报错
    expect(src).toContain('去反结账')
    // 反结账成功后必须刷新期间状态，否则页头标签停留在「已结账」
    const reopenFn = src.slice(src.indexOf('async function onReopen'))
    expect(reopenFn).toContain('refreshPeriodStatus')
  })

  it('AT-149-6 visibleAlertPresent', () => {
    const src = viewSource()
    // 首屏必须有可见警示条（不是只在点击后才弹）
    expect(src).toContain('already-closed-alert')
    expect(src).toMatch(/v-if="alreadyClosed"/)
  })

  it('componentUsesHelperGuard', () => {
    const src = viewSource()
    expect(src).toContain('canClosePeriod')
    expect(src).not.toMatch(/allVouchers\.value\.length\s*>\s*0/)
  })
})
