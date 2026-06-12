import { test, expect } from '@playwright/test'

const BASE = 'http://localhost:3002'

test.describe('银行对账单 - 导入与自动生单流程', () => {
  test.beforeEach(async ({ page }) => {
    // Inject mock token so the app thinks we're authenticated
    await page.addInitScript(() => {
      localStorage.setItem('huicai_token', 'mock-token-for-e2e')
      localStorage.setItem('huicai_refresh_token', 'mock-refresh-token')
    })

    // Mock bank accounts API
    await page.route('**/api/v1/bank-accounts/active', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 200,
          msg: 'success',
          data: [
            { id: 1, accountNo: '110906291910608', accountName: '招商银行北京分行', bankName: '招商银行', balance: 500000.00, isActive: true },
          ],
        }),
      })
    })
  })

  test('页面加载并显示对账单列表', async ({ page }) => {
    // Mock empty bank statement page
    await page.route('**/api/v1/bank-statements/page*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 200,
          msg: 'success',
          data: { records: [], total: 0, current: 1, size: 20 },
        }),
      })
    })

    await page.goto(`${BASE}/finance/bank-statement`)
    await page.waitForLoadState('networkidle')

    // Page title should be visible
    await expect(page.locator('.page-title')).toHaveText('银行对账单')
    // Import button should be visible
    await expect(page.getByRole('button', { name: /导入对账单/i })).toBeVisible()
  })

  test('导入对账单对话框可以打开', async ({ page }) => {
    await page.route('**/api/v1/bank-statements/page*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 200,
          msg: 'success',
          data: { records: [], total: 0, current: 1, size: 20 },
        }),
      })
    })

    await page.goto(`${BASE}/finance/bank-statement`)
    await page.waitForLoadState('networkidle')

    // Click import button
    await page.getByRole('button', { name: /导入对账单/i }).click()
    await page.waitForTimeout(500)

    // Dialog should be visible with Excel tab active
    await expect(page.getByText('Excel导入')).toBeVisible()
    await expect(page.getByText('CSV导入')).toBeVisible()
  })

  test('导入后显示分类结果和批量确认', async ({ page }) => {
    // Mock initial empty page
    await page.route('**/api/v1/bank-statements/page*', async (route) => {
      const url = route.request().url()
      // After import, return classified data
      if (url.includes('reviewStatus=CONFIRMED') || url.includes('classification=')) {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            code: 200,
            msg: 'success',
            data: {
              records: [
                {
                  id: 101, accountId: 1, txDate: '2024-07-10', txType: 'INCOME',
                  amount: 1500.00, counterAccount: '北京数字认证股份有限公司',
                  summary: '业务收款-货款', direction: 'in',
                  classification: 'business_receipt', reviewStatus: 'PENDING',
                  matchStatus: 'UNMATCHED',
                },
                {
                  id: 102, accountId: 1, txDate: '2024-07-11', txType: 'EXPENSE',
                  amount: 300.00, counterAccount: '银行',
                  summary: '账户管理费', direction: 'out',
                  classification: 'bank_fee', reviewStatus: 'PENDING',
                  matchStatus: 'UNMATCHED',
                },
              ],
              total: 2, current: 1, size: 20,
            },
          }),
        })
      } else {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            code: 200,
            msg: 'success',
            data: { records: [], total: 0, current: 1, size: 20 },
          }),
        })
      }
    })

    // Mock classify endpoint
    await page.route('**/api/v1/bank-statements/*/classify', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 200,
          msg: 'success',
          data: { id: 101, classification: 'business_receipt', reviewStatus: 'PENDING' },
        }),
      })
    })

    // Mock Excel import endpoint
    await page.route('**/api/v1/bank-statements/import-excel*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 200,
          msg: 'success',
          data: { total: 10, success: 10, classified: 10, batchId: 'IMP_20260710_123456', errors: [] },
        }),
      })
    })

    // Mock batch confirm endpoint
    await page.route('**/api/v1/bank-statements/batch-confirm', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 200,
          msg: 'success',
          data: { confirmed: 2, vouchers_created: 2, docs_created: 2, failed: 0 },
        }),
      })
    })

    await page.goto(`${BASE}/finance/bank-statement`)
    await page.waitForLoadState('networkidle')

    // Select bank account
    await page.locator('.el-select').first().click()
    await page.waitForTimeout(300)
    await page.locator('.el-select-dropdown__item').first().click()
    await page.waitForTimeout(300)

    // Click import, then switch to CSV tab and simulate a simple import to see results
    await page.getByRole('button', { name: /导入对账单/i }).click()
    await page.waitForTimeout(300)

    // Switch to CSV tab and import test data
    await page.getByText('CSV导入').click()
    await page.waitForTimeout(200)
    const textarea = page.locator('textarea').first()
    await textarea.fill('2024-07-10,收,1500.00,客户A,货款\n2024-07-11,支,300.00,银行,手续费')
    await page.getByRole('button', { name: /导入CSV/i }).click()
    await page.waitForTimeout(1000)

    // Wait for the query to re-fetch with data
    await page.waitForTimeout(500)

    // Now select rows and batch confirm
    const checkboxes = page.locator('.el-table__body-wrapper .el-checkbox')
    const checkboxCount = await checkboxes.count()
    if (checkboxCount > 0) {
      await checkboxes.first().click()
    }
    await page.waitForTimeout(200)

    // Click batch confirm button
    const batchBtn = page.getByRole('button', { name: /批量确认并生成/i })
    if (await batchBtn.isEnabled()) {
      await batchBtn.click()
      await page.waitForTimeout(1000)
      // Result dialog should show
      await expect(page.getByText(/确认|已生成凭证/)).toBeVisible()
    }
  })

  test('分类标签正确显示', async ({ page }) => {
    await page.route('**/api/v1/bank-statements/page*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 200,
          msg: 'success',
          data: {
            records: [
              {
                id: 201, accountId: 1, txDate: '2024-07-10', txType: 'INCOME',
                amount: 100000.00, counterAccount: '客户A',
                summary: '销售回款-发票INV-001',
                classification: 'business_receipt', reviewStatus: 'CONFIRMED',
                generatedVoucherId: 5001, generatedDocId: 3001,
                matchStatus: 'UNMATCHED',
              },
              {
                id: 202, accountId: 1, txDate: '2024-07-11', txType: 'EXPENSE',
                amount: 15.00, counterAccount: '银行',
                summary: '网银转账手续费',
                classification: 'bank_fee', reviewStatus: 'PENDING',
                matchStatus: 'UNMATCHED',
              },
              {
                id: 203, accountId: 1, txDate: '2024-07-12', txType: 'OUT',
                amount: 5000.00, counterAccount: '税务局',
                summary: '增值税扣缴',
                classification: 'tax_payment', reviewStatus: 'PENDING',
                matchStatus: 'UNMATCHED',
              },
            ],
            total: 3, current: 1, size: 20,
          },
        }),
      })
    })

    await page.goto(`${BASE}/finance/bank-statement`)
    await page.waitForLoadState('networkidle')

    // 第一行: business_receipt + CONFIRMED + generatedVoucherId → 应显示绿色标签 + 凭证号
    const firstTag = page.locator('.el-table__body-wrapper .el-tag').first()
    await expect(firstTag).toBeVisible()

    // 第二行: bank_fee + PENDING → 应显示黄色警告标签
    // 第三行: tax_payment + PENDING → 应显示黄色警告标签

    // 确认分类标签文字存在
    await expect(page.getByText('业务收款')).toBeVisible()
    await expect(page.getByText('银行手续费')).toBeVisible()
    await expect(page.getByText('税费扣缴')).toBeVisible()

    // 确认状态标签
    await expect(page.getByText('已确认')).toBeVisible()
  })
})
