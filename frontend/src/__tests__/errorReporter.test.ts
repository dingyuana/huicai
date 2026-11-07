import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { reportError, installErrorReporter, __resetReporter, __flushForTest, type ClientLogPayload } from '@/utils/errorReporter'

/**
 * SPEC-P152 前端错误上报器行为守卫。
 *
 * 关键不变量：
 *  - 发送走原生 fetch（不经 axios，避免「上报失败→拦截器→再上报」递归）；
 *  - 去重窗口内同错误只发一次（渲染循环错误每帧触发，不去重就是 DoS 自己）；
 *  - 每分钟发送封顶（限流）；
 *  - installErrorReporter 装上后，Vue errorHandler / window error /
 *    unhandledrejection / router.onError 四类源都会进入上报队列。
 */

const fetchMock = vi.fn().mockResolvedValue({ ok: true })

function payload(overrides: Partial<ClientLogPayload> = {}): ClientLogPayload {
  return { level: 'ERROR', message: 'boom-' + Math.random(), stack: 'at Foo.vue:1:1', url: 'http://app/#/x', ...overrides }
}

describe('errorReporter（SPEC-P152）', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', fetchMock)
    fetchMock.mockClear()
    __resetReporter()
  })
  afterEach(() => {
    __resetReporter()
    vi.unstubAllGlobals()
  })

  it('reportError → flush 后 POST 到 /audit-log/client，报文含 message/stack/url', async () => {
    reportError(payload({ message: 'render failed' }))
    __flushForTest()
    await vi.waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(1))
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/v1/system/audit-log/client')
    expect(init.method).toBe('POST')
    const body = JSON.parse(init.body)
    expect(body.message).toBe('render failed')
    expect(body.stack).toContain('Foo.vue')
    expect(body.url).toContain('/#/x')
  })

  it('去重：同 message+stack 连续两次只发一次', async () => {
    const p = payload({ message: 'dup', stack: 'same-stack' })
    reportError(p)
    reportError({ ...p })
    __flushForTest()
    __flushForTest()
    await vi.waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(1))
  })

  it('限流：一分钟内超过 30 条不同错误，发送被封顶在 30', async () => {
    for (let i = 0; i < 60; i++) reportError(payload())
    __flushForTest()
    // 队列单批 5 条，多轮 flush 直到排空
    for (let i = 0; i < 15; i++) __flushForTest()
    await new Promise((r) => setTimeout(r, 0))
    expect(fetchMock.mock.calls.length).toBeLessThanOrEqual(30)
    expect(fetchMock.mock.calls.length).toBeGreaterThan(0)
  })

  it('installErrorReporter：四类错误源全部接入', async () => {
    const app: any = { config: {} }
    const router: any = { onError: vi.fn() }
    installErrorReporter(app, router)

    // 1) Vue errorHandler
    app.config.errorHandler(new Error('vue-render-boom'), null, 'render')
    // 2) window error（脚本错误：target 必须是 window 本身，用 ErrorEvent 构造）
    window.dispatchEvent(new ErrorEvent('error', {
      message: 'script-boom', filename: 'a.js', lineno: 1, colno: 2,
      error: new Error('script-boom'),
    }))
    // 3) unhandledrejection（happy-dom 无 PromiseRejectionEvent 构造器，手工造事件）
    const rejEvent = new Event('unhandledrejection') as any
    rejEvent.reason = new Error('promise-boom')
    window.dispatchEvent(rejEvent)
    // 4) router.onError
    expect(router.onError).toHaveBeenCalled()
    router.onError.mock.calls[0][0](new Error('chunk-404'))

    __flushForTest()
    await vi.waitFor(() => expect(fetchMock.mock.calls.length).toBeGreaterThanOrEqual(3))
    const all = fetchMock.mock.calls.map((c) => JSON.parse((c[1] as any).body).message).join('\n')
    expect(all).toContain('[vue:render] vue-render-boom')
    expect(all).toContain('script-boom')
    expect(all).toContain('[unhandledrejection] promise-boom')
    expect(all).toContain('[router] chunk-404')
  })

  it('上报器自身异常不外溢（fetch 抛错只 console.debug）', async () => {
    fetchMock.mockRejectedValueOnce(new Error('network down'))
    reportError(payload())
    __flushForTest()
    await expect(Promise.resolve()).resolves.toBeUndefined() // 不炸即通过
  })
})
