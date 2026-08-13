import { test, expect } from '@playwright/test'

const BASE = 'http://localhost:3001'

test.describe('核销工作台', () => {
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
          permissions: ['admin', 'arap:reconciliation:workbench', 'arap:settlement:list', 'arap:expense:list'],
        }})
      })
    })
    // Mock 当前期间接口（组件 onMounted 调用 resolveDefaultPeriod）
    await page.route('**/api/v1/enterprise/current-period', async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: { currentPeriod: '202607', startPeriod: '202601', hasDataPeriod: '202607' } })
      })
    })
  }

  test('页面加载显示收款单和付款单 tab', async ({ page }) => {
    await mockAuth(page)

    // Mock 业务单据查询：返回一条收款单
    await page.route('**/business-docs/page', async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: {
          total: 1, current: 1, size: 20,
          records: [
            { id: 1, docNo: 'SH2026070001', docType: 'RECEIPT', docDate: '2026-07-08', period: '202607',
              amount: 1000, settledAmount: 0, unsettledAmount: 1000,
              customerName: '测试客户A', summary: '收款', status: 'DRAFT', customerId: 100 },
          ]
        }})
      })
    })

    await page.goto(`${BASE}/arap/reconciliation-workbench`, { waitUntil: 'networkidle', timeout: 15000 })
    await page.waitForTimeout(500)

    // 验证页面标题
    await expect(page.locator('.page-title')).toHaveText('核销管理')
    // 验证 tab 切换
    await expect(page.locator('.el-radio-button').first()).toHaveText('收款单')
    await expect(page.locator('.el-radio-button').last()).toHaveText('付款单')
    // 验证表格显示单据
    await expect(page.getByText('SH2026070001')).toBeVisible()
    // 表头列名为"客户"（收款单模式）
    await expect(page.locator('th').filter({ hasText: '客户' })).toBeVisible()
  })

  test('切换到付款单 tab 应显示供应商列', async ({ page }) => {
    await mockAuth(page)

    // 返回一条收款单（默认 tab）
    await page.route('**/business-docs/page', async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: {
          total: 1, current: 1, size: 20,
          records: [
            { id: 4, docNo: 'FK2026070001', docType: 'PAYMENT', docDate: '2026-07-08', period: '202607',
              amount: 2000, settledAmount: 0, unsettledAmount: 2000,
              supplierName: '供应商A', summary: '付款', status: 'DRAFT', supplierId: 200 },
          ]
        }})
      })
    })

    await page.goto(`${BASE}/arap/reconciliation-workbench`, { waitUntil: 'networkidle', timeout: 15000 })
    await page.waitForTimeout(500)

    // 切换到付款单 tab
    await page.locator('.el-radio-button').last().click()
    await page.waitForTimeout(500)

    // 列标题变为"供应商"
    await expect(page.locator('th').filter({ hasText: '供应商' })).toBeVisible()
    // 验证金额显示
    await expect(page.getByText('2,000.00').first()).toBeVisible()
  })

  test('点击有未核销金额的行应显示核销推荐弹窗', async ({ page }) => {
    await mockAuth(page)

    // 返回一条有未核销金额的收款单
    await page.route('**/business-docs/page', async route => {
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

    // Mock 核销推荐接口
    await page.route('**/reconciliation/**/recommend*', async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: {
          message: '共 1 项匹配',
          items: [
            { targetDocNo: 'SH2026070001', targetDocType: 'RECEIPT', originalAmount: 2000, unsettledAmount: 2000,
              matchLevel: 'L1', suggestedAmount: 1950, targetDocId: 2 },
          ],
        }})
      })
    })

    await page.goto(`${BASE}/arap/reconciliation-workbench`, { waitUntil: 'networkidle', timeout: 15000 })
    await page.waitForTimeout(500)

    // 点击行触发核销推荐
    await page.getByText('YS2026070001').click()
    await page.waitForTimeout(1000)

    // 验证弹窗出现
    await expect(page.getByText(/核销推荐/).first()).toBeVisible({ timeout: 5000 })
    // 验证匹配单据信息
    await expect(page.getByText('SH2026070001')).toBeVisible()
    await expect(page.getByText('L1 引用号匹配')).toBeVisible()
  })
})
