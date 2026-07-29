/**
 * 集成测试通用工具
 * 
 * 用于「一推一」前后端联调测试，不 mock API，真实调用后端。
 * 前端 dev server (port 3001) 代理 /api 到后端 (port 8000)。
 */

import { test as base, type Page, type APIRequestContext } from '@playwright/test'

const BASE = 'http://localhost:3001'

// ========== 测试标识 ==========
// 所有测试数据使用统一前缀，方便清理
export const TEST_PREFIX = 'E2E-INT-'
export function testId(suffix: string): string {
  return `${TEST_PREFIX}${suffix}-${Date.now()}`
}

// ========== Auth 管理 ==========
let cachedToken: string | null = null
let cachedUserInfo: any = null

export interface LoginResult {
  token: string
  userInfo: any
  enterpriseId: string
}

/**
 * 登录获取 token，带缓存避免重复登录
 */
export async function login(request: APIRequestContext): Promise<LoginResult> {
  if (cachedToken) {
    return { token: cachedToken, userInfo: cachedUserInfo, enterpriseId: '1' }
  }

  const res = await request.post(`${BASE}/api/v1/auth/login`, {
    data: { username: 'admin', password: 'admin123' },
  })
  const body = await res.json()
  if (body.code !== 200) {
    throw new Error(`登录失败: ${JSON.stringify(body)}`)
  }

  cachedToken = body.data.token
  cachedUserInfo = body.data.userInfo
  return { token: cachedToken, userInfo: cachedUserInfo, enterpriseId: body.data.enterpriseId || '1' }
}

/**
 * 清除登录缓存（每个测试文件清理，避免跨测试 token 过期问题）
 */
export function clearAuthCache(): void {
  cachedToken = null
  cachedUserInfo = null
}

// ========== 发票测试数据 ==========
export interface TestInvoice {
  invoiceNo: string
  invoiceType: string
  vendorName: string
  amount: string
  taxRate: number
  taxAmount: string
  totalAmount: string
  period: string
}

/**
 * 创建进项发票测试数据
 */
export function makeInputInvoice(suffix: string): TestInvoice {
  const prefix = testId(suffix)
  return {
    invoiceNo: `IN-${prefix}`,
    invoiceType: 'SPECIAL',
    vendorName: `测试供应商-${prefix}`,
    amount: '10000.00',
    taxRate: 13,
    taxAmount: '1300.00',
    totalAmount: '11300.00',
    period: '202607',
  }
}

/**
 * 创建销项发票测试数据
 */
export function makeOutputInvoice(suffix: string): TestInvoice {
  const prefix = testId(suffix)
  return {
    invoiceNo: `OUT-${prefix}`,
    invoiceType: 'SPECIAL',
    vendorName: `测试客户-${prefix}`,
    amount: '10000.00',
    taxRate: 13,
    taxAmount: '1300.00',
    totalAmount: '11300.00',
    period: '202607',
  }
}

// ========== API 调用封装 ==========
const AUTH_HEADERS = (token: string) => ({
  'Authorization': `Bearer ${token}`,
  'Content-Type': 'application/json',
})

/**
 * 通过 API 创建进项发票
 */
export async function createInputInvoice(request: APIRequestContext, token: string, data: TestInvoice) {
  const res = await request.post(`${BASE}/api/sme/tax/v1/tax/input-invoices`, {
    headers: AUTH_HEADERS(token),
    data: {
      invoiceNo: data.invoiceNo,
      invoiceType: data.invoiceType,
      vendorName: data.vendorName,
      invoiceDate: '2026-07-28',
      period: data.period,
      amount: data.amount,
      taxRate: data.taxRate,
      taxAmount: data.taxAmount,
      totalAmount: data.totalAmount,
      status: 'PENDING_CONFIRM',
      certificationStatus: 'UNCERTIFIED',
    },
  })
  const body = await res.json()
  if (body.code !== 200) {
    throw new Error(`创建进项发票失败: ${JSON.stringify(body)}`)
  }
  return body.data
}

/**
 * 通过 API 提交审核
 */
export async function submitForReview(request: APIRequestContext, token: string, id: number) {
  const res = await request.post(`${BASE}/api/sme/tax/v1/tax/input-invoices/${id}/submit-review`, {
    headers: AUTH_HEADERS(token),
    params: { userId: 1 },
  })
  const body = await res.json()
  if (body.code !== 200) {
    throw new Error(`提交审核失败: ${JSON.stringify(body)}`)
  }
}

/**
 * 通过 API 审核通过
 */
export async function confirmInvoice(request: APIRequestContext, token: string, id: number) {
  const res = await request.post(`${BASE}/api/sme/tax/v1/tax/input-invoices/${id}/confirm`, {
    headers: AUTH_HEADERS(token),
    params: { userId: 1 },
  })
  const body = await res.json()
  if (body.code !== 200) {
    throw new Error(`审核通过失败: ${JSON.stringify(body)}`)
  }
}

/**
 * 查询进项发票列表
 */
export async function queryInputInvoices(request: APIRequestContext, token: string, params?: any) {
  const res = await request.get(`${BASE}/api/sme/tax/v1/tax/input-invoices/page`, {
    headers: AUTH_HEADERS(token),
    params: { current: 1, size: 20, ...params },
  })
  const body = await res.json()
  if (body.code !== 200) {
    throw new Error(`查询进项发票失败: ${JSON.stringify(body)}`)
  }
  return body.data
}

/**
 * 查询凭证列表（POST 方式，带请求体）
 */
export async function queryVouchers(request: APIRequestContext, token: string, params?: any) {
  const res = await request.post(`${BASE}/api/base/voucher/v1/vouchers/page`, {
    headers: AUTH_HEADERS(token),
    data: { current: 1, size: 20, ...params },
  })
  const body = await res.json()
  if (body.code !== 200) {
    throw new Error(`查询凭证失败: ${JSON.stringify(body)}`)
  }
  return body.data
}

/**
 * 查询业务单据列表（POST 方式，带请求体）
 */
export async function queryBusinessDocs(request: APIRequestContext, token: string, params?: any) {
  const res = await request.post(`${BASE}/api/sme/arap/v1/business-docs/page`, {
    headers: AUTH_HEADERS(token),
    data: { current: 1, size: 20, ...params },
  })
  const body = await res.json()
  if (body.code !== 200) {
    throw new Error(`查询业务单据失败: ${JSON.stringify(body)}`)
  }
  return body.data
}

// ========== 数据清理工具 ==========

/**
 * 清理测试数据 — 删除所有 TEST_PREFIX 相关的数据
 */
export async function cleanupTestData(request: APIRequestContext, token: string): Promise<void> {
  // 清理进项发票
  const inputRes = await queryInputInvoices(request, token, { size: 9999 })
  if (inputRes?.records) {
    for (const rec of inputRes.records) {
      if (rec.invoiceNo?.startsWith(TEST_PREFIX) || rec.invoiceNo?.startsWith('IN-')) {
        await request.delete(`${BASE}/api/sme/tax/v1/tax/input-invoices/${rec.id}`, {
          headers: AUTH_HEADERS(token),
        }).catch(() => {})
      }
    }
  }
}

// ========== 前端页面操作 ==========

/**
 * 通过前端登录页面登录（真实登录流程）
 */
export async function loginViaUI(page: Page, token: string) {
  // 先注入 token，让 Pinia store 初始化时能读到
  await page.addInitScript((t) => {
    localStorage.setItem('huicai_token', t)
    localStorage.setItem('huicai_current_enterprise_id', '1')
  }, token)
  // 拦截 userinfo API — 直接返回 mock 数据，避免真实 API 调用
  await page.route(url => url.toString().includes('/api/v1/auth/userinfo'), async route => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        code: 200, msg: 'ok', data: {
          id: 1, username: 'admin', realName: '管理员', nickname: 'admin',
          email: '', phone: '', avatar: '', deptId: 1, roles: [1],
          permissions: [
            'subjects:manage', 'voucher:list', 'voucher:type:list',
            'tax:input:list', 'tax:output:list', 'tax:vat:view',
            'report:subject:list', 'report:balance:view', 'report:income:view',
            'report:cashflow:view', 'doc:list', 'arap:reconciliation:workbench',
            'bank:statement:list', 'period:close', 'beginning:balance:init',
            'asset:category:list', 'asset:card:list',
          ],
          userType: 'SUPER_ADMIN',
        }
      })
    })
  })
  // 拦截所有 /api/v1/auth/ 下的其他请求
  await page.route(url => url.toString().includes('/api/v1/auth/') && !url.toString().includes('/userinfo'), async route => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ code: 200, msg: 'ok' })
    })
  })
}