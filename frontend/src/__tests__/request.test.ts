import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/api/request'

/**
 * SPEC-P136 —— 403 的全局处理（AT-136-8 / G-4）。
 *
 * <p>403 是**预期内的业务结果**（当前账号没有该操作权限），不是系统故障 ⇒
 * 必须用对话框让用户确认知悉，而不是红色 toast（暗示「系统出错了」）。
 */

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn(), info: vi.fn() },
  ElMessageBox: { alert: vi.fn().mockResolvedValue(undefined) },
}))

const ROOT = resolve(__dirname, '../..')

/** axios 的拦截器栈是运行时结构，类型未公开 ⇒ 取 rejected 处理器直接驱动。 */
const rejected = (request.interceptors.response as any).handlers[0].rejected as (
  e: any,
) => Promise<any>

const forbidden = () => ({
  response: { status: 403, data: { code: 403, msg: '权限不足：该操作需要权限 voucher:audit' } },
})

let clock = 0

describe('SPEC-P136 request.ts 403 处理', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    // 请求拦截器内有 800ms 的弹窗合并窗口（模块级时间戳）⇒ 用单调递增的假时钟
    // 保证每个用例都落在窗口之外，否则会出现「测试互相吞掉弹窗」的顺序依赖。
    vi.useFakeTimers()
    clock += 5000
    vi.setSystemTime(new Date(2026, 9, 19, 0, 0, 0).getTime() + clock)
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('AT-136-8 forbiddenShowsDialogNotToast：403 → 弹对话框，且不再弹红色 toast', async () => {
    const err = forbidden()
    await expect(rejected(err)).rejects.toBe(err)

    expect(ElMessageBox.alert).toHaveBeenCalledTimes(1)
    expect(ElMessage.error).not.toHaveBeenCalled()
    // 文案要能指导用户（说明是权限问题），而不是「请求失败」这类无行动指引的话
    expect(String(vi.mocked(ElMessageBox.alert).mock.calls[0][0])).toContain('权限')
  })

  it('并发/连续 403 只弹一次（800ms 合并窗口）', async () => {
    const err = forbidden()
    await rejected(err).catch(() => undefined)
    await rejected(err).catch(() => undefined)
    expect(ElMessageBox.alert).toHaveBeenCalledTimes(1)
  })

  it('G-4：src/api/request.ts 的 403 分支不得出现 ElMessage.error（源码断言）', () => {
    const src = readFileSync(resolve(ROOT, 'src/api/request.ts'), 'utf-8')
    const start = src.indexOf('if (status === 403)')
    expect(start).toBeGreaterThanOrEqual(0)
    const end = src.indexOf('return Promise.reject(error)', start)
    expect(end).toBeGreaterThan(start)
    const branch = src.slice(start, end)
    expect(branch).not.toContain('ElMessage.error')
    expect(branch).toContain('showForbiddenDialog')
  })
})
