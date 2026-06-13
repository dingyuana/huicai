import { test, expect } from '@playwright/test'

const BASE = 'http://localhost:3001'

test.describe('银行对账单导入流程', () => {
  test('页面加载并显示对账单标题', async ({ page }) => {
    await page.addInitScript(() => {
      localStorage.setItem('huicai_token', 'mock-token-for-e2e')
    })

    // Mock all API endpoints the page calls
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
    await page.route('**/api/v1/bank-accounts/active', async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: [{ id: 1, accountNo: '110906291910608', accountName: '招商银行', balance: 500000, isActive: true }] })
      })
    })
    await page.route('**/api/v1/bank-statements/page*', async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: { records: [], total: 0, current: 1, size: 20 } })
      })
    })

    await page.goto(`${BASE}/finance/bank-statement`, { waitUntil: 'load', timeout: 15000 })
    await page.waitForTimeout(3000)
    await expect(page.locator('.page-title')).toHaveText('银行对账单', { timeout: 10000 })
    await expect(page.getByRole('button', { name: /导入对账单/i })).toBeVisible()
  })

  test('导入对话框可以打开', async ({ page }) => {
    await page.addInitScript(() => {
      localStorage.setItem('huicai_token', 'mock-token-for-e2e')
    })
    await page.route('**/api/v1/auth/userinfo', async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: { id: 1, username: 'admin', realName: '管理员', nickname: 'admin', email: '', phone: '', avatar: '', deptId: 1, roles: [1], permissions: ['admin', 'bank:statement:list'] } })
      })
    })
    await page.route('**/api/v1/bank-accounts/active', async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: [{ id: 1, accountNo: '110906291910608', accountName: '招商银行', balance: 500000, isActive: true }] })
      })
    })
    await page.route('**/api/v1/bank-statements/page*', async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: { records: [{ id:1, accountId:1, txDate:'2024-07-10', txType:'INCOME', amount:1500, counterAccount:'客户', matchStatus:'UNMATCHED' }], total: 1, current: 1, size: 20 } })
      })
    })

    await page.goto(`${BASE}/finance/bank-statement`, { waitUntil: 'load', timeout: 15000 })
    await page.waitForTimeout(3000)
    await page.getByRole('button', { name: /导入对账单/i }).click()
    await page.waitForTimeout(500)
    await expect(page.getByText('Excel导入')).toBeVisible()
    await expect(page.getByText('CSV导入')).toBeVisible()
  })
})