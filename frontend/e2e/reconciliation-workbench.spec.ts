import { test, expect } from '@playwright/test'

const BASE = 'http://localhost:3001'

test.describe('核销工作台 E2E', () => {
  test.beforeEach(async ({ page }) => {
    await page.addInitScript(() => { localStorage.setItem('huicai_token', 'mock') })
    await page.route('**/api/v1/auth/userinfo', async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: { id: 1, username: 'admin', realName: '管理员', nickname: 'admin', email: '', phone: '', avatar: '', deptId: 1, roles: [1], permissions: ['admin', 'arap:reconciliation:workbench'] } })
      })
    })
  })

  test('页面加载显示收款单和付款单 tab', async ({ page }) => {
    await page.route('**/api/v1/business-docs/page', async route => {
      const body = JSON.parse(route.request().postData() || '{}')
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: {
          total: 1, current: 1, size: 20,
          records: [
            { id: 1, docNo: 'SH2026070001', docType: 'RECEIPT', docDate: '2026-07-08', period: '202607',
              amount: 1000, settledAmount: 0, unsettledAmount: 1000,
              customerName: '测试客户A', summary: '收款', status: 'DRAFT' },
          ]
        }})
      })
    })
    await page.goto(`${BASE}/arap/reconciliation-workbench`, { waitUntil: 'load' })
    await page.waitForTimeout(1000)

    // 验证页面标题
    await expect(page.locator('.page-title')).toHaveText('核销工作台')
    // 验证 tab 切换
    await expect(page.locator('.el-radio-button').first()).toHaveText('收款单')
    await expect(page.locator('.el-radio-button').last()).toHaveText('付款单')
    // 验证默认选中收款单
    await expect(page.locator('.el-radio-button__inner.is-active').first()).toHaveText('收款单')
    // 验证表格行数（只显示收款单，不显示应收单）
    const rows = page.locator('.el-table__body-wrapper tbody tr')
    await expect(rows).toHaveCount(1)
    // 验证 RECEIPT 行可见
    await expect(page.getByText('SH2026070001')).toBeVisible()
    // 验证未核销金额列
    await expect(page.getByText('1,000.00').first()).toBeVisible()
  })

  test('切换到付款单 tab 应查询应付方向单据', async ({ page }) => {
    await page.route('**/api/v1/business-docs/page', async route => {
      const body = JSON.parse(route.request().postData() || '{}')
      if (JSON.stringify(body.docTypes).includes('PAYMENT')) {
        await route.fulfill({ status: 200, contentType: 'application/json',
          body: JSON.stringify({ code: 200, msg: 'ok', data: {
            total: 2, current: 1, size: 20,
            records: [
              { id: 4, docNo: 'FK2026070001', docType: 'PAYMENT', docDate: '2026-07-08', period: '202607',
                amount: 2000, settledAmount: 0, unsettledAmount: 2000,
                supplierName: '供应商A', summary: '付款', status: 'DRAFT' },
              { id: 5, docNo: 'FP2026070001', docType: 'INVOICE_IN', docDate: '2026-07-08', period: '202607',
                amount: 3000, settledAmount: 0, unsettledAmount: 3000,
                supplierName: '供应商B', summary: '进项发票应付', status: 'DRAFT' },
            ]
          }})
      } else {
        // RECEIPT tab 返回空
        await route.fulfill({ status: 200, contentType: 'application/json',
          body: JSON.stringify({ code: 200, msg: 'ok', data: { total: 0, current: 1, size: 20, records: [] }}) })
      }
    })
    await page.goto(`${BASE}/arap/reconciliation-workbench`, { waitUntil: 'load' })
    await page.waitForTimeout(1000)

    // 点击付款单 tab
    await page.locator('.el-radio-button').last().click()
    await page.waitForTimeout(500)

    // 验证列标题变为"供应商"
    await expect(page.locator('th').filter({ hasText: '供应商' })).toBeVisible()
    // 验证表格显示付款单和进项发票
    await expect(page.getByText('FK2026070001')).toBeVisible()
    await expect(page.getByText('FP2026070001')).toBeVisible()
    // 验证金额显示
    await expect(page.getByText('2,000.00').first()).toBeVisible()
    await expect(page.getByText('3,000.00').first()).toBeVisible()
  })

  test('点击核销推荐应显示推荐弹窗', async ({ page }) => {
    await page.route('**/api/v1/business-docs/page', async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: {
          total: 1, current: 1, size: 20,
          records: [
            { id: 1, docNo: 'YS2026070001', docType: 'INVOICE_OUT', docDate: '2026-07-08', period: '202607',
              amount: 1950, settledAmount: 0, unsettledAmount: 1950,
              customerName: '测试客户', summary: '应收', status: 'DRAFT', customerId: 100 },
          ]
        }})
      })
    })
    await page.route('**/api/v1/reconciliation/suggest', async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: {
          sourceDocId: 1, message: '共 1 项匹配',
          items: [
            { targetDocNo: 'SH2026070001', targetDocType: 'RECEIPT', originalAmount: 2000, unsettledAmount: 2000,
              matchLevel: 'L1', suggestedAmount: 1950, targetDocId: 2 },
          ],
        }})
      })
    })
    await page.goto(`${BASE}/arap/reconciliation-workbench`, { waitUntil: 'load' })
    await page.waitForTimeout(1000)

    // 点击行触发核销推荐
    await page.getByText('YS2026070001').click()
    await page.waitForTimeout(1000)

    // 验证弹窗出现
    await expect(page.getByText(/核销推荐/).first()).toBeVisible()
    // 验证匹配单据显示
    await expect(page.getByText('SH2026070001')).toBeVisible()
    await expect(page.getByText('收款单')).toBeVisible()
    await expect(page.getByText('L1 引用号匹配')).toBeVisible()
  })
})