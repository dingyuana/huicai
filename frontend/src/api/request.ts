import axios from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { ApiResponse } from '@/types/api'

const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 30000,
  headers: { 'Content-Type': 'application/json' },
})

// 请求拦截器：注入 JWT
//
// SPEC-P124 切片5：<b>不再注入 X-Enterprise-Id</b>。
// 该头曾使「当前企业」有三个来源且其中之一由客户端可控（3 源 2 义 0 权威）。
// 现在切换由 POST /v1/enterprise/switch 校验并<b>重签 token</b>，
// 企业上下文只来自 JWT，服务端为唯一权威。
request.interceptors.request.use(
  (requestConfig) => {
  const config = requestConfig
  const token = localStorage.getItem('huicai_token')
  if (token) {
    config.headers['Authorization'] = `Bearer ${token}`
  }
  return config
})

/**
 * SPEC-P124 切片1-b：access token 过期时用 refresh token 自动续期并重放原请求。
 *
 * <p><b>为什么必须有</b>：登录一直会签发 refresh token，但此前**没有任何端点能兑换它**，
 * 前端 401 只清 localStorage 直接登出 —— 「缩短 access token 有效期」因此无法落地。
 * 后端 POST /api/v1/auth/refresh 已实现（切片1），本条补上前端消费侧。
 *
 * <p><b>并发去重</b>：页面初始化常并发多个请求，同时 401 会触发多次 refresh ——
 * 每次都换出新 token，后到的响应可能带着<b>已被换掉的旧 token</b> 覆盖回去，
 * 形成「续期后立刻又失效」的抖动。故用共享 promise 保证同一时刻只有一次 refresh。
 *
 * <p><b>防止无限递归</b>：重放时打 {@code _retried} 标记；再次 401 说明 refresh 本身
 * 已不可用，直接登出。
 *
 * <p><b>为何用合成 storage 事件回灌 store</b>：auth.store 内部 token 是内存态，
 * 而本模块直接读 localStorage。dispatch 一个合成 storage 事件即可复用 store 里
 * 已有的跨标签页监听器，避免 request.ts ↔ auth.store 形成循环依赖。
 */
let refreshing: Promise<string> | null = null

function doRefresh(): Promise<string> {
  if (!refreshing) {
    refreshing = request
      .post<{ token?: string }>('/v1/auth/refresh', {
        refreshToken: localStorage.getItem('huicai_refresh_token'),
      })
      // ⚠️ 响应拦截器运行期已返回 body.data（后端 R 结构解包），
      //    但 axios 的 TS 类型仍标注为 AxiosResponse<T>，故按运行期形态断言。
      .then((res) => {
        const t = (res as unknown as { token?: string })?.token
        if (!t) throw new Error('refresh 未返回新 token')
        localStorage.setItem('huicai_token', t)
        window.dispatchEvent(new StorageEvent('storage', { key: 'huicai_token', newValue: t }))
        return t
      })
      .finally(() => { refreshing = null })
  }
  return refreshing
}

function logoutHard() {
  localStorage.removeItem('huicai_token')
  localStorage.removeItem('huicai_refresh_token')
  localStorage.removeItem('huicai_current_enterprise_id')
  window.dispatchEvent(new StorageEvent('storage', { key: 'huicai_token', newValue: null }))
  window.location.href = '/login'
}

async function handleUnauthorized(error: unknown) {
  const cfg = (error as { config?: Record<string, unknown> })?.config
  const alreadyRetried = cfg?.__retried === true

  if (alreadyRetried || !localStorage.getItem('huicai_refresh_token')) {
    logoutHard()
    return Promise.reject(error)
  }

  try {
    const newToken = await doRefresh()
    if (cfg) {
      cfg.__retried = true
      const headers = (cfg.headers ?? {}) as Record<string, string>
      cfg.headers = { ...headers, Authorization: `Bearer ${newToken}` }
      return request(cfg)
    }
    return Promise.reject(error)
  } catch (e) {
    logoutHard()
    return Promise.reject(error)
  }
}

// 响应拦截器：统一错误处理
/**
 * SPEC-P136：403 是**预期内的业务结果**（当前账号没有该操作权限），不是系统故障。
 *
 * <p>故用对话框（需要用户确认知悉）而非红色 toast（暗示出了错）。
 *
 * <p><b>去重是必需的</b>：页面初始化常并发多个请求，一次操作可能同时触发多条 403；
 * 若每条都弹对话框，用户要点很多次「知道了」。用时间窗内只弹一次的守卫，
 * 并把后续的合并掉。窗口取 800ms —— 足够覆盖并发请求，又不会把
 * 「过一会儿再点另一个无权限按钮」也吞掉。
 */
let forbiddenDialogAt = 0
const FORBIDDEN_DIALOG_WINDOW_MS = 800

function showForbiddenDialog() {
  const now = Date.now()
  if (now - forbiddenDialogAt < FORBIDDEN_DIALOG_WINDOW_MS) return
  forbiddenDialogAt = now
  ElMessageBox.alert(
    '当前账号缺少该操作权限，请联系管理员开通。',
    '权限不足',
    { type: 'warning' },
  ).catch(() => undefined)
}

request.interceptors.response.use(
  (response) => {
    // P89-D：文件下载（Excel 导出）直接返回 blob，不走 R 结构解析
    if (response.config.responseType === 'blob') {
      return response.data
    }
    // 后端 R 结构：{ code, msg, data }
    const body = response.data as ApiResponse
    if (body.code !== 200) {
      ElMessage.error(body.msg || '请求失败')
      return Promise.reject(new Error(body.msg))
    }
    return body.data
  },
  (error) => {
    if (error.response) {
      const { status, data } = error.response
      if (status === 401) {
        return handleUnauthorized(error)
      }
      if (status === 403) {
        showForbiddenDialog()
        return Promise.reject(error)
      }
      // S-26: 跨租户/企业暂停错误
      const code = data?.code
      if (code === 20003) {
        ElMessage.warning('请先选择客户企业')
        window.location.href = '/agency/enterprise-list'
        return Promise.reject(error)
      }
      if (code === 20005) {
        ElMessage.error('该企业已被暂停，无法操作')
        return Promise.reject(error)
      }
      const msg = data?.msg || data?.error || '请求失败'
      ElMessage.error(msg)
    } else {
      ElMessage.error('网络异常，请检查连接')
    }
    return Promise.reject(error)
  },
)

export default request