import { test, expect } from '@playwright/test'

const BASE = 'http://localhost:3001'

test.describe('固定资产 - 资产类别', () => {
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
    await page.route(url => url.toString().includes('/sme/asset/v1/asset-categories/page'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: { records: [], total: 0, current: 1, size: 20 } })
      })
    })

    await page.goto(`${BASE}/asset/category`, { waitUntil: 'networkidle', timeout: 15000 })
    await page.waitForTimeout(500)

    await expect(page.locator('.page-title')).toHaveText('资产类别', { timeout: 10000 })
    await expect(page.getByRole('button', { name: /新增类别/i })).toBeVisible()
    await expect(page.locator('th').filter({ hasText: '编码' })).toBeVisible()
    await expect(page.locator('th').filter({ hasText: '名称' })).toBeVisible()
    await expect(page.locator('th').filter({ hasText: '折旧方法' })).toBeVisible()
    await expect(page.locator('th').filter({ hasText: '使用年限' })).toBeVisible()
  })

  test('表格显示类别数据', async ({ page }) => {
    await mockAuth(page)
    await page.route(url => url.toString().includes('/sme/asset/v1/asset-categories/page'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: { records: [
          { id: 1, code: '01', name: '房屋及建筑物', depreciationMethod: 'STRAIGHT_LINE', usefulLife: 20, residualRate: 0.05 },
          { id: 2, code: '02', name: '机器设备', depreciationMethod: 'DOUBLE_DECLINING', usefulLife: 10, residualRate: 0.05 },
        ], total: 2, current: 1, size: 20 }})
      })
    })

    await page.goto(`${BASE}/asset/category`, { waitUntil: 'networkidle', timeout: 15000 })
    await page.waitForTimeout(500)

    await expect(page.getByText('房屋及建筑物')).toBeVisible()
    await expect(page.getByText('机器设备')).toBeVisible()
    await expect(page.getByText('平均年限法')).toBeVisible()
    await expect(page.getByText('双倍余额递减法')).toBeVisible()
  })

  test('新增类别弹窗显示表单', async ({ page }) => {
    await mockAuth(page)
    await page.route(url => url.toString().includes('/sme/asset/v1/asset-categories/page'), async route => {
      await route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ code: 200, msg: 'ok', data: { records: [], total: 0, current: 1, size: 20 } })
      })
    })

    await page.goto(`${BASE}/asset/category`, { waitUntil: 'networkidle', timeout: 15000 })
    await page.waitForTimeout(500)

    await page.getByRole('button', { name: /新增类别/i }).click()
    await page.waitForTimeout(500)

    await expect(page.getByText('新增类别').first()).toBeVisible({ timeout: 5000 })
    await expect(page.locator('label').filter({ hasText: '编码' })).toBeVisible()
    await expect(page.locator('label').filter({ hasText: '名称' })).toBeVisible()
    await expect(page.locator('label').filter({ hasText: '折旧方法' })).toBeVisible()
    await expect(page.locator('label').filter({ hasText: '使用年限' })).toBeVisible()
    await expect(page.locator('label').filter({ hasText: '残值率' })).toBeVisible()
  })
})