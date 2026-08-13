import { test, expect } from '@playwright/test'

const BASE = 'http://localhost:3001'

test.describe('财务报表 - 三大报表', () => {
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

  const mockBalanceSheet = {
    balanced: true,
    totalAssets: 1200000,
    totalLiabEquity: 1200000,
    assets: [
      { code: '1001', name: '货币资金', end_balance: 500000 },
      { code: '1002', name: '应收账款', end_balance: 200000 },
    ],
    liabilities: [
      { code: '2001', name: '短期借款', end_balance: 300000 },
    ],
    equity: [
      { code: '4001', name: '实收资本', end_balance: 500000 },
      { code: '4002', name: '未分配利润', end_balance: 400000 },
    ],
  }

  test('资产负债表加载显示平衡提示', async ({ page }) => {
    await mockAuth(page)
    await page.route(url => url.toString().includes('/base/report/v1/reports/balance-sheet'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: mockBalanceSheet })
      })
    })

    await page.goto(`${BASE}/report/balance-sheet`, { waitUntil: 'networkidle', timeout: 15000 })
    await page.waitForTimeout(500)

    await expect(page.locator('.page-title')).toHaveText('资产负债表', { timeout: 10000 })
    await expect(page.getByText('资产=负债+所有者权益, 平衡')).toBeVisible()
    await expect(page.getByText('货币资金')).toBeVisible()
    await expect(page.getByText('应收账款')).toBeVisible()
    await expect(page.getByText('短期借款')).toBeVisible()
    await expect(page.getByText('实收资本')).toBeVisible()
  })

  test('利润表加载显示', async ({ page }) => {
    await mockAuth(page)
    await page.route(url => url.toString().includes('/base/report/v1/reports/income-statement'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: {
          revenue: 1000000,
          cost: 600000,
          grossProfit: 400000,
          expense: 200000,
          operatingProfit: 200000,
          otherExpense: 0,
          totalProfit: 200000,
          cumulativeRevenue: 3000000,
          cumulativeProfit: 600000,
        }})
      })
    })

    await page.goto(`${BASE}/report/income-statement`, { waitUntil: 'networkidle', timeout: 15000 })
    await page.waitForTimeout(500)

    await expect(page.locator('.page-title')).toHaveText('利润表', { timeout: 10000 })
  })

  test('现金流量表加载显示', async ({ page }) => {
    await mockAuth(page)
    await page.route(url => url.toString().includes('/base/report/v1/reports/cash-flow'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: {
          operatingIn: 800000,
          operatingOut: 600000,
          operatingNet: 200000,
          investingIn: 0,
          investingOut: 100000,
          investingNet: -100000,
          financingIn: 300000,
          financingOut: 50000,
          financingNet: 250000,
          totalNet: 350000,
        }})
      })
    })

    await page.goto(`${BASE}/report/cash-flow`, { waitUntil: 'networkidle', timeout: 15000 })
    await page.waitForTimeout(500)

    await expect(page.locator('.page-title')).toHaveText('现金流量表', { timeout: 10000 })
  })
})