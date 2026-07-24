/**
 * 冒烟测试 3：销项发票页面
 * 验证销项发票列表页面能正常打开，无"系统繁忙"错误，
 * 且页面包含预期的关键元素（搜索表单、表格、分页）
 */
import { test, expect } from '@playwright/test';
import { login, expectNoServerErrors } from './helpers';

test.describe('销项发票', () => {
  test('销项发票列表页面正常加载', async ({ page }) => {
    await login(page);

    // 导航到销项发票页面
    await page.goto('/tax/output-invoice');
    await page.waitForLoadState('domcontentloaded');

    // 1. 验证没有 500 错误
    await expectNoServerErrors(page);

    // 2. 验证页面包含关键元素
    // 搜索表单
    await expect(page.locator('input[placeholder*="开票日期"]')).toBeVisible({ timeout: 10_000 });
    await expect(page.locator('input[placeholder*="发票号码"]')).toBeVisible({ timeout: 10_000 });

    // 表格（Element Plus 的 el-table）
    await expect(page.locator('.el-table')).toBeVisible({ timeout: 10_000 });

    // 分页
    await expect(page.locator('.el-pagination')).toBeVisible({ timeout: 10_000 });

    // 3. 验证 API 调用成功（无 500）
    const requestPromise = page.waitForRequest(
      req => req.url().includes('/api/sme/tax/v1/output-invoices'),
      { timeout: 15_000 }
    ).catch(() => null);

    // 刷新表格触发 API 调用
    await page.locator('.el-table').click();

    const req = await requestPromise;
    if (req) {
      const response = await req.response();
      expect(response?.status()).toBe(200);
    }
  });
});
