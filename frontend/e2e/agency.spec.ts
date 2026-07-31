import { test, expect } from '@playwright/test'

const BASE = 'http://localhost:3001'

async function mockAuth(page: any, userType = 'AGENCY', agencyRole = 'AGENCY_ADMIN') {
  await page.addInitScript(() => {
    localStorage.setItem('huicai_token', 'mock-token-for-e2e')
    localStorage.setItem('huicai_current_enterprise_id', '1')
  })
  await page.route(url => url.toString().includes('/api/v1/auth/userinfo'), async route => {
    await route.fulfill({ status: 200, contentType: 'application/json',
      body: JSON.stringify({ code: 200, msg: 'ok', data: {
        id: 1, username: 'agent01', realName: '代理会计', nickname: 'agent01',
        email: '', phone: '', avatar: '', deptId: 1, roles: [1],
        permissions: ['admin'],
        userType, agencyRole, agencyId: 1, enterpriseId: 1,
      }})
    })
  })
  await page.route(url => url.toString().includes('/api/v1/auth/agency-info'), async route => {
    await route.fulfill({ status: 200, contentType: 'application/json',
      body: JSON.stringify({ code: 200, msg: 'ok', data: { agencyId: 1, agencyName: '测试代理公司' } })
    })
  })
}

test.describe('代理 - 批量操作', () => {
  test('页面加载显示批量操作页签', async ({ page }) => {
    await mockAuth(page)

    await page.goto(`${BASE}/agency/batch-operation`, { waitUntil: 'networkidle', timeout: 15000 })
    await page.waitForTimeout(500)

    await expect(page.getByRole('heading', { name: '批量操作' })).toBeVisible()
    await expect(page.getByRole('tab', { name: /批量导入/i })).toBeVisible()
    await expect(page.getByRole('button', { name: /开始导入/i })).toBeVisible()
  })
})

test.describe('代理 - 会计管理', () => {
  test('页面加载显示会计列表', async ({ page }) => {
    await mockAuth(page)
    await page.route(url => url.toString().includes('/v1/agency/users'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: [
          { id: 1, realName: '张会计', username: 'zhang', agencyRole: 'ACCOUNTANT', status: 'ACTIVE' },
          { id: 2, realName: '李会计', username: 'li', agencyRole: 'REVIEWER', status: 'ACTIVE' },
        ]})
      })
    })

    await page.goto(`${BASE}/agency/accountant-list`, { waitUntil: 'networkidle', timeout: 15000 })
    await page.waitForTimeout(500)

    await expect(page.getByRole('heading', { name: '会计管理' })).toBeVisible()
    await expect(page.getByRole('button', { name: /新增会计/i })).toBeVisible()
    await expect(page.locator('.el-table').getByText('张会计')).toBeVisible()
    await expect(page.locator('.el-table').getByText('李会计')).toBeVisible()
  })
})

test.describe('代理 - 客户分配', () => {
  test('页面加载显示分配管理', async ({ page }) => {
    await mockAuth(page)
    await page.route(url => url.toString().includes('/v1/agency/users'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: [
          { id: 1, realName: '张会计', username: 'zhang', agencyRole: 'ACCOUNTANT', status: 'ACTIVE' },
        ]})
      })
    })

    await page.goto(`${BASE}/agency/assignment-manage`, { waitUntil: 'networkidle', timeout: 15000 })
    await page.waitForTimeout(500)

    await expect(page.getByRole('heading', { name: '客户分配' })).toBeVisible()
    await expect(page.getByText(/选择会计/).first()).toBeVisible()
  })
})

test.describe('代理 - 主管仪表盘', () => {
  test('页面加载显示统计卡片', async ({ page }) => {
    await mockAuth(page)
    await page.route(url => url.toString().includes('/v1/agency/dashboard'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: { data: {
          totalEnterprises: 3, activeEnterprises: 2, totalVouchersThisMonth: 5,
        }} })
      })
    })

    await page.goto(`${BASE}/agency/dashboard`, { waitUntil: 'networkidle', timeout: 15000 })
    await page.waitForTimeout(500)

    await expect(page.getByText('管理企业').first()).toBeVisible()
    await expect(page.getByText('活跃企业').first()).toBeVisible()
    await expect(page.getByText('本月凭证').first()).toBeVisible()
  })
})

test.describe('代理 - 会计详情', () => {
  test('页面加载显示负责企业列表', async ({ page }) => {
    await mockAuth(page)
    await page.route(url => url.toString().includes('/v1/agency/dashboard/accountant/1'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: { data: { enterprises: [
          { id: 1, enterpriseName: '测试企业A', taxId: '91110108MA001', status: 'ACTIVE', seedDataDone: true, createdAt: '2026-07-01' },
        ]} }})
      })
    })

    await page.goto(`${BASE}/agency/accountant-detail/1`, { waitUntil: 'networkidle', timeout: 15000 })
    await page.waitForTimeout(500)

    await expect(page.locator('.page-title').filter({ hasText: '会计详情' })).toBeVisible()
    await expect(page.locator('.el-table').getByText('测试企业A')).toBeVisible()
  })
})

test.describe('代理 - 角色门禁', () => {
  test('非 AGENCY_ADMIN 访问会计管理被重定向到 403', async ({ page }) => {
    await mockAuth(page, 'AGENCY', 'ACCOUNTANT')

    await page.goto(`${BASE}/agency/accountant-list`, { waitUntil: 'networkidle', timeout: 15000 })
    await page.waitForTimeout(500)

    await expect(page).toHaveURL(/403/)
  })
})
