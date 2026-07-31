import { test, expect } from '@playwright/test'

const BASE = 'http://localhost:3001'

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

test.describe('往来管理 - 往来核销', () => {
  test('页面加载显示核销列表和日志页签', async ({ page }) => {
    await mockAuth(page)
    await page.route(url => url.toString().includes('/sme/arap/v1/arap-settlements/page'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: { records: [], total: 0, current: 1, size: 20 } })
      })
    })
    await page.route(url => url.toString().includes('/sme/arap/v1/reconciliation/logs/page'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: { records: [], total: 0, current: 1, size: 20 } })
      })
    })

    await page.goto(`${BASE}/arap/settlement`, { waitUntil: 'networkidle', timeout: 15000 })
    await page.waitForTimeout(500)

    await expect(page.locator('.page-title').filter({ hasText: '往来核销' })).toBeVisible()
    await expect(page.getByRole('tab', { name: /核销单/i })).toBeVisible()
    await expect(page.getByRole('tab', { name: /核销日志/i })).toBeVisible()
  })
})

test.describe('往来管理 - 费用报销单', () => {
  test('页面加载显示费用报销单列表', async ({ page }) => {
    await mockAuth(page)
    await page.route(url => url.toString().includes('/sme/arap/v1/expense-reimbursements/page'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: { records: [], total: 0, current: 1, size: 20 } })
      })
    })

    await page.goto(`${BASE}/arap/expense`, { waitUntil: 'networkidle', timeout: 15000 })
    await page.waitForTimeout(500)

    await expect(page.locator('.page-title').filter({ hasText: '费用报销单' })).toBeVisible()
    await expect(page.getByRole('button', { name: /新增报销/i })).toBeVisible()
  })
})

test.describe('固定资产 - 折旧计提', () => {
  test('页面加载显示开发中占位', async ({ page }) => {
    await mockAuth(page)

    await page.goto(`${BASE}/asset/depreciation`, { waitUntil: 'networkidle', timeout: 15000 })
    await page.waitForTimeout(500)

    await expect(page.getByText(/折旧计提功能开发中/)).toBeVisible()
  })
})

test.describe('固定资产 - 资产处置', () => {
  test('页面加载显示处置列表', async ({ page }) => {
    await mockAuth(page)
    await page.route(url => url.toString().includes('/sme/asset/v1/asset-disposals/page'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: { records: [], total: 0, current: 1, size: 20 } })
      })
    })

    await page.goto(`${BASE}/asset/disposal`, { waitUntil: 'networkidle', timeout: 15000 })
    await page.waitForTimeout(500)

    await expect(page.locator('.page-title').filter({ hasText: '资产处置' })).toBeVisible()
  })
})

test.describe('固定资产 - 资产盘点', () => {
  test('页面加载显示开发中占位', async ({ page }) => {
    await mockAuth(page)

    await page.goto(`${BASE}/asset/inventory`, { waitUntil: 'networkidle', timeout: 15000 })
    await page.waitForTimeout(500)

    await expect(page.getByText(/资产盘点功能开发中/)).toBeVisible()
  })
})
