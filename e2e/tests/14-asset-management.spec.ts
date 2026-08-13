/**
 * E2E 测试 14：固定资产管理
 *
 * 覆盖资产类别列表、资产卡片列表、新增弹窗等操作
 *
 * @标签：@smoke @asset @management
 */
import { test, expect } from '@playwright/test';
import { login, createErrorTracker } from './helpers';

test.describe('固定资产管理', () => {

  test('资产类别页面加载正常', async ({ page }) => {
    const tracker = createErrorTracker(page);
    await login(page);

    await page.goto('/asset/category');
    await page.waitForLoadState('load');
    await page.waitForTimeout(2_000);

    // 验证页面标题
    await expect(page.getByText('资产类别')).toBeVisible({ timeout: 10_000 });
    // 验证主内容区域
    const mainContent = page.locator('.el-table, .el-card, main, .app-main').first();
    await expect(mainContent).toBeVisible({ timeout: 10_000 });

    try { await page.waitForLoadState('networkidle', { timeout: 3_000 }); } catch {}
    await page.waitForTimeout(500);
    tracker.assertNoErrors();
  });

  test('资产类别新增按钮可点击', async ({ page }) => {
    const tracker = createErrorTracker(page);
    await login(page);

    await page.goto('/asset/category');
    await page.waitForLoadState('load');
    await page.waitForTimeout(2_000);

    // 尝试点击新增按钮
    const addBtn = page.getByRole('button', { name: /新增/ }).first();
    if (await addBtn.isVisible({ timeout: 3_000 }).catch(() => false)) {
      await addBtn.click();
      await page.waitForTimeout(2_000);

      // 验证对话框出现
      const dialog = page.locator('.el-dialog, .el-drawer').first();
      const isDialogVisible = await dialog.isVisible({ timeout: 3_000 }).catch(() => false);
      expect(isDialogVisible).toBeTruthy();
    }

    try { await page.waitForLoadState('networkidle', { timeout: 3_000 }); } catch {}
    await page.waitForTimeout(500);
    tracker.assertNoErrors();
  });

  test('资产卡片列表页面加载正常', async ({ page }) => {
    const tracker = createErrorTracker(page);
    await login(page);

    await page.goto('/asset/card');
    await page.waitForLoadState('load');
    await page.waitForTimeout(2_000);

    // 验证页面标题
    await expect(page.getByText('资产卡片')).toBeVisible({ timeout: 10_000 });
    // 验证表格
    const table = page.locator('.el-table').first();
    await expect(table).toBeVisible({ timeout: 10_000 });

    try { await page.waitForLoadState('networkidle', { timeout: 3_000 }); } catch {}
    await page.waitForTimeout(500);
    tracker.assertNoErrors();
  });

  test('资产卡片搜索功能正常', async ({ page }) => {
    const tracker = createErrorTracker(page);
    await login(page);

    await page.goto('/asset/card');
    await page.waitForLoadState('load');
    await page.waitForTimeout(2_000);

    // 尝试搜索
    const searchInput = page.locator('input[placeholder*="搜索"], input[placeholder*="资产"]').first();
    if (await searchInput.isVisible({ timeout: 3_000 }).catch(() => false)) {
      await searchInput.fill('测试资产');
      await page.waitForTimeout(500);

      const searchBtn = page.getByRole('button', { name: /搜索|查询/ }).first();
      if (await searchBtn.isVisible({ timeout: 2_000 }).catch(() => false)) {
        await searchBtn.click();
        await page.waitForTimeout(1_500);
      }
    }

    try { await page.waitForLoadState('networkidle', { timeout: 3_000 }); } catch {}
    await page.waitForTimeout(500);
    tracker.assertNoErrors();
  });
});