import { test, expect } from '@playwright/test'
const BASE = 'http://localhost:3001'
test('debug basis', async ({ page }) => {
  page.on('response', res => {
    if (res.url().includes('/api/')) console.log('API:', res.status(), res.url())
  })
  await page.addInitScript(() => {
    localStorage.setItem('huicai_token', 'mock-token-for-e2e')
    localStorage.setItem('huicai_current_enterprise_id', '1')
  })
  await page.route(u => u.toString().includes('/api/v1/auth/userinfo'), async r => {
    console.log('MOCK: userinfo')
    await r.fulfill({ status: 200, contentType: 'application/json',
      body: JSON.stringify({ code: 200, msg: 'ok', data: { id: 1, username: 'admin', permissions: ['admin'], userType: 'SUPER_ADMIN' } })
    })
  })
  await page.route(u => u.toString().includes('/v1/subjects/tree'), async r => {
    console.log('MOCK: subjects')
    await r.fulfill({ status: 200, contentType: 'application/json',
      body: JSON.stringify({ code: 200, msg: 'ok', data: [] })
    })
  })
  await page.route(u => u.toString().includes('/v1/summary-lib'), async r => {
    console.log('MOCK: summary')
    await r.fulfill({ status: 200, contentType: 'application/json',
      body: JSON.stringify({ code: 200, msg: 'ok', data: { records: [], total: 0 } })
    })
  })
  await page.goto(`${BASE}/basis/account-and-summary`, { waitUntil: 'networkidle', timeout: 15000 })
  await page.waitForTimeout(500)
  console.log('URL:', page.url())
  const title = await page.locator('.page-title').textContent().catch(() => 'NOT FOUND')
  console.log('TITLE:', title)
})
