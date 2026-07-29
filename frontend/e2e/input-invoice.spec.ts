import { test, expect } from '@playwright/test'

const BASE = 'http://localhost:3001'

test.describe('进项发票', () => {
  /** 注入 mock token + 通用 auth */
  async function mockAuth(page: any) {
    await page.addInitScript(() => {
      localStorage.setItem('huicai_token', 'mock-token-for-e2e')
      localStorage.setItem('huicai_current_enterprise_id', '1')
    })
    await page.route(url => url.toString().includes('/api/v1/auth/userinfo'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: {
          id: 1, username: 'admin', realName: '管理员', nickname: 'admin',
          email: '', phone: '', avatar: '', deptId: 1, roles: [1],
          permissions: ['admin'],
          userType: 'SUPER_ADMIN',
        }})
      })
    })
  }

  test('页面加载显示进项发票标题和筛选条件', async ({ page }) => {
    await mockAuth(page)

    // Mock 分页查询
    await page.route(url => url.toString().includes('/sme/tax/v1/tax/input-invoices/page'), async route => {
      const url = route.request().url()
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: { records: [], total: 0, current: 1, size: 20 } })
      })
    })

    await page.goto(`${BASE}/tax/input-invoice`, { waitUntil: 'networkidle', timeout: 15000 })
    await page.waitForTimeout(500)

    await expect(page.locator('.page-title')).toHaveText('进项发票', { timeout: 10000 })
    await expect(page.getByRole('button', { name: /新增发票/i })).toBeVisible()
    // 筛选条件
    await expect(page.locator('label').filter({ hasText: '供应商' })).toBeVisible()
    await expect(page.locator('label').filter({ hasText: '期间' })).toBeVisible()
    await expect(page.locator('label').filter({ hasText: '认证状态' })).toBeVisible()
    await expect(page.locator('label').filter({ hasText: '审核状态' })).toBeVisible()
    await expect(page.getByRole('button', { name: /查询/i })).toBeVisible()
  })

  test('表格显示发票数据', async ({ page }) => {
    await mockAuth(page)

    const mockRecords = [
      { id: 1, invoiceNo: 'INV202607001', invoiceDate: '2026-07-15', vendorName: '供应商A',
        amount: 10000, taxAmount: 1300, taxRate: 13, status: 'PENDING_CONFIRM', certificationStatus: 'UNCERTIFIED' },
      { id: 2, invoiceNo: 'INV202607002', invoiceDate: '2026-07-20', vendorName: '供应商B',
        amount: 5000, taxAmount: 300, taxRate: 6, status: 'CONFIRMED', certificationStatus: 'CERTIFIED' },
    ]

    await page.route(url => url.toString().includes('/sme/tax/v1/tax/input-invoices/page'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: { records: mockRecords, total: 2, current: 1, size: 20 } })
      })
    })

    await page.goto(`${BASE}/tax/input-invoice`, { waitUntil: 'networkidle', timeout: 15000 })
    await page.waitForTimeout(500)

    // 发票数据
    await expect(page.getByText('INV202607001')).toBeVisible()
    await expect(page.getByText('INV202607002')).toBeVisible()
    await expect(page.getByText('供应商A')).toBeVisible()
    await expect(page.getByText('供应商B')).toBeVisible()
    // 金额列
    await expect(page.getByText('10000.00')).toBeVisible()
    await expect(page.getByText('5000.00')).toBeVisible()
    // 表头列
    await expect(page.locator('th').filter({ hasText: '发票号' })).toBeVisible()
    await expect(page.locator('th').filter({ hasText: '供应商' })).toBeVisible()
    await expect(page.locator('th').filter({ hasText: '金额' })).toBeVisible()
    await expect(page.locator('th').filter({ hasText: '税额' })).toBeVisible()
    await expect(page.locator('th').filter({ hasText: '审核状态' })).toBeVisible()
    await expect(page.locator('th').filter({ hasText: '认证状态' })).toBeVisible()
  })

  test('新增发票弹窗显示表单字段', async ({ page }) => {
    await mockAuth(page)

    await page.route(url => url.toString().includes('/sme/tax/v1/tax/input-invoices/page'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: { records: [], total: 0, current: 1, size: 20 } })
      })
    })

    await page.goto(`${BASE}/tax/input-invoice`, { waitUntil: 'networkidle', timeout: 15000 })
    await page.waitForTimeout(500)

    // 点击新增发票
    await page.getByRole('button', { name: /新增发票/i }).click()
    await page.waitForTimeout(500)

    // 验证弹窗标题
    await expect(page.getByText('新增进项发票').first()).toBeVisible({ timeout: 5000 })
    // 表单字段
    await expect(page.locator('label').filter({ hasText: '发票号' }).first()).toBeVisible()
    await expect(page.locator('label').filter({ hasText: '开票日期' }).first()).toBeVisible()
    await expect(page.locator('label').filter({ hasText: '供应商' }).first()).toBeVisible()
    await expect(page.locator('label').filter({ hasText: '金额(不含税)' })).toBeVisible()
    await expect(page.locator('label').filter({ hasText: '税率' })).toBeVisible()
    await expect(page.locator('label').filter({ hasText: '税额' })).toBeVisible()
    await expect(page.locator('label').filter({ hasText: '发票类型' })).toBeVisible()
    // 弹窗按钮
    await expect(page.getByRole('button', { name: /确定/i })).toBeVisible()
    await expect(page.getByRole('button', { name: /取消/i })).toBeVisible()
  })

  test('新增发票提交后刷新列表', async ({ page }) => {
    await mockAuth(page)

    let listData: any[] = []

    await page.route(url => url.toString().includes('/sme/tax/v1/tax/input-invoices/page'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: { records: listData, total: listData.length, current: 1, size: 20 } })
      })
    })

    // Mock 创建发票
    await page.route(url => url.toString().includes('/sme/tax/v1/tax/input-invoices') && !url.toString().includes('/page') && !url.toString().includes('/by-tax-rate'), async route => {
      const body = JSON.parse(route.request().postData() || '{}')
      listData = [{ id: 1, ...body, status: 'PENDING_CONFIRM', certificationStatus: 'UNCERTIFIED' }]
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: { id: 1 } })
      })
    })

    await page.goto(`${BASE}/tax/input-invoice`, { waitUntil: 'networkidle', timeout: 15000 })
    await page.waitForTimeout(500)

    // 新增发票
    await page.getByRole('button', { name: /新增发票/i }).click()
    await page.waitForTimeout(500)

    // 填写表单
    await page.locator('.el-dialog .el-form-item:has(.el-form-item__label:text("发票号")) input').fill('INV202607001')
    // 开票日期
    await page.locator('.el-dialog .el-form-item:has(.el-form-item__label:text("开票日期")) input').fill('2026-07-28')
    await page.locator('.el-dialog .el-form-item:has(.el-form-item__label:text("供应商")) input').fill('测试供应商')
    // 填写金额
    const amountInput = page.locator('.el-dialog .el-input-number').first().locator('input')
    await amountInput.fill('10000')
    // 弹窗确定按钮
    await page.getByRole('button', { name: /确定/i }).click()
    await page.waitForTimeout(500)

    // 提交后应关闭弹窗并刷新列表
    await expect(page.getByText('新增进项发票')).not.toBeVisible()
    // 列表应显示新数据
    await expect(page.getByText('INV202607001')).toBeVisible()
  })

  test('发票状态可流转：提交审核→审核通过', async ({ page }) => {
    await mockAuth(page)

    let invoiceStatus = 'PENDING_CONFIRM'
    const mockInvoice = () => ({
      id: 1, invoiceNo: 'INV202607001', invoiceDate: '2026-07-15', vendorName: '供应商A',
      amount: 10000, taxAmount: 1300, taxRate: 13, status: invoiceStatus, certificationStatus: 'UNCERTIFIED',
    })

    await page.route(url => url.toString().includes('/sme/tax/v1/tax/input-invoices/page'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: { records: [mockInvoice()], total: 1, current: 1, size: 20 } })
      })
    })

    // Mock 提交审核
    await page.route(url => url.toString().includes('/sme/tax/v1/tax/input-invoices/1/submit-review'), async route => {
      invoiceStatus = 'PENDING_REVIEW'
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ code: 200, msg: 'ok' }) })
    })
    // Mock 审核通过
    await page.route(url => url.toString().includes('/sme/tax/v1/tax/input-invoices/1/confirm'), async route => {
      invoiceStatus = 'CONFIRMED'
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ code: 200, msg: 'ok' }) })
    })

    // 初始状态：待确认，有"提交审核"按钮
    await page.goto(`${BASE}/tax/input-invoice`, { waitUntil: 'networkidle', timeout: 15000 })
    await page.waitForTimeout(500)
    await expect(page.getByText('待确认').first()).toBeVisible()
    await expect(page.getByRole('button', { name: /提交审核/i })).toBeVisible()

    // 提交审核
    await page.getByRole('button', { name: /提交审核/i }).click()
    await page.waitForTimeout(500)
    // 刷新页面
    await page.reload({ waitUntil: 'networkidle', timeout: 15000 })
    await page.waitForTimeout(500)
    await expect(page.getByText('待审核').first()).toBeVisible()
    await expect(page.getByRole('button', { name: /通过/i })).toBeVisible()

    // 审核通过
    await page.getByRole('button', { name: /通过/i }).click()
    await page.waitForTimeout(500)
    await page.reload({ waitUntil: 'networkidle', timeout: 15000 })
    await page.waitForTimeout(500)
    await expect(page.getByText('已确认').first()).toBeVisible()
    // 已确认后应有"生成凭证"按钮
    await expect(page.getByRole('button', { name: /生成凭证/i })).toBeVisible()
  })
})