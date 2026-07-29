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
 * 验证页面没有 500 / 系统繁忙错误（旧版，仅检查页面文本）
 */
export async function expectNoServerErrors(page: Page) {
  // 检查页面是否出现"系统繁忙"提示
  await expect(page.locator('text=系统繁忙')).not.toBeVisible({ timeout: 5_000 });
  // 检查页面是否出现"Request failed with status code 500"
  await expect(page.locator('text=Request failed with status code 500')).not.toBeVisible({ timeout: 5_000 });
}

/**
 * 创建 API 500 错误 + 控制台错误追踪器
 * 在网络层面拦截 /api/ 的 500 响应和 console.error，不依赖页面文本
 * 必须在 page.goto / page.click 等操作前注册
 *
 * 用法：
 *   const tracker = createErrorTracker(page);
 *   await page.goto('/some-page');
 *   await page.waitForLoadState('networkidle');
 *   tracker.assertNoErrors();
 */
export function createErrorTracker(page: Page) {
  const apiErrors: { url: string; status: number }[] = [];
  const consoleErrors: { level: string; text: string }[] = [];
  const pageErrors: { message: string; source?: string }[] = [];

  page.on('response', (res) => {
    if (res.status() >= 500 && res.url().includes('/api/')) {
      apiErrors.push({ url: res.url(), status: res.status() });
    }
  });

  page.on('console', (msg) => {
    if (msg.type() === 'error') {
      consoleErrors.push({ level: msg.type(), text: msg.text() });
    }
  });

  page.on('pageerror', (err) => {
    pageErrors.push({ message: err.message });
  });

  return {
    apiErrors: () => apiErrors,
    consoleErrors: () => consoleErrors,
    pageErrors: () => pageErrors,
    assertNoErrors: () => {
      const allErrors = [
        ...apiErrors.map(e => `API ${e.status}: ${e.url}`),
        ...consoleErrors.map(e => `Console ${e.level}: ${e.text}`),
        ...pageErrors.map(e => `PageError: ${e.message}`),
      ];
      expect(allErrors, `发现 ${allErrors.length} 个错误:\n${allErrors.join('\n')}`).toHaveLength(0);
    },
  };
}
