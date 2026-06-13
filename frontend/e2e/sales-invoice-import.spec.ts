import { test, expect } from '@playwright/test'

const BASE = 'http://localhost:3001'

test.describe('销售发票导入流程', () => {
  test.beforeEach(async ({ page }) => {
    await page.addInitScript(() => { localStorage.setItem('huicai_token', 'mock') })
    await page.route('**/api/v1/auth/userinfo', async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: { id: 1, username: 'admin', realName: '管理员', nickname: 'admin', email: '', phone: '', avatar: '', deptId: 1, roles: [1], permissions: ['admin', 'doc:create'] } })
      })
    })
  })

  test('页面加载显示导入说明', async ({ page }) => {
    await page.goto(`${BASE}/finance/sales-invoice-import`, { waitUntil: 'load' })
    await page.waitForTimeout(2000)
    await expect(page.locator('.page-title')).toHaveText('销售发票导入')
    await expect(page.getByText(/自动识别列名、匹配客户/)).toBeVisible()
    await expect(page.getByText(/拖放销售发票Excel文件/)).toBeVisible()
  })

  test('预览导入返回结果', async ({ page }) => {
    await page.route('**/api/v1/sales-invoices/preview', async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: { total: 3, valid: 3, errors: [], batchId: 'PRE_test', previews: [{ rowIndex: 1, invoiceNo: 'INV-001', buyerName: '测试客户', invoiceDate: '2024-12-01', goodsName: '测试商品', amount: 1000, taxAmount: 130, totalAmount: 1130 }] } })
      })
    })
    await page.goto(`${BASE}/finance/sales-invoice-import`, { waitUntil: 'load' })
    await page.waitForTimeout(2000)
    await expect(page.getByText(/下一步: 预览/)).toBeVisible()
  })
})
