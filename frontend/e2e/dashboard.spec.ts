import { test, expect } from '@playwright/test'

const BASE = 'http://localhost:3001'

test.describe('首页 - Dashboard', () => {
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

  test('页面加载显示欢迎标题和统计卡片', async ({ page }) => {
    await mockAuth(page)
    await page.goto(`${BASE}/dashboard`, { waitUntil: 'networkidle', timeout: 15000 })
    await page.waitForTimeout(500)

    await expect(page.locator('h2')).toHaveText('欢迎使用慧财智能财务平台', { timeout: 10000 })
    await expect(page.getByText('本月流水')).toBeVisible()
    await expect(page.getByText('待审核凭证')).toBeVisible()
    await expect(page.getByText('本月净利润')).toBeVisible()
    await expect(page.getByText('待处理流水')).toBeVisible()
  })

  test('统计卡片值显示占位符', async ({ page }) => {
    await mockAuth(page)
    await page.goto(`${BASE}/dashboard`, { waitUntil: 'networkidle', timeout: 15000 })
    await page.waitForTimeout(500)

    await expect(page.getByText('-- 笔')).toBeVisible()
    await expect(page.getByText('-- 张')).toBeVisible()
    await expect(page.getByText('¥--')).toBeVisible()
    await expect(page.getByText('-- 条')).toBeVisible()
  })

  test('快速入口按钮显示', async ({ page }) => {
    await mockAuth(page)
    await page.goto(`${BASE}/dashboard`, { waitUntil: 'networkidle', timeout: 15000 })
    await page.waitForTimeout(500)

    await expect(page.getByRole('button', { name: /导入银行流水/i })).toBeVisible()
    await expect(page.getByRole('button', { name: /新增凭证/i })).toBeVisible()
    await expect(page.getByRole('button', { name: /结账体检/i })).toBeVisible()
  })
})
