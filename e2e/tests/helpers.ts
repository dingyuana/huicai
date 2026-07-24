/**
 * E2E 测试共享工具：登录、断言、截图等
 */
import { Page, expect } from '@playwright/test';

export const CREDENTIALS = {
  username: 'admin',
  password: 'admin123',
};

/**
 * 登录到慧财财务系统
 */
export async function login(page: Page) {
  await page.goto('/login');
  await page.getByPlaceholder('账号').fill(CREDENTIALS.username);
  await page.getByPlaceholder('密码').fill(CREDENTIALS.password);
  await page.getByRole('button', { name: /登.*录/ }).click();
  // 等待跳转到首页（URL 包含 /dashboard 或出现欢迎文字）
  await page.waitForURL(/\/dashboard|\/#/);
  await expect(page).toHaveURL(/\/dashboard|\/#/);
}

/**
 * 等待页面主内容加载完成（无加载指示器）
 */
export async function waitForPageReady(page: Page) {
  // 等待所有网络请求完成（最长 5 秒）
  await page.waitForLoadState('networkidle', { timeout: 5_000 }).catch(() => {});
}

/**
 * 验证页面没有 500/系统繁忙错误
 */
export async function expectNoServerErrors(page: Page) {
  // 等待页面稳定
  await page.waitForLoadState('domcontentloaded');
  // 检查页面是否出现 500 / 系统繁忙提示
  await expect(page.locator('text=系统繁忙')).not.toBeVisible({ timeout: 5_000 });
  await expect(page.locator('text=500')).not.toBeVisible({ timeout: 5_000 });
  await expect(page.locator('text=Request failed with status code 500')).not.toBeVisible({ timeout: 5_000 });
}
