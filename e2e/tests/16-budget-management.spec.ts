/**
 * E2E 测试 16：预算管理
 *
 * 覆盖预算列表、预算调整、预算审批等操作
 *
 * @标签：@smoke @budget @management
 */
import { test, expect } from '@playwright/test';
import { login, createErrorTracker } from './helpers';

test.describe('预算管理', () => {

  test('预算列表页面加载正常', async ({ page }) => {
    const tracker = createErrorTracker(page);
    await login(page);

    await page.goto('/budget/list');
    await page.waitForLoadState('load');
    await page.waitForTimeout(2_000);

    // 验证页面标题
    await expect(page.getByText('预算管理').or(page.getByText('预算列表'))).toBeVisible({ timeout: 10_000 });
    // 验证主内容区域
    const mainContent = page.locator('.el-table, .el-card, main, .app-main').first();
    await expect(mainContent).toBeVisible({ timeout: 10_000 });

    try { await page.waitForLoadState('networkidle', { timeout: 3_000 }); } catch {}
    await page.waitForTimeout(500);
    tracker.assertNoErrors();
  });

  test('预算调整页面加载正常', async ({ page }) => {
    const tracker = createErrorTracker(page);
    await login(page);

    // 尝试多个可能的预算调整路径
    const paths = ['/budget/adjustment', '/budget/adjust', '/budget/list?tab=adjustment'];
    let loaded = false;
    for (const path of paths) {
      await page.goto(path);
      await page.waitForLoadState('load');
      await page.waitForTimeout(1_500);
      const mainContent = page.locator('.el-card, main, .app-main').first();
      if (await mainContent.isVisible({ timeout: 3_000 }).catch(() => false)) {
        loaded = true;
        break;
      }
    }
    expect(loaded, '预算调整页面应能加载').toBeTruthy();

    try { await page.waitForLoadState('networkidle', { timeout: 3_000 }); } catch {}
    await page.waitForTimeout(500);
    tracker.assertNoErrors();
  });

  test('预算审批流程页面可访问', async ({ page }) => {
    const tracker = createErrorTracker(page);
    await login(page);

    // 尝试多个可能的预算审批路径
    const paths = ['/budget/approval', '/budget/approve', '/budget/list?tab=approval'];
    let loaded = false;
    for (const path of paths) {
      await page.goto(path);
      await page.waitForLoadState('load');
      await page.waitForTimeout(1_500);
      const mainContent = page.locator('.el-card, main, .app-main').first();
      if (await mainContent.isVisible({ timeout: 3_000 }).catch(() => false)) {
        loaded = true;
        break;
      }
    }
    expect(loaded, '预算审批页面应能加载').toBeTruthy();

    try { await page.waitForLoadState('networkidle', { timeout: 3_000 }); } catch {}
    await page.waitForTimeout(500);
    tracker.assertNoErrors();
  });
});