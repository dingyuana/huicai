import { test, expect } from '@playwright/test'

const BASE = 'http://localhost:3001'

test.describe('财务报表 - 科目余额表', () => {
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

  test('页面加载显示标题和查询条件', async ({ page }) => {
    await mockAuth(page)
    await page.route(url => url.toString().includes('/base/report/v1/reports/subject-balance'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: [] })
      })
    })

    await page.goto(`${BASE}/report/subject-balance`, { waitUntil: 'networkidle', timeout: 15000 })
    await page.waitForTimeout(500)

    await expect(page.locator('.page-title')).toHaveText('科目余额表', { timeout: 10000 })
    await expect(page.locator('label').filter({ hasText: '期间' })).toBeVisible()
    await expect(page.getByRole('button', { name: /查询/i })).toBeVisible()
    await expect(page.getByRole('button', { name: /导出/i })).toBeVisible()
  })

  test('查询后显示科目余额数据', async ({ page }) => {
    await mockAuth(page)
    await page.route(url => url.toString().includes('/base/report/v1/reports/subject-balance'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: [
          { code: '1001', name: '库存现金', level: 1, begin_balance: 10000, debit_total: 5000, credit_total: 3000, end_balance: 12000 },
          { code: '1002', name: '银行存款', level: 1, begin_balance: 500000, debit_total: 100000, credit_total: 50000, end_balance: 550000 },
          { code: '2001', name: '短期借款', level: 1, begin_balance: 200000, debit_total: 0, credit_total: 50000, end_balance: 250000 },
        ]})
      })
    })

    await page.goto(`${BASE}/report/subject-balance`, { waitUntil: 'networkidle', timeout: 15000 })
    await page.waitForTimeout(500)

    // 金额列
    await expect(page.getByText('12000.00')).toBeVisible()
    await expect(page.getByText('550000.00')).toBeVisible()
    // 表头列
    await expect(page.locator('th').filter({ hasText: '科目编码' })).toBeVisible()
    await expect(page.locator('th').filter({ hasText: '期初余额' })).toBeVisible()
    await expect(page.locator('th').filter({ hasText: '借方' })).toBeVisible()
    await expect(page.locator('th').filter({ hasText: '贷方' })).toBeVisible()
    await expect(page.locator('th').filter({ hasText: '期末余额' })).toBeVisible()
    // 合计行
    await expect(page.getByText('合计')).toBeVisible()
  })
})