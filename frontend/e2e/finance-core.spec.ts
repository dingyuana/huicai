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

test.describe('财务核心 - 银行账户', () => {
  test('页面加载显示账户列表', async ({ page }) => {
    await mockAuth(page)
    await page.route(url => url.toString().includes('/v1/subjects/tree'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: [] })
      })
    })
    await page.route(url => url.toString().includes('/sme/cash/v1/bank-accounts/page'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: { records: [
          { id: 1, accountName: '基本户', accountNo: '6222021234567890', bankName: '工商银行', currency: 'CNY', status: 'ACTIVE' },
        ], total: 1, current: 1, size: 20 } })
      })
    })

    await page.goto(`${BASE}/finance/bank-account`, { waitUntil: 'networkidle', timeout: 15000 })
    await page.waitForTimeout(500)

    await expect(page.locator('.page-title').filter({ hasText: '银行账户' })).toBeVisible()
    await expect(page.locator('.el-table').getByText('基本户')).toBeVisible()
    await expect(page.getByRole('columnheader', { name: '账号' })).toBeVisible()
  })
})

test.describe('财务核心 - 银行日记账', () => {
  test('页面加载显示账簿查询表单', async ({ page }) => {
    await mockAuth(page)
    await page.route(url => url.toString().includes('/sme/cash/v1/bank-accounts/active'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: [
          { id: 1, accountName: '基本户', accountNo: '6222021234567890', bankName: '工商银行', currency: 'CNY' },
        ]})
      })
    })
    await page.route(url => url.toString().includes('/sme/cash/v1/bank-journals/page'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: { records: [], total: 0, current: 1, size: 20 } })
      })
    })

    await page.goto(`${BASE}/finance/bank-journal`, { waitUntil: 'networkidle', timeout: 15000 })
    await page.waitForTimeout(500)

    await expect(page.locator('.page-title').filter({ hasText: '银行日记账' })).toBeVisible()
    await expect(page.getByRole('button', { name: /查询/i }).first()).toBeVisible()
  })
})

test.describe('财务核心 - 待处理流水', () => {
  test('页面加载显示待处理流水标题', async ({ page }) => {
    await mockAuth(page)
    await page.route(url => url.toString().includes('/sme/cash/v1/bank-accounts/active'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: [] })
      })
    })
    await page.route(url => url.toString().includes('/sme/cash/v1/bank-statements/page'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: { records: [], total: 0, current: 1, size: 20 } })
      })
    })

    await page.goto(`${BASE}/finance/pending-pool`, { waitUntil: 'networkidle', timeout: 15000 })
    await page.waitForTimeout(500)

    await expect(page.locator('.page-title').filter({ hasText: 'C类待处理流水' })).toBeVisible()
  })
})

test.describe('财务核心 - 银行对账', () => {
  test('页面加载显示对账工作区', async ({ page }) => {
    await mockAuth(page)
    await page.route(url => url.toString().includes('/sme/cash/v1/bank-accounts/active'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: [
          { id: 1, accountName: '基本户', accountNo: '6222021234567890', bankName: '工商银行', currency: 'CNY' },
        ]})
      })
    })

    await page.goto(`${BASE}/finance/bank-reconciliation`, { waitUntil: 'networkidle', timeout: 15000 })
    await page.waitForTimeout(500)

    await expect(page.locator('.page-title').filter({ hasText: '银行对账' })).toBeVisible()
  })
})

test.describe('财务核心 - 现金日记账', () => {
  test('页面加载显示现金日记账', async ({ page }) => {
    await mockAuth(page)
    await page.route(url => url.toString().includes('/sme/cash/v1/cash-journals/page'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: { records: [], total: 0, current: 1, size: 20 } })
      })
    })

    await page.goto(`${BASE}/finance/cash-journal`, { waitUntil: 'networkidle', timeout: 15000 })
    await page.waitForTimeout(500)

    await expect(page.locator('.page-title').filter({ hasText: '现金日记账' })).toBeVisible()
    await expect(page.getByRole('button', { name: /查询/i }).first()).toBeVisible()
  })
})

test.describe('财务核心 - 票据管理', () => {
  test('页面加载显示票据列表', async ({ page }) => {
    await mockAuth(page)
    await page.route(url => url.toString().includes('/sme/cash/v1/tickets/page'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: { records: [], total: 0, current: 1, size: 20 } })
      })
    })

    await page.goto(`${BASE}/finance/ticket`, { waitUntil: 'networkidle', timeout: 15000 })
    await page.waitForTimeout(500)

    await expect(page.locator('.page-title').filter({ hasText: '票据管理' })).toBeVisible()
    await expect(page.getByRole('button', { name: /新增票据/i })).toBeVisible()
  })
})

test.describe('财务核心 - 凭证设置', () => {
  test('页面加载显示凭证类型和模板页签', async ({ page }) => {
    await mockAuth(page)
    await page.route(url => url.toString().includes('/v1/voucher-types'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: { records: [], total: 0, current: 1, size: 20 } })
      })
    })
    await page.route(url => url.toString().includes('/base/voucher/v1/voucher-templates'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: [] })
      })
    })
    await page.route(url => url.toString().includes('/v1/subjects/tree'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: [] })
      })
    })

    await page.goto(`${BASE}/finance/voucher-setup`, { waitUntil: 'networkidle', timeout: 15000 })
    await page.waitForTimeout(500)

    await expect(page.getByRole('tab', { name: /凭证类型/i })).toBeVisible()
    await expect(page.getByRole('tab', { name: /凭证模板/i })).toBeVisible()
  })
})

test.describe('财务核心 - 账簿查询', () => {
  test('页面加载显示账簿页签', async ({ page }) => {
    await mockAuth(page)
    await page.route(url => url.toString().includes('/v1/subjects/tree'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: [
          { id: 1, subjectCode: '1001', subjectName: '库存现金', children: [] },
        ]})
      })
    })
    await page.route(url => url.toString().includes('/base/voucher/v1/ledgers/subject-balance'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: [] })
      })
    })

    await page.goto(`${BASE}/finance/ledger`, { waitUntil: 'networkidle', timeout: 15000 })
    await page.waitForTimeout(500)

    await expect(page.getByRole('tab', { name: /科目余额表/i })).toBeVisible()
    await expect(page.getByRole('tab', { name: /总分类账/i })).toBeVisible()
    await expect(page.getByRole('tab', { name: /明细账/i })).toBeVisible()
  })
})

test.describe('财务核心 - 结转向导', () => {
  test('页面加载显示结转向导', async ({ page }) => {
    await mockAuth(page)
    await page.route(url => url.toString().includes('/v1/periods/all'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: [
          { period: '202607' },
        ]})
      })
    })

    await page.goto(`${BASE}/finance/carryover-guide`, { waitUntil: 'networkidle', timeout: 15000 })
    await page.waitForTimeout(500)

    await expect(page.locator('.page-title').filter({ hasText: '期末结转向导' })).toBeVisible()
  })
})

test.describe('财务核心 - 期初建账', () => {
  test('页面加载显示期初建账', async ({ page }) => {
    await mockAuth(page)
    await page.route(url => url.toString().includes('/v1/periods/all'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: [
          { period: '202607' },
        ]})
      })
    })
    await page.route(url => url.toString().includes('/v1/subjects/tree'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: [] })
      })
    })

    await page.goto(`${BASE}/finance/beginning-balance`, { waitUntil: 'networkidle', timeout: 15000 })
    await page.waitForTimeout(500)

    await expect(page.locator('.page-title').filter({ hasText: '期初建账' })).toBeVisible()
  })
})
