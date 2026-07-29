/**
 * 销项发票→凭证全链路联调测试
 * 
 * 真实数据模拟，不 mock API。
 * 测试链：创建销项发票 → 提交审核 → 审核通过(自动生成应收单+凭证) → 验证
 */
import { test, expect } from '@playwright/test'
import {
  login, clearAuthCache, testId,
  makeOutputInvoice,
  loginViaUI,
  type TestInvoice,
} from './helpers'

const BASE = 'http://localhost:3001'

// ========== 销项发票 API 封装 ==========
const AUTH = (token: string) => ({
  'Authorization': `Bearer ${token}`,
  'Content-Type': 'application/json',
})

async function createOutputInvoice(request: any, token: string, data: TestInvoice) {
  const res = await request.post(`${BASE}/api/sme/tax/v1/tax/output-invoices`, {
    headers: AUTH(token),
    data: {
      invoiceNo: data.invoiceNo,
      invoiceType: data.invoiceType,
      customerName: data.vendorName,
      invoiceDate: '2026-07-28',
      period: data.period,
      amount: data.amount,
      taxRate: data.taxRate,
      taxAmount: data.taxAmount,
      totalAmount: data.totalAmount,
      status: 'PENDING_CONFIRM',
    },
  })
  const body = await res.json()
  if (body.code !== 200) throw new Error(`创建销项发票失败: ${JSON.stringify(body)}`)
  return body.data
}

async function submitOutputReview(request: any, token: string, id: number) {
  const res = await request.post(`${BASE}/api/sme/tax/v1/tax/output-invoices/${id}/submit-review`, {
    headers: AUTH(token),
    params: { userId: 1 },
  })
  const body = await res.json()
  if (body.code !== 200) throw new Error(`销项提交审核失败: ${JSON.stringify(body)}`)
}

async function confirmOutputInvoice(request: any, token: string, id: number) {
  const res = await request.post(`${BASE}/api/sme/tax/v1/tax/output-invoices/${id}/confirm`, {
    headers: AUTH(token),
    params: { userId: 1 },
  })
  const body = await res.json()
  if (body.code !== 200) throw new Error(`销项审核通过失败: ${JSON.stringify(body)}`)
}

async function queryOutputInvoices(request: any, token: string, params?: any) {
  const res = await request.get(`${BASE}/api/sme/tax/v1/tax/output-invoices/page`, {
    headers: AUTH(token),
    params: { current: 1, size: 20, ...params },
  })
  const body = await res.json()
  if (body.code !== 200) throw new Error(`查询销项发票失败: ${JSON.stringify(body)}`)
  return body.data
}

async function queryVouchers(request: any, token: string, params?: any) {
  const res = await request.post(`${BASE}/api/base/voucher/v1/vouchers/page`, {
    headers: AUTH(token),
    data: { current: 1, size: 20, ...params },
  })
  const body = await res.json()
  if (body.code !== 200) throw new Error(`查询凭证失败: ${JSON.stringify(body)}`)
  return body.data
}

async function queryBusinessDocs(request: any, token: string, params?: any) {
  const res = await request.post(`${BASE}/api/sme/arap/v1/business-docs/page`, {
    headers: AUTH(token),
    data: { current: 1, size: 9999, ...params },
  })
  const body = await res.json()
  if (body.code !== 200) throw new Error(`查询业务单据失败: ${JSON.stringify(body)}`)
  return body.data
}

async function cleanupOutputData(request: any, token: string) {
  const list = await queryOutputInvoices(request, token, { size: 9999 })
  if (list?.records) {
    for (const rec of list.records) {
      if (rec.invoiceNo?.startsWith('OUT-')) {
        await request.delete(`${BASE}/api/sme/tax/v1/tax/output-invoices/${rec.id}`, {
          headers: AUTH(token),
        }).catch(() => {})
      }
    }
  }
}

test.describe('销项发票→凭证全链路 (真实数据)', () => {
  let token: string
  let invoiceData: TestInvoice

  test.beforeAll(async ({ request }) => {
    const auth = await login(request)
    token = auth.token
  })

  test.afterAll(async ({ request }) => {
    await cleanupOutputData(request, token)
    clearAuthCache()
  })

  test.beforeEach(() => {
    invoiceData = makeOutputInvoice('flow')
  })

  test('1. 创建销项发票 → 状态为待确认', async ({ request }) => {
    const invoice = await createOutputInvoice(request, token, invoiceData)
    expect(invoice).toBeTruthy()
    expect(Number(invoice.id)).toBeGreaterThan(0)
    expect(invoice.status).toBe('PENDING_CONFIRM')
    expect(invoice.invoiceNo).toBe(invoiceData.invoiceNo)
  })

  test('2. 销项发票列表页 → 显示新建发票', async ({ request, page }) => {
    await createOutputInvoice(request, token, invoiceData)

    await loginViaUI(page, token)
    await page.goto(`${BASE}/tax/output-invoice`, { waitUntil: 'networkidle', timeout: 15000 })
    await page.waitForTimeout(1500)

    await expect(page.locator('.page-title')).toHaveText('销项发票', { timeout: 10000 })
    await expect(page.getByText(invoiceData.invoiceNo, { exact: false }).first()).toBeVisible({ timeout: 5000 })
  })

  test('3. 提交审核 → 状态变为待审核', async ({ request }) => {
    const invoice = await createOutputInvoice(request, token, invoiceData)
    const invoiceId = Number(invoice.id)

    await submitOutputReview(request, token, invoiceId)

    const list = await queryOutputInvoices(request, token, { size: 9999 })
    const updated = list.records.find((r: any) => Number(r.id) === invoiceId)
    expect(updated).toBeTruthy()
    expect(updated.status).toBe('PENDING_REVIEW')
  })

  test('4. 审核通过 → 自动生成应收单据 + 凭证（一推一）', async ({ request }) => {
    const invoice = await createOutputInvoice(request, token, invoiceData)
    const invoiceId = Number(invoice.id)
    await submitOutputReview(request, token, invoiceId)
    await confirmOutputInvoice(request, token, invoiceId)

    // 验证：发票状态
    const list = await queryOutputInvoices(request, token, { size: 9999 })
    const confirmed = list.records.find((r: any) => Number(r.id) === invoiceId)
    expect(confirmed).toBeTruthy()
    expect(confirmed.status).toBe('VOUCHERED')

    // 验证：业务单据（INVOICE_OUT 类型，金额匹配）
    const docs = await queryBusinessDocs(request, token, { size: 9999 })
    const relatedDoc = docs.records.find((d: any) =>
      d.invoiceNo === invoiceData.invoiceNo && d.docType === 'INVOICE_OUT'
    )
    expect(relatedDoc).toBeTruthy()
    expect(relatedDoc.status).toBe('VOUCHERED')
    expect(Number(relatedDoc.amount)).toBe(Number(invoiceData.totalAmount))

    // 验证：凭证（借贷平衡 - 按金额匹配查找）
    const vouchers = await queryVouchers(request, token, { size: 9999 })
    const relatedVoucher = vouchers.records.find((v: any) =>
      Number(v.totalDebit) === Number(invoiceData.totalAmount) && v.status === 'DRAFT'
    )
    expect(relatedVoucher).toBeTruthy()
    expect(relatedVoucher.status).toBe('DRAFT')
    expect(Number(relatedVoucher.totalDebit)).toBe(Number(relatedVoucher.totalCredit))
    expect(Number(relatedVoucher.totalDebit)).toBe(Number(invoiceData.totalAmount))
  })

  test('5. 前端页面验证：凭证列表显示来源', async ({ request, page }) => {
    const invoice = await createOutputInvoice(request, token, invoiceData)
    const invoiceId = Number(invoice.id)
    await submitOutputReview(request, token, invoiceId)
    await confirmOutputInvoice(request, token, invoiceId)

    await loginViaUI(page, token)
    await page.goto(`${BASE}/finance/voucher`, { waitUntil: 'networkidle', timeout: 15000 })
    await page.waitForTimeout(1000)

    await expect(page.locator('.page-title')).toHaveText('凭证管理', { timeout: 10000 })
    await expect(page.getByText(invoiceData.invoiceNo, { exact: false }).first()).toBeVisible({ timeout: 5000 })
  })
})