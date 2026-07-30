import { test, expect } from '@playwright/test'
const BASE = 'http://localhost:3001'
test('debug subject', async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.setItem('huicai_token', 'mock-token-for-e2e')
    localStorage.setItem('huicai_current_enterprise_id', '1')
  })
  await page.route(u => u.toString().includes('/api/v1/auth/userinfo'), async r => {
    await r.fulfill({ status: 200, contentType: 'application/json',
      body: JSON.stringify({ code: 200, msg: 'ok', data: { id: 1, username: 'admin', permissions: ['admin'], userType: 'SUPER_ADMIN' } })
    })
  })
  await page.route(u => u.toString().includes('/base/report/v1/reports/subject-balance'), async r => {
    await r.fulfill({ status: 200, contentType: 'application/json',
      body: JSON.stringify({ code: 200, msg: 'ok', data: [{ code: '1001', name: '库存现金', level: 1, begin_balance: 10000, debit_total: 5000, credit_total: 3000, end_balance: 12000 }] })
    })
  })
  await page.goto(`${BASE}/report/subject-balance`, { waitUntil: 'networkidle', timeout: 15000 })
  await page.waitForTimeout(500)
  
  // Check what's visible
  const ths = await page.locator('th').allTextContents()
  console.log('COLUMNS:', ths)
  
  // Check table rows
  const cells = await page.locator('td').allTextContents()
  console.log('CELLS:', cells.slice(0, 10))
  
  // Check if summary row exists
  const footer = await page.locator('.el-table__footer').count()
  console.log('FOOTER ELEMENTS:', footer)
  
  // Check for 合计
  const allText = await page.locator('.el-table').allTextContents()
  console.log('TABLE TEXT:', allText)
})
