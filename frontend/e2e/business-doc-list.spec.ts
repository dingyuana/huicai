import { test, expect } from '@playwright/test'

const BASE = 'http://localhost:3001'

test.describe('业务单据', () => {
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

  test('页面加载显示标题和筛选条件', async ({ page }) => {
    await mockAuth(page)
    await page.route('**/sme/arap/v1/business-docs/page', async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: { records: [], total: 0, current: 1, size: 20 } })
      })
    })

    await page.goto(`${BASE}/finance/business-doc`, { waitUntil: 'networkidle', timeout: 15000 })
    await page.waitForTimeout(500)

    await expect(page.locator('.page-title')).toHaveText('业务单据', { timeout: 10000 })
    await expect(page.getByRole('button', { name: /新增单据/i })).toBeVisible()
    await expect(page.getByRole('button', { name: /刷新/i })).toBeVisible()
    await expect(page.locator('label').filter({ hasText: '状态' })).toBeVisible()
    await expect(page.locator('label').filter({ hasText: '期间' })).toBeVisible()
    await expect(page.locator('label').filter({ hasText: '关键字' })).toBeVisible()
  })

  test('表格显示单据数据', async ({ page }) => {
    await mockAuth(page)
    await page.route('**/sme/arap/v1/business-docs/page', async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: { records: [
          { id: 1, docNo: 'SK2026070001', docType: 'RECEIPT', docDate: '2026-07-15', period: '202607',
            amount: 5000, settledAmount: 0, unsettledAmount: 5000, status: 'DRAFT',
            customerName: '客户A', summary: '销售收款', dueDate: '2026-08-15' },
          { id: 2, docNo: 'FK2026070001', docType: 'PAYMENT', docDate: '2026-07-20', period: '202607',
            amount: 3000, settledAmount: 3000, unsettledAmount: 0, status: 'FULLY_RECONCILED',
            supplierName: '供应商B', summary: '采购付款', dueDate: '2026-07-20' },
        ], total: 2, current: 1, size: 20 }})
      })
    })

    await page.goto(`${BASE}/finance/business-doc`, { waitUntil: 'networkidle', timeout: 15000 })
    await page.waitForTimeout(500)

    await expect(page.getByText('SK2026070001')).toBeVisible()
    await expect(page.getByText('FK2026070001')).toBeVisible()
    await expect(page.getByText('客户A')).toBeVisible()
    await expect(page.getByText('供应商B')).toBeVisible()
    await expect(page.getByText('5,000.00').first()).toBeVisible()
    await expect(page.getByText('3,000.00').first()).toBeVisible()
    // 表头列
    await expect(page.locator('th').filter({ hasText: '单据号' })).toBeVisible()
    await expect(page.locator('th').filter({ hasText: '类型' })).toBeVisible()
    await expect(page.locator('th').filter({ hasText: '金额' })).toBeVisible()
    await expect(page.locator('th').filter({ hasText: '已核销' })).toBeVisible()
    await expect(page.locator('th').filter({ hasText: '未核销' })).toBeVisible()
    await expect(page.locator('th').filter({ hasText: '状态' })).toBeVisible()
  })

  test('单据类型筛选 Tabs 显示', async ({ page }) => {
    await mockAuth(page)
    await page.route('**/sme/arap/v1/business-docs/page', async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: { records: [], total: 0, current: 1, size: 20 } })
      })
    })

    await page.goto(`${BASE}/finance/business-doc`, { waitUntil: 'networkidle', timeout: 15000 })
    await page.waitForTimeout(500)

    // 单据类型筛选 radio-button
    await expect(page.locator('.el-radio-button').first()).toContainText('全部')
    await expect(page.getByText('收款单').first()).toBeVisible()
    await expect(page.getByText('付款单').first()).toBeVisible()
    await expect(page.getByText('应收单（销售）').first()).toBeVisible()
    await expect(page.getByText('应付单（采购）').first()).toBeVisible()
    await expect(page.getByText('费用报销单').first()).toBeVisible()
  })
})