import { test, expect } from '@playwright/test'

const BASE = 'http://localhost:3001'

test.describe('DIAG auth deep', () => {
  test('trace auth store state', async ({ page }) => {
    const logs: string[] = []
    page.on('console', msg => {
      logs.push(`[${msg.type()}] ${msg.text()}`)
    })
    page.on('pageerror', err => logs.push(`[PAGE_ERROR] ${err.message}`))

    await page.addInitScript(() => {
      // Set localStorage BEFORE page loads
      localStorage.setItem('huicai_token', 'mock-token-for-e2e')
      localStorage.setItem('huicai_current_enterprise_id', '1')
      // Store logs in window for retrieval
      window.__authLogs = []
    })

    // Intercept auth userinfo
    let interceptCount = 0
    await page.route('**/api/v1/auth/userinfo', async route => {
      interceptCount++
      logs.push(`[ROUTE] Intercepted: ${route.request().url()}`)
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: {
          id: 1, username: 'admin', realName: '管理员', nickname: 'admin',
          email: '', phone: '', avatar: '', deptId: 1, roles: [1],
          permissions: ['admin'], userType: 'SUPER_ADMIN',
        }})
      })
    })

    // Spy on auth store via evaluate script after navigation
    await page.goto(`${BASE}/report/balance-sheet`, { waitUntil: 'load', timeout: 15000 })
    await page.waitForTimeout(2000)

    // Get final URL and page content
    const finalUrl = page.url()
    const pageTitle = await page.locator('.page-title').allTextContents()
    const visibleText = await page.locator('body').innerText().then(t => t.substring(0, 500)).catch(() => '')

    logs.push(`[RESULT] Final URL: ${finalUrl}`)
    logs.push(`[RESULT] Page title texts: ${JSON.stringify(pageTitle)}`)
    logs.push(`[RESULT] Intercept count: ${interceptCount}`)
    logs.push(`[RESULT] Visible text: ${visibleText.substring(0, 200)}`)

    console.log(logs.join('\n'))
  })
})