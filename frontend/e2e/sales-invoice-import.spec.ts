import { test, expect } from '@playwright/test'

const BASE = 'http://localhost:3001'

test.describe('销项发票', () => {
  /** 注入 mock token + mock 通用 API */
  async function mockAuth(page: any) {
    await page.addInitScript(() => {
      localStorage.setItem('huicai_token', 'mock-token-for-e2e')
      localStorage.setItem('huicai_current_enterprise_id', '1')
    })
    await page.route('**/api/v1/auth/userinfo', async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: {
          id: 1, username: 'admin', realName: '管理员', nickname: 'admin',
          email: '', phone: '', avatar: '', deptId: 1, roles: [1],
          permissions: ['admin', 'tax:output:list'],
        }})
      })
    })
  }

  test('页面加载显示销项发票标题和操作按钮', async ({ page }) => {
    await mockAuth(page)

    // Mock 分页查询
    await page.route('**/tax/output-invoices/page*', async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: { records: [], total: 0, current: 1, size: 20 } })
      })
    })
    // Mock 汇总统计
    await page.route('**/tax/output-invoices/summary*', async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: { totalCount: 0, blueAmount: 0, redAmount: 0, redCount: 0 } })
      })
    })

    await page.goto(`${BASE}/tax/output-invoice`, { waitUntil: 'networkidle', timeout: 15000 })
    await page.waitForTimeout(500)

    await expect(page.locator('.page-title')).toHaveText('销项发票', { timeout: 10000 })
    await expect(page.getByRole('button', { name: /导入发票/i })).toBeVisible()
    await expect(page.getByRole('button', { name: /新增发票/i })).toBeVisible()
  })

  test('点击导入打开导入对话框', async ({ page }) => {
    await mockAuth(page)
    await page.route('**/tax/output-invoices/page*', async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: { records: [], total: 0, current: 1, size: 20 } })
      })
    })
    await page.route('**/tax/output-invoices/summary*', async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: { totalCount: 0, blueAmount: 0, redAmount: 0, redCount: 0 } })
      })
    })

    await page.goto(`${BASE}/tax/output-invoice`, { waitUntil: 'networkidle', timeout: 15000 })
    await page.waitForTimeout(500)

    await page.getByRole('button', { name: /导入发票/i }).click()
    await page.waitForTimeout(500)

    // 导入对话框标题应为 "导入销售发票"
    await expect(page.getByText('导入销售发票')).toBeVisible({ timeout: 5000 })
    // 应有"下一步: 预览"按钮
    await expect(page.getByRole('button', { name: /下一步.*预览/i })).toBeVisible()
  })
})
