import { test, expect } from '@playwright/test'

const BASE = 'http://localhost:3001'

test.describe('慧财财务 - Login Flow', () => {
  test('login page loads and shows login form', async ({ page }) => {
    await page.goto(`${BASE}/login`)
    await expect(page).toHaveTitle(/慧财|登录|Huicai/i)

    // Should have username/password fields and a login button
    const usernameInput = page.locator('input[type="text"]').first()
    const passwordInput = page.locator('input[type="password"]').first()
    const loginButton = page.locator('button').filter({ hasText: /登录|登 录/i })

    await expect(usernameInput).toBeVisible({ timeout: 10000 })
    await expect(passwordInput).toBeVisible()
    await expect(loginButton).toBeVisible()
  })

  test('login with admin credentials succeeds', async ({ page }) => {
    await page.goto(`${BASE}/login`)
    await page.waitForTimeout(1000)

    // Fill login form
    const usernameInput = page.locator('input[type="text"]').first()
    const passwordInput = page.locator('input[type="password"]').first()
    const loginButton = page.locator('button').filter({ hasText: /登录|登 录/i })

    await usernameInput.fill('admin')
    await passwordInput.fill('admin123')
    await loginButton.click()

    // After successful login, should redirect away from /login
    await page.waitForURL((url) => !url.pathname.includes('/login'), { timeout: 15000 })
    expect(page.url()).not.toContain('/login')
  })

  test('protected route redirects to login when unauthenticated', async ({ page }) => {
    await page.goto(`${BASE}/system/user`)
    await page.waitForTimeout(2000)

    // Should redirect to login
    expect(page.url()).toContain('/login')
  })
})
