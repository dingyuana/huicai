/**
 * 冒烟测试 1：登录
 * 验证 admin/admin123 可以正常登录并跳转到首页
 * 
 * @标签：@smoke
 */
import { test, expect } from '@playwright/test';
import { login, createErrorTracker } from './helpers';

test.describe('登录', () => {
  test('admin/admin123 登录成功', async ({ page }) => {
    const tracker = createErrorTracker(page);
    await login(page);
    // 验证首页标题出现
    await expect(page.getByRole('heading', { name: '欢迎使用慧财智能财务平台' })).toBeVisible();
    // 等待异步请求完成
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(1_000);
    tracker.assertNoErrors();
  });

  test('登录失败（错误密码）', async ({ page }) => {
    await page.goto('/login');
    await page.getByPlaceholder('账号').fill('admin');
    await page.getByPlaceholder('密码').fill('wrongpassword');
    await page.getByRole('button', { name: /登.*录/ }).click();
    // 等待登录失败提示（仍停留在登录页）
    await expect(page).toHaveURL(/\/login/);
    // 验证出现错误提示
    await expect(page.getByText('用户名或密码错误')).toBeVisible({ timeout: 5_000 });
  });
});
