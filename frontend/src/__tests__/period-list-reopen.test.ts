/**
 * @vitest-environment node
 *
 * SPEC-P150 —— 「反结账」入口不可见的防回归守卫。
 *
 * <p><b>缺陷背景（2026-11-02 生产实证）</b>：用户报「/basis/period 没有反结账按键」。
 * 真实根因在后端（`PeriodController` 调 `IService#getById` 这个 default 方法，
 * 无事务 ⇒ GUC 不设 ⇒ RLS 过滤 ⇒ 返回 null ⇒ 结账静默 200 不生效），
 * 但<b>前端把结论藏了起来</b>：按钮只在 `status === 'closed'` 时渲染，
 * 于是「结账没成功」的表现是「按钮消失了」，用户无从判断是哪种情况。</p>
 *
 * <p>本守卫锁定两点：
 * <ol>
 *   <li>按钮<b>常驻</b>：非已结账期间渲染禁用态（`period-reopen-disabled`），
 *       并给出「为什么不可用 + 怎么才能用」；</li>
 *   <li>状态文案与判定<b>收口到 utils</b>，禁止再写回内联比较表达式。</li>
 * </ol>
 *
 * ⚠️ 环境必须是 node：本文件读源码（`node:fs`），happy-dom 会把内置模块 externalize
 * 成空对象（详见 period-close-guard.test.ts 的同类登记）。
 */
import { describe, it, expect } from 'vitest'
import { readFileSync } from 'node:fs'

import {
  isPeriodAlreadyClosed,
  periodStatusLabel,
  reopenBlockedHint,
} from '@/utils/periodClose'

const VIEW_PATH = `${process.cwd()}/src/views/system/period/PeriodList.vue`

function viewSource(): string {
  return readFileSync(VIEW_PATH, 'utf8')
}

describe('SPEC-P150 反结账入口', () => {
  it('AT-150-1 不可反结账时必须同时给出原因与出路', () => {
    const open = reopenBlockedHint('open')
    expect(open).toContain('仅「已结账」期间可反结账')
    expect(open).toContain('当前状态：开启')
    // 只讲原因不讲出路 = 用户还是会反复找按钮
    expect(open).toContain('期末结账')

    const locked = reopenBlockedHint('locked')
    expect(locked).toContain('已锁定')
    expect(locked).toContain('解锁')
  })

  it('AT-150-2 状态文案口径统一，未知状态保留原值便于排查', () => {
    expect(periodStatusLabel('open')).toBe('开启')
    expect(periodStatusLabel('closed')).toBe('已结账')
    expect(periodStatusLabel('locked')).toBe('已锁定')
    expect(periodStatusLabel(' closed ')).toBe('已结账')
    // 未知值不得静默说成「已锁定」——那会把人引向错误的处理动作
    expect(periodStatusLabel('pending')).toContain('pending')
    expect(periodStatusLabel(null)).toBe('未知')
  })

  it('AT-150-3 可反结账的判据与后端 reopenPeriod 同口径（只认 closed）', () => {
    expect(isPeriodAlreadyClosed('closed')).toBe(true)
    expect(isPeriodAlreadyClosed(' CLOSED ')).toBe(true)
    expect(isPeriodAlreadyClosed('open')).toBe(false)
    expect(isPeriodAlreadyClosed('locked')).toBe(false)
    expect(isPeriodAlreadyClosed(null)).toBe(false)
  })

  it('AT-150-4 列表页必须常驻渲染按钮，且不得把条件写回内联表达式', () => {
    const src = viewSource()

    // 自证：读到的确实是会计期间列表页
    expect(src).toContain('会计期间管理')

    // 可用态 + 禁用态都必须存在
    expect(src).toContain('data-test="period-reopen"')
    expect(src).toContain('data-test="period-reopen-disabled"')
    expect(src).toContain('disabled')
    // 禁用态必须带得动 tooltip（Element Plus disabled 按钮不派发事件，需要包一层）
    expect(src).toContain('disabled-action-wrap')
    expect(src).toContain('reopenBlockedHint(')

    // 防回退：按钮不得再整体消失
    expect(src).not.toContain('v-if="row.status === \'closed\'"')
  })
})
