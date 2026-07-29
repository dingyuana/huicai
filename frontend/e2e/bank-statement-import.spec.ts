import { test, expect } from '@playwright/test'

const BASE = 'http://localhost:3001'

test.describe('银行对账单', () => {
  /** 注入 mock token + mock API 拦截 */
  async function mockAuth(page: any) {
    await page.addInitScript(() => {
      localStorage.setItem('huicai_token', 'mock-token-for-e2e')
      localStorage.setItem('huicai_current_enterprise_id', '1')
    })
    await page.route('**/api/v1/auth/userinfo', async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({
          code: 200, msg: 'ok', data: {
            id: 1, username: 'admin', realName: '管理员', nickname: 'admin',
            email: '', phone: '', avatar: '', deptId: 1, roles: [1],
            permissions: ['admin', 'bank:statement:list'],
          }
        })
      })
    })
    await page.route('**/bank-accounts/active', async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: [{ id: 1, accountNo: '110906291910608', accountName: '招商银行', balance: 500000, isActive: true }] })
      })
    })
    await page.route('**/bank-statements/page*', async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: { records: [], total: 0, current: 1, size: 20 } })
      })
    })
    // 分类统计 —— 页面 mount 时自动调用
    await page.route('**/classification-counts*', async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: {} })
      })
    })
  }

  test('页面加载并显示对账单标题', async ({ page }) => {
    await mockAuth(page)
    await page.goto(`${BASE}/finance/bank-statement`, { waitUntil: 'networkidle', timeout: 15000 })
    await expect(page.locator('.page-title')).toHaveText('银行对账单', { timeout: 10000 })
    await expect(page.getByRole('button', { name: /导入对账单/i })).toBeVisible()
  })

  test('打开导入对话框显示 CSV 和 Excel 标签', async ({ page }) => {
    await mockAuth(page)

    await page.goto(`${BASE}/finance/bank-statement`, { waitUntil: 'load', timeout: 15000 })
    await page.waitForTimeout(2000)
    await expect(page.getByRole('button', { name: /导入对账单/i })).toBeVisible({ timeout: 10000 })
    await page.getByRole('button', { name: /导入对账单/i }).click()
    // 对话框打开后应有 CSV 导入 tab 和 Excel 导入 tab
    await expect(page.getByText('CSV导入')).toBeVisible({ timeout: 5000 })
    await expect(page.getByText('Excel导入')).toBeVisible({ timeout: 5000 })
  })
})
