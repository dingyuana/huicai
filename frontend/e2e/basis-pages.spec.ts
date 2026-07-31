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

test.describe('基础数据 - 会计期间', () => {
  test('页面加载显示查询表单和操作按钮', async ({ page }) => {
    await mockAuth(page)
    await page.route(url => url.toString().includes('/v1/periods'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: { records: [], total: 0, current: 1, size: 20 } })
      })
    })

    await page.goto(`${BASE}/basis/period`, { waitUntil: 'networkidle', timeout: 15000 })
    await page.waitForTimeout(500)

    await expect(page.locator('.page-title').filter({ hasText: '会计期间管理' })).toBeVisible()
    await expect(page.getByRole('button', { name: /新增期间/i })).toBeVisible()
    await expect(page.getByRole('button', { name: /刷新/i })).toBeVisible()
  })

  test('表格显示期间数据', async ({ page }) => {
    await mockAuth(page)
    await page.route(url => url.toString().includes('/v1/periods'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: { records: [
          { id: 1, year: 2026, month: 7, periodCode: '202607', startDate: '2026-07-01', endDate: '2026-07-31', status: 'OPEN' },
          { id: 2, year: 2026, month: 6, periodCode: '202606', startDate: '2026-06-01', endDate: '2026-06-30', status: 'CLOSED' },
        ], total: 2, current: 1, size: 20 } })
      })
    })

    await page.goto(`${BASE}/basis/period`, { waitUntil: 'networkidle', timeout: 15000 })
    await page.waitForTimeout(500)

    await expect(page.locator('.el-table').getByText('202607')).toBeVisible()
    await expect(page.locator('.el-table').getByText('202606')).toBeVisible()
    await expect(page.getByRole('columnheader', { name: '期间编码' })).toBeVisible()
    await expect(page.getByRole('columnheader', { name: '状态' })).toBeVisible()
  })
})

test.describe('基础数据 - 往来单位', () => {
  async function mockPartyApis(page: any) {
    // el-tabs 非 lazy：挂载时四个页签组件全部渲染，四个 API 都必须 mock
    await page.route(url => url.toString().includes('/v1/customers/page'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: { records: [], total: 0, current: 1, size: 20 } })
      })
    })
    await page.route(url => url.toString().includes('/v1/vendors/page'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: { records: [], total: 0, current: 1, size: 20 } })
      })
    })
    await page.route(url => url.toString().includes('/v1/employees/list'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: [] })
      })
    })
    await page.route(url => url.toString().includes('/v1/system/dept/tree'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: [] })
      })
    })
  }

  test('页面加载显示客户档案页签', async ({ page }) => {
    await mockAuth(page)
    await mockPartyApis(page)

    await page.goto(`${BASE}/basis/party`, { waitUntil: 'networkidle', timeout: 15000 })
    await page.waitForTimeout(500)

    await expect(page.locator('.page-title').filter({ hasText: '客户档案' })).toBeVisible()
    await expect(page.getByRole('tab', { name: /客户/i }).first()).toBeVisible()
    await expect(page.getByRole('tab', { name: /供应商/i })).toBeVisible()
    await expect(page.getByRole('tab', { name: /员工/i })).toBeVisible()
    await expect(page.getByRole('tab', { name: /部门/i })).toBeVisible()
  })

  test('切换到供应商页签', async ({ page }) => {
    await mockAuth(page)
    await mockPartyApis(page)

    await page.goto(`${BASE}/basis/party`, { waitUntil: 'networkidle', timeout: 15000 })
    await page.waitForTimeout(500)
    await page.getByRole('tab', { name: /供应商/i }).click()
    await page.waitForTimeout(500)

    await expect(page.locator('.page-title').filter({ hasText: '供应商档案' })).toBeVisible()
  })
})

test.describe('基础数据 - 分类规则', () => {
  test('页面加载显示规则列表', async ({ page }) => {
    await mockAuth(page)
    await page.route(url => url.toString().includes('/v1/subjects/tree'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: [
          { id: 1, subjectCode: '1001', subjectName: '库存现金', children: [] },
        ]})
      })
    })
    await page.route(url => url.toString().includes('/sme/cash/v1/classification-rules'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: { records: [
          { id: 1, name: '默认入账规则', priority: 1, routeType: 'A', pattern: '.*' },
        ], total: 1, current: 1, size: 20 } })
      })
    })

    await page.goto(`${BASE}/system/classification-rule`, { waitUntil: 'networkidle', timeout: 15000 })
    await page.waitForTimeout(500)

    await expect(page.locator('.page-title').filter({ hasText: '分类规则管理' })).toBeVisible()
    await expect(page.locator('.el-table').getByText('默认入账规则')).toBeVisible()
  })
})

test.describe('基础数据 - 系统参数', () => {
  test('页面加载显示参数列表', async ({ page }) => {
    await mockAuth(page)
    await page.route(url => url.toString().includes('/v1/configs'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: { records: [
          { id: 1, configKey: 'system.name', configValue: '慧财财务', configType: 'SYSTEM', description: '系统名称' },
        ], total: 1, current: 1, size: 20 } })
      })
    })

    await page.goto(`${BASE}/basis/config`, { waitUntil: 'networkidle', timeout: 15000 })
    await page.waitForTimeout(500)

    await expect(page.getByRole('button', { name: /新增参数/i })).toBeVisible()
    await expect(page.locator('.el-table').getByText('system.name')).toBeVisible()
  })

  test('空列表显示空表格', async ({ page }) => {
    await mockAuth(page)
    await page.route(url => url.toString().includes('/v1/configs'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: { records: [], total: 0, current: 1, size: 20 } })
      })
    })

    await page.goto(`${BASE}/basis/config`, { waitUntil: 'networkidle', timeout: 15000 })
    await page.waitForTimeout(500)

    await expect(page.getByRole('button', { name: /新增参数/i })).toBeVisible()
    await expect(page.locator('.el-table__empty-text')).toBeVisible()
  })
})

test.describe('基础数据 - 数据维护', () => {
  async function mockClearDataApis(page: any) {
    await page.route(url => url.toString().includes('/sme/cash/v1/bank-statements/page'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: { records: [], total: 12 } })
      })
    })
    await page.route(url => url.toString().includes('/sme/arap/v1/business-docs/page'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: { records: [], total: 5 } })
      })
    })
    await page.route(url => url.toString().includes('/base/voucher/v1/vouchers/page'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: { records: [], total: 3 } })
      })
    })
    await page.route(url => url.toString().includes('/sme/arap/v1/receivables/page'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: { records: [], total: 7 } })
      })
    })
    await page.route(url => url.toString().includes('/sme/arap/v1/payables/page'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: { records: [], total: 9 } })
      })
    })
  }

  test('页面加载显示统计卡片', async ({ page }) => {
    await mockAuth(page)
    await mockClearDataApis(page)

    await page.goto(`${BASE}/system/clear-data`, { waitUntil: 'networkidle', timeout: 15000 })
    await page.waitForTimeout(500)

    await expect(page.locator('.page-title').filter({ hasText: '数据维护' })).toBeVisible()
    await expect(page.locator('.stat-card').filter({ hasText: '银行流水' }).first()).toBeVisible()
    await expect(page.locator('.stat-card').filter({ hasText: '发票记录' }).first()).toBeVisible()
    await expect(page.locator('.stat-card').filter({ hasText: '应收明细' }).first()).toBeVisible()
    await expect(page.locator('.stat-card').filter({ hasText: '应付明细' }).first()).toBeVisible()
    await expect(page.locator('.stat-card').filter({ hasText: '业务单据' }).first()).toBeVisible()
    await expect(page.locator('.stat-card').filter({ hasText: '生成凭证' }).first()).toBeVisible()
    await expect(page.getByText('数据清理')).toBeVisible()
  })

  test('统计数字正确渲染', async ({ page }) => {
    await mockAuth(page)
    await mockClearDataApis(page)

    await page.goto(`${BASE}/system/clear-data`, { waitUntil: 'networkidle', timeout: 15000 })
    await page.waitForTimeout(1000)

    await expect(page.locator('.stat-card').filter({ hasText: '银行流水' }).getByText('12')).toBeVisible()
    await expect(page.locator('.stat-card').filter({ hasText: '应收明细' }).getByText('7')).toBeVisible()
    await expect(page.locator('.stat-card').filter({ hasText: '生成凭证' }).getByText('3')).toBeVisible()
  })
})
