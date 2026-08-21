import { test, expect } from '@playwright/test'

const BASE = 'http://localhost:3001'

test.describe('DIAG auth mock', () => {
  test('check route interception', async ({ page }) => {
    const intercepted: string[] = []
    await page.addInitScript(() => {
      localStorage.setItem('huicai_token', 'mock-token-for-e2e')
      localStorage.setItem('huicai_current_enterprise_id', '1')
    })
    await page.route('**/api/v1/auth/userinfo', async route => {
      intercepted.push(route.request().url())
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: {
          id: 1, username: 'admin', realName: '管理员', nickname: 'admin',
          email: '', phone: '', avatar: '', deptId: 1, roles: [1],
          permissions: ['admin'], userType: 'SUPER_ADMIN',
        }})
      })
    })
    await page.goto(`${BASE}/report/balance-sheet`, { waitUntil: 'networkidle', timeout: 15000 })
    await page.waitForTimeout(1000)
    console.log('FINAL URL:', page.url())
    console.log('INTERCEPTED:', JSON.stringify(intercepted))
    console.log('PAGE TITLE:', await page.locator('.page-title').allTextContents())
    console.log('LOGIN VISIBLE:', await page.getByRole('button', { name: /登 录/ }).isVisible().catch(() => 'err'))
  })
})