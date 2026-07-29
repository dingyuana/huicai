import { test, expect } from '@playwright/test'

const BASE = 'http://localhost:3001'

test.describe('固定资产 - 资产卡片', () => {
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
    // Mock 类别列表
    await page.route(url => url.toString().includes('/sme/asset/v1/asset-categories/list'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: [] })
      })
    })
    await page.route(url => url.toString().includes('/sme/asset/v1/asset-cards/page'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: { records: [], total: 0, current: 1, size: 20 } })
      })
    })

    await page.goto(`${BASE}/asset/card`, { waitUntil: 'networkidle', timeout: 15000 })
    await page.waitForTimeout(500)

    await expect(page.locator('.page-title')).toHaveText('资产卡片', { timeout: 10000 })
    await expect(page.getByRole('button', { name: /新增资产/i })).toBeVisible()
    await expect(page.getByRole('button', { name: /一键计提折旧/i })).toBeVisible()
    await expect(page.locator('th').filter({ hasText: '资产编码' })).toBeVisible()
    await expect(page.locator('th').filter({ hasText: '资产名称' })).toBeVisible()
    await expect(page.locator('th').filter({ hasText: '原值' })).toBeVisible()
    await expect(page.locator('th').filter({ hasText: '累计折旧' })).toBeVisible()
    await expect(page.locator('th').filter({ hasText: '净值' })).toBeVisible()
  })

  test('表格显示资产卡片数据', async ({ page }) => {
    await mockAuth(page)
    await page.route(url => url.toString().includes('/sme/asset/v1/asset-categories/list'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: [
          { id: 1, code: '01', name: '房屋及建筑物', depreciationMethod: 'STRAIGHT_LINE', usefulLife: 20, residualRate: 0.05 },
        ]})
      })
    })
    await page.route(url => url.toString().includes('/sme/asset/v1/asset-cards/page'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: { records: [
          { id: 1, assetCode: 'ZC-001', assetName: '办公电脑', originalValue: 8000, accumulatedDepreciation: 1600, netValue: 6400, status: 'IN_USE', acquisitionDate: '2026-01-15' },
          { id: 2, assetCode: 'ZC-002', assetName: '服务器', originalValue: 50000, accumulatedDepreciation: 5000, netValue: 45000, status: 'IN_USE', acquisitionDate: '2026-03-01' },
        ], total: 2, current: 1, size: 20 }})
      })
    })

    await page.goto(`${BASE}/asset/card`, { waitUntil: 'networkidle', timeout: 15000 })
    await page.waitForTimeout(500)

    await expect(page.getByText('ZC-001')).toBeVisible()
    await expect(page.getByText('ZC-002')).toBeVisible()
    await expect(page.getByText('办公电脑')).toBeVisible()
    await expect(page.getByText('服务器')).toBeVisible()
    await expect(page.getByText('8000.00')).toBeVisible()
    await expect(page.getByText('50000.00')).toBeVisible()
    await expect(page.getByText('6400.00')).toBeVisible()
  })

  test('点击新增资产打开弹窗', async ({ page }) => {
    await mockAuth(page)
    await page.route(url => url.toString().includes('/sme/asset/v1/asset-categories/list'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: [
          { id: 1, code: '02', name: '机器设备', depreciationMethod: 'STRAIGHT_LINE', usefulLife: 10, residualRate: 0.05 },
        ]})
      })
    })
    await page.route(url => url.toString().includes('/sme/asset/v1/asset-cards/page'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: { records: [], total: 0, current: 1, size: 20 } })
      })
    })

    await page.goto(`${BASE}/asset/card`, { waitUntil: 'networkidle', timeout: 15000 })
    await page.waitForTimeout(500)

    await page.getByRole('button', { name: /新增资产/i }).click()
    await page.waitForTimeout(500)

    await expect(page.getByText('新增资产').first()).toBeVisible({ timeout: 5000 })
    await expect(page.locator('label').filter({ hasText: '资产编码' })).toBeVisible()
    await expect(page.locator('label').filter({ hasText: '资产名称' })).toBeVisible()
    await expect(page.locator('label').filter({ hasText: '类别' })).toBeVisible()
    await expect(page.locator('label').filter({ hasText: '购置日期' })).toBeVisible()
    await expect(page.locator('label').filter({ hasText: '原值' })).toBeVisible()
    await expect(page.locator('label').filter({ hasText: '使用年限' })).toBeVisible()
  })
})