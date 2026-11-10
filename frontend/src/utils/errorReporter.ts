/**
 * SPEC-P152 前端错误捕捉与上报。
 *
 * 覆盖四类此前完全不可见的错误：
 *  1. Vue 渲染/生命周期异常（app.config.errorHandler）
 *  2. 脚本运行时错误与资源加载失败（window 'error'）
 *  3. 未处理的 Promise rejection（'unhandledrejection'）
 *  4. 路由懒加载 chunk 失败（router.onError）
 * 另外 request.ts 对 5xx/网络中断调用 reportHttpError（4xx 不上报——
 * 服务端已有自身日志与审计，前端重复上报只会灌库）。
 *
 * 防护设计（对「上报自身制造故障」的对冲）：
 *  - 原生 fetch + keepalive：不走 axios 拦截器，避免「上报失败→再上报」递归；
 *  - 去重：同 message+stack 前缀 60s 内只报一次（渲染循环里的错误会每帧触发）；
 *  - 限流：每分钟发送上限，超出丢弃；
 *  - 队列封顶：发送失败暂存≤50 条，满则丢最旧；
 *  - 一切异常静默（console.debug），绝不影响页面功能。
 */
import type { App } from 'vue'
import type { Router } from 'vue-router'

const REPORT_URL = '/api/v1/system/audit-log/client'
const DEDUP_WINDOW_MS = 60_000
const RATE_LIMIT_PER_MIN = 30
const FLUSH_INTERVAL_MS = 10_000
const FLUSH_BATCH_SIZE = 5
const QUEUE_MAX = 50

export interface ClientLogPayload {
  level: 'ERROR' | 'WARN'
  message: string
  stack?: string
  url: string
}

interface QueueItem extends ClientLogPayload {}

const queue: QueueItem[] = []
const seen = new Map<string, number>() // dedupKey -> 首次出现时间
let rateWindow = Math.floor(Date.now() / 60_000)
let rateCount = 0
let flushTimer: ReturnType<typeof setInterval> | null = null

/** 包一层 try/catch：上报器自身的任何异常都不允许外溢 */
function safe(fn: () => void) {
  try { fn() } catch (e) { console.debug('[errorReporter]', e) }
}

function shouldSend(item: ClientLogPayload): boolean {
  const now = Date.now()
  const window = Math.floor(now / 60_000)
  if (window !== rateWindow) { rateWindow = window; rateCount = 0 }
  if (rateCount >= RATE_LIMIT_PER_MIN) return false

  const key = item.message.slice(0, 200) + '|' + (item.stack || '').slice(0, 200)
  const firstSeen = seen.get(key)
  if (firstSeen !== undefined && now - firstSeen < DEDUP_WINDOW_MS) return false
  seen.set(key, now)
  // 清陈旧键，防 Map 无界增长
  if (seen.size > 500) {
    for (const [k, t] of seen) if (now - t >= DEDUP_WINDOW_MS) seen.delete(k)
  }
  return true
}

async function send(item: ClientLogPayload) {
  try {
    await fetch(REPORT_URL, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      // 服务端接口为单条入参；keepalive 让页面卸载时最后一批仍能送达
      body: JSON.stringify(item),
      keepalive: true,
    })
  } catch (e) {
    console.debug('[errorReporter] report failed', e)
  }
}

function flush() {
  safe(() => {
    if (!queue.length) return
    const batch = queue.splice(0, FLUSH_BATCH_SIZE)
    // 服务端接口单条入参：逐条发送（批次小，且单条失败不影响其余）
    batch.forEach((item) => {
      if (shouldSend(item)) { rateCount++; void send(item) }
    })
  })
}

export function reportError(payload: ClientLogPayload) {
  safe(() => {
    if (queue.length >= QUEUE_MAX) queue.shift() // 满则丢最旧
    queue.push(payload)
    if (queue.length >= FLUSH_BATCH_SIZE) flush()
  })
}

function stackOf(err: unknown): string | undefined {
  if (err instanceof Error) return err.stack
  if (typeof err === 'string') return undefined
  try { return JSON.stringify(err)?.slice(0, 2000) } catch { return undefined }
}

function messageOf(err: unknown): string {
  if (err instanceof Error) return err.message || err.name
  return String(err).slice(0, 500)
}

export function installErrorReporter(app: App, router?: Router) {
  // 1) Vue 组件树内错误（渲染、生命周期、watcher）
  app.config.errorHandler = (err, _instance, info) => {
    reportError({
      level: 'ERROR',
      message: `[vue:${info}] ${messageOf(err)}`,
      stack: stackOf(err),
      url: location.href,
    })
  }

  // 2) 脚本错误 + 资源加载失败。
  // 🔴 必须用**捕获阶段**（第 3 参 true）：img/script 的 error 事件不冒泡，
  // 只在捕获阶段能收到；否则资源加载失败（发版后 CDN 404 等）完全不可见。
  window.addEventListener('error', (e) => {
    const el = e.target as any
    // 资源错误的 target 是元素本身；脚本错误的 target 是 window（部分实现为 document）。
    // 判据取「是有 tagName 的元素且非 window/document」，两实现下都稳。
    if (el && el !== window && el !== document && typeof el.tagName === 'string') {
      const src = el.src || ''
      reportError({
        level: 'WARN',
        message: `[resource] ${el.tagName.toLowerCase()} 加载失败 ${src}`.slice(0, 500),
        url: location.href,
      })
      return
    }
    reportError({
      level: 'ERROR',
      message: e.message || 'window.onerror',
      stack: e.error ? stackOf(e.error) : `${e.filename}:${e.lineno}:${e.colno}`,
      url: location.href,
    })
  }, true)

  // 3) 未处理的 Promise rejection（async 代码里最常见的静默失败）
  window.addEventListener('unhandledrejection', (e) => {
    reportError({
      level: 'ERROR',
      message: `[unhandledrejection] ${messageOf(e.reason)}`,
      stack: stackOf(e.reason),
      url: location.href,
    })
  })

  // 4) 路由错误（典型：发版后旧 chunk 404）
  router?.onError((err) => {
    reportError({
      level: 'ERROR',
      message: `[router] ${messageOf(err)}`,
      stack: stackOf(err),
      url: location.href,
    })
  })

  // 批量发送定时器 + 页面隐藏时兜底 flush
  if (flushTimer === null) {
    flushTimer = setInterval(flush, FLUSH_INTERVAL_MS)
  }
  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'hidden') flush()
  })
}

/** 测试/SSR 复位用 */
export function __resetReporter() {
  queue.length = 0
  seen.clear()
  rateCount = 0
  if (flushTimer !== null) { clearInterval(flushTimer); flushTimer = null }
}

/** 供测试注入一条并强制 flush */
export function __flushForTest() { flush() }
