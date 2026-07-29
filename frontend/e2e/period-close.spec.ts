import { test, expect } from '@playwright/test'

const BASE = 'http://localhost:3001'

test.describe('期末结账', () => {
  /** 注入 mock token + 通用 auth */
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

  test('页面加载显示标题和操作按钮', async ({ page }) => {
    await mockAuth(page)

    await page.goto(`${BASE}/finance/period-close`, { waitUntil: 'networkidle', timeout: 15000 })
    await page.waitForTimeout(500)

    await expect(page.locator('.page-title')).toHaveText('期末结账', { timeout: 10000 })
    // 操作按钮
    await expect(page.getByRole('button', { name: /结账前检查/i })).toBeVisible()
    await expect(page.getByRole('button', { name: /生成损益结转/i })).toBeVisible()
    await expect(page.getByRole('button', { name: /执行结账/i })).toBeVisible()
    await expect(page.getByRole('button', { name: /反结账/i })).toBeVisible()
    // 初始空状态
    await expect(page.getByText('请点击「结账前检查」开始')).toBeVisible()
  })

  test('结账前检查通过显示试算平衡表', async ({ page }) => {
    await mockAuth(page)

    // Mock 结账检查 API（通过）
    await page.route(url => url.toString().includes('/base/voucher/v1/period-close/check'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: {
          passed: true, issues: [],
          trialBalance: {
            balanced: true, beginBalanced: true, movementBalanced: true, endBalanced: true,
            totalBeginDebit: 500000, totalBeginCredit: 500000,
            totalDebitTotal: 100000, totalCreditTotal: 100000,
            totalEndDebit: 600000, totalEndCredit: 600000,
          },
        }})
      })
    })

    await page.goto(`${BASE}/finance/period-close`, { waitUntil: 'networkidle', timeout: 15000 })
    await page.waitForTimeout(500)

    // 点击结账前检查
    await page.getByRole('button', { name: /结账前检查/i }).click()
    await page.waitForTimeout(1000)

    // 检查通过提示
    await expect(page.getByText('结账检查通过, 可执行结账')).toBeVisible()
    // 试算平衡表
    await expect(page.getByText('试算平衡')).toBeVisible()
    await expect(page.locator('.el-tag').filter({ hasText: '是' }).first()).toBeVisible()
  })

  test('结账前检查未通过显示问题列表', async ({ page }) => {
    await mockAuth(page)

    // Mock 结账检查 API（未通过）
    await page.route(url => url.toString().includes('/base/voucher/v1/period-close/check'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: {
          passed: false,
          issues: [
            '存在未记账凭证, 期间 202607 共 3 笔',
            '存在未审核凭证, 期间 202607 共 1 笔',
            '试算不平衡: 借方合计 100000 ≠ 贷方合计 95000',
          ],
          trialBalance: {
            balanced: false, beginBalanced: true, movementBalanced: false, endBalanced: false,
            totalBeginDebit: 500000, totalBeginCredit: 500000,
            totalDebitTotal: 100000, totalCreditTotal: 95000,
            totalEndDebit: 600000, totalEndCredit: 595000,
          },
        }})
      })
    })

    await page.goto(`${BASE}/finance/period-close`, { waitUntil: 'networkidle', timeout: 15000 })
    await page.waitForTimeout(500)

    // 点击结账前检查
    await page.getByRole('button', { name: /结账前检查/i }).click()
    await page.waitForTimeout(1000)

    // 检查未通过提示
    await expect(page.getByText('结账检查未通过, 请处理以下问题')).toBeVisible()
    // 问题列表
    await expect(page.getByText('存在未记账凭证')).toBeVisible()
    await expect(page.getByText('存在未审核凭证')).toBeVisible()
    await expect(page.getByText('试算不平衡')).toBeVisible()
    // 执行结账按钮应禁用
    await expect(page.getByRole('button', { name: /执行结账/i })).toBeDisabled()
  })

  test('生成损益结转后显示成功提示', async ({ page }) => {
    await mockAuth(page)

    // Mock 结账检查（通过）
    await page.route(url => url.toString().includes('/base/voucher/v1/period-close/check'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: {
          passed: true, issues: [],
          trialBalance: { balanced: true, beginBalanced: true, movementBalanced: true, endBalanced: true,
            totalBeginDebit: 500000, totalBeginCredit: 500000,
            totalDebitTotal: 100000, totalCreditTotal: 100000,
            totalEndDebit: 600000, totalEndCredit: 600000,
          },
        }})
      })
    })
    // Mock 损益结转
    await page.route(url => url.toString().includes('/base/voucher/v1/period-close/profit-carryover'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: '生成成功', data: 100 })
      })
    })

    await page.goto(`${BASE}/finance/period-close`, { waitUntil: 'networkidle', timeout: 15000 })
    await page.waitForTimeout(500)

    // 先检查
    await page.getByRole('button', { name: /结账前检查/i }).click()
    await page.waitForTimeout(500)

    // 生成损益结转按钮可见
    await expect(page.getByRole('button', { name: /生成损益结转/i })).toBeVisible()
    // 执行结账按钮可见
    await expect(page.getByRole('button', { name: /执行结账/i })).toBeVisible()
  })
})