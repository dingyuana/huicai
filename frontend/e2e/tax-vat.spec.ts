import { test, expect } from '@playwright/test'

const BASE = 'http://localhost:3001'

test.describe('税务 - 增值税计算', () => {
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
    // Mock 当前期间接口（组件 onMounted 调用 resolveDefaultPeriod）
    await page.route(url => url.toString().includes('/api/v1/enterprise/current-period'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: { currentPeriod: '202607', startPeriod: '202601', hasDataPeriod: '202607' } })
      })
    })
  }

  test('页面加载显示标题和期间输入', async ({ page }) => {
    await mockAuth(page)
    // Mock 增值税计算（初始加载时自动调用）
    await page.route(url => url.toString().includes('/sme/tax/v1/tax/vat/calculate'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: {
          outputTax: 130000, inputTax: 78000, payableTax: 52000,
          surcharge: 6240, totalPayable: 58240, note: '计算结果仅供参考',
        }})
      })
    })
    await page.route(url => url.toString().includes('/sme/tax/v1/tax/input-invoices/summary'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: { totalAmount: 600000, totalTax: 78000, count: 8 } })
      })
    })
    await page.route(url => url.toString().includes('/sme/tax/v1/tax/output-invoices/summary'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: { totalAmount: 1000000, totalTax: 130000, count: 8 } })
      })
    })
    await page.route(url => url.toString().includes('/sme/tax/v1/tax/input-invoices/by-tax-rate'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: [] })
      })
    })
    await page.route(url => url.toString().includes('/sme/tax/v1/tax/output-invoices/by-tax-rate'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: [] })
      })
    })

    await page.goto(`${BASE}/tax/vat`, { waitUntil: 'networkidle', timeout: 15000 })
    await page.waitForTimeout(500)

    await expect(page.locator('.page-title')).toHaveText('增值税计算', { timeout: 10000 })
    await expect(page.locator('label').filter({ hasText: '期间' })).toBeVisible()
    await expect(page.getByRole('button', { name: /计算/i })).toBeVisible()
  })

  test('计算后显示增值税汇总卡片', async ({ page }) => {
    await mockAuth(page)
    await page.route(url => url.toString().includes('/sme/tax/v1/tax/vat/calculate'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: {
          outputTax: 130000, inputTax: 78000, payableTax: 52000,
          surcharge: 6240, totalPayable: 58240, note: '计算结果仅供参考',
        }})
      })
    })
    await page.route(url => url.toString().includes('/sme/tax/v1/tax/input-invoices/summary'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: { totalAmount: 600000, totalTax: 78000, count: 8 } })
      })
    })
    await page.route(url => url.toString().includes('/sme/tax/v1/tax/output-invoices/summary'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: { totalAmount: 1000000, totalTax: 130000, count: 8 } })
      })
    })
    await page.route(url => url.toString().includes('/sme/tax/v1/tax/input-invoices/by-tax-rate'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: [] })
      })
    })
    await page.route(url => url.toString().includes('/sme/tax/v1/tax/output-invoices/by-tax-rate'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: [] })
      })
    })

    await page.goto(`${BASE}/tax/vat`, { waitUntil: 'networkidle', timeout: 15000 })
    await page.waitForTimeout(500)

    // 汇总卡片
    await expect(page.getByText('销项税')).toBeVisible()
    await expect(page.getByText('进项税(可抵扣)')).toBeVisible()
    await expect(page.getByText('应交增值税')).toBeVisible()
    await expect(page.getByText('附加税合计')).toBeVisible()
    await expect(page.getByText('应交税费合计')).toBeVisible()
    // 金额
    await expect(page.getByText('130000.00')).toBeVisible()
    await expect(page.getByText('78000.00')).toBeVisible()
    await expect(page.getByText('52000.00')).toBeVisible()
    // 提示信息
    await expect(page.getByText('计算结果仅供参考')).toBeVisible()
  })

  test('按税率明细表格显示', async ({ page }) => {
    await mockAuth(page)
    await page.route(url => url.toString().includes('/sme/tax/v1/tax/vat/calculate'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: {
          outputTax: 130000, inputTax: 78000, payableTax: 52000,
          surcharge: 6240, totalPayable: 58240, note: '计算结果仅供参考',
        }})
      })
    })
    await page.route(url => url.toString().includes('/sme/tax/v1/tax/input-invoices/summary'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: { totalAmount: 600000, totalTax: 78000, count: 8 } })
      })
    })
    await page.route(url => url.toString().includes('/sme/tax/v1/tax/output-invoices/summary'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: { totalAmount: 1000000, totalTax: 130000, count: 8 } })
      })
    })
    await page.route(url => url.toString().includes('/sme/tax/v1/tax/input-invoices/by-tax-rate'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: [
          { tax_rate: 0.13, amount: 600000, count: 5 },
          { tax_rate: 0.06, amount: 300000, count: 3 },
        ]})
      })
    })
    await page.route(url => url.toString().includes('/sme/tax/v1/tax/output-invoices/by-tax-rate'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: [
          { tax_rate: 0.13, amount: 1000000, count: 8 },
        ]})
      })
    })

    await page.goto(`${BASE}/tax/vat`, { waitUntil: 'networkidle', timeout: 15000 })
    await page.waitForTimeout(500)

    // 进项按税率表格
    await expect(page.getByText('进项按税率')).toBeVisible()
    await expect(page.getByText('销项按税率')).toBeVisible()
    // 列头
    await expect(page.locator('th').filter({ hasText: '税率' }).first()).toBeVisible()
    await expect(page.locator('th').filter({ hasText: '金额' }).first()).toBeVisible()
    await expect(page.locator('th').filter({ hasText: '张数' }).first()).toBeVisible()
  })
})