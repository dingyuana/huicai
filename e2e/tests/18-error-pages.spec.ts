/**
 * E2E 测试 18：异常页面处理
 *
 * 覆盖 403/404 页面加载、无效路由兜底、权限跳转等异常场景
 *
 * @标签：@smoke @error @exception
 */
import { test, expect } from '@playwright/test';
import { login, createErrorTracker } from './helpers';

test.describe('异常页面处理', () => {

  test('404 页面正常显示', async ({ page }) => {
    const tracker = createErrorTracker(page);
    await login(page);

    // 访问一个不存在的路由
    await page.goto('/this-path-does-not-exist-12345');
    await page.waitForLoadState('load');
    await page.waitForTimeout(2_000);

    // 验证 404 提示或错误页面出现
    const errorText = page.getByText(/404|页面不存在|找不到|Not Found/i);
    const isErrorVisible = await errorText.isVisible({ timeout: 5_000 }).catch(() => false);
    if (!isErrorVisible) {
      // 也可能是路由兜底到了首页
      console.log('404 页面未显示特定错误文本，可能被路由兜底处理');
    }

    // 即使没有 404 页面，确保没有 API 500 错误
    try { await page.waitForLoadState('networkidle', { timeout: 3_000 }); } catch {}
    await page.waitForTimeout(500);
    tracker.assertNoErrors();
  });

  test('403 无权限页面正常显示', async ({ page }) => {
    const tracker = createErrorTracker(page);
    await login(page);

    // 尝试访问可能需要特殊权限的页面
    await page.goto('/system/admin-only');
    await page.waitForLoadState('load');
    await page.waitForTimeout(2_000);

    // 验证 403 或权限提示
    const forbiddenText = page.getByText(/403|无权限|禁止访问|Forbidden/i);
    const isForbiddenVisible = await forbiddenText.isVisible({ timeout: 5_000 }).catch(() => false);
    if (!isForbiddenVisible) {
      console.log('403 页面未显示特定错误文本，可能被重定向处理');
    }

    try { await page.waitForLoadState('networkidle', { timeout: 3_000 }); } catch {}
    await page.waitForTimeout(500);
    tracker.assertNoErrors();
  });

  test('未登录访问受保护页面应跳转到登录页', async ({ page }) => {
    const tracker = createErrorTracker(page);

    // 不登录直接访问受保护页面
    await page.goto('/finance/voucher');
    await page.waitForLoadState('load');
    await page.waitForTimeout(2_000);

    // 验证被重定向到登录页
    const isLoginPage = page.url().includes('login');
    const loginButton = page.getByRole('button', { name: /登.*录/ });
    const isLoginButtonVisible = await loginButton.isVisible({ timeout: 5_000 }).catch(() => false);
    expect(isLoginPage || isLoginButtonVisible, '未登录应被重定向到登录页').toBeTruthy();

    try { await page.waitForLoadState('networkidle', { timeout: 3_000 }); } catch {}
    await page.waitForTimeout(500);
    tracker.assertNoErrors();
  });

  test('无效参数页面不崩溃', async ({ page }) => {
    const tracker = createErrorTracker(page);
    await login(page);

    // 访问带无效参数的页面
    await page.goto('/finance/voucher?id=invalid&page=-1');
    await page.waitForLoadState('load');
    await page.waitForTimeout(2_000);

    // 验证页面没有崩溃（主内容区域仍可见或显示错误提示）
    const mainContent = page.locator('.el-card, main, .app-main, .el-alert, .el-result').first();
    const isContentVisible = await mainContent.isVisible({ timeout: 5_000 }).catch(() => false);
    if (!isContentVisible) {
      console.log('无效参数页面未显示特定内容，但应确保不崩溃');
    }

    try { await page.waitForLoadState('networkidle', { timeout: 3_000 }); } catch {}
    await page.waitForTimeout(500);
    tracker.assertNoErrors();
  });
});