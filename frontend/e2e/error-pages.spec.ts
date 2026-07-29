import { test, expect } from '@playwright/test'

const BASE = 'http://localhost:3001'

test.describe('异常页面', () => {
  test('/403 显示无权限提示', async ({ page }) => {
    await page.goto(`${BASE}/403`, { waitUntil: 'networkidle', timeout: 15000 })
    await page.waitForTimeout(500)

    // 403 页面应有禁止访问提示
    const body = page.locator('body')
    await expect(body).toContainText(/403|无权限|禁止访问|Forbidden/i, { timeout: 10000 })
  })

  test('/404 显示页面不存在提示', async ({ page }) => {
    await page.goto(`${BASE}/404`, { waitUntil: 'networkidle', timeout: 15000 })
    await page.waitForTimeout(500)

    const body = page.locator('body')
    await expect(body).toContainText(/404|页面不存在|Not Found|抱歉/i, { timeout: 10000 })
  })

  test('不存在的路由重定向到 404', async ({ page }) => {
    await page.goto(`${BASE}/this-page-does-not-exist`, { waitUntil: 'networkidle', timeout: 15000 })
    await page.waitForTimeout(500)

    // 应显示 404 页面
    const body = page.locator('body')
    await expect(body).toContainText(/404|页面不存在|Not Found|抱歉/i, { timeout: 10000 })
  })

  test('未登录访问受保护页面跳转到登录', async ({ page }) => {
    // 不 mock auth，直接访问受保护页面
    await page.goto(`${BASE}/system/user`, { waitUntil: 'networkidle', timeout: 15000 })
    await page.waitForTimeout(2000)

    // 应跳转到登录页
    expect(page.url()).toContain('/login')
  })
})