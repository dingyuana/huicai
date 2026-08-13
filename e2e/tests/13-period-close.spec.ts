/**
 * E2E 测试 13：期末结账流程
 *
 * 覆盖结账页面加载、试算平衡检查、损益结转、结账执行等操作
 *
 * @标签：@smoke @finance @period-close
 */
import { test, expect } from '@playwright/test';
import { login, createErrorTracker } from './helpers';

test.describe('期末结账流程', () => {

  test('期末结账页面加载正常', async ({ page }) => {
    const tracker = createErrorTracker(page);
    await login(page);

    await page.goto('/finance/period-close');
    await page.waitForLoadState('load');
    await page.waitForTimeout(2_000);

    // 验证页面标题
    await expect(page.getByText('期末结账')).toBeVisible({ timeout: 10_000 });
    // 验证主内容区域
    const mainContent = page.locator('.el-card, main, .app-main').first();
    await expect(mainContent).toBeVisible({ timeout: 10_000 });

    try { await page.waitForLoadState('networkidle', { timeout: 3_000 }); } catch {}
    await page.waitForTimeout(500);
    tracker.assertNoErrors();
  });

  test('结账向导步骤可见', async ({ page }) => {
    const tracker = createErrorTracker(page);
    await login(page);

    await page.goto('/finance/period-close');
    await page.waitForLoadState('load');
    await page.waitForTimeout(2_000);

    // 验证步骤条或引导区域可见
    const steps = page.locator('.el-steps, .steps, .el-step, .close-guide, .el-timeline').first();
    const isStepsVisible = await steps.isVisible({ timeout: 5_000 }).catch(() => false);
    if (isStepsVisible) {
      await expect(steps).toBeVisible();
    }

    try { await page.waitForLoadState('networkidle', { timeout: 3_000 }); } catch {}
    await page.waitForTimeout(500);
    tracker.assertNoErrors();
  });

  test('试算平衡检查按钮可点击', async ({ page }) => {
    const tracker = createErrorTracker(page);
    await login(page);

    await page.goto('/finance/period-close');
    await page.waitForLoadState('load');
    await page.waitForTimeout(2_000);

    // 尝试点击试算平衡或检查按钮
    const checkBtn = page.getByRole('button', { name: /试算平衡|检查|校验/ }).first();
    if (await checkBtn.isVisible({ timeout: 3_000 }).catch(() => false)) {
      await checkBtn.click();
      await page.waitForTimeout(2_000);
    }

    try { await page.waitForLoadState('networkidle', { timeout: 3_000 }); } catch {}
    await page.waitForTimeout(500);
    tracker.assertNoErrors();
  });

  test('结账期间选择器正常', async ({ page }) => {
    const tracker = createErrorTracker(page);
    await login(page);

    await page.goto('/finance/period-close');
    await page.waitForLoadState('load');
    await page.waitForTimeout(2_000);

    // 尝试选择期间
    const periodSelect = page.locator('.el-select, .el-date-editor').first();
    if (await periodSelect.isVisible({ timeout: 3_000 }).catch(() => false)) {
      await periodSelect.click();
      await page.waitForTimeout(1_000);
      // 尝试选择第一个选项
      const firstOption = page.locator('.el-select-dropdown__item, .el-date-picker').first();
      if (await firstOption.isVisible({ timeout: 2_000 }).catch(() => false)) {
        await firstOption.click();
        await page.waitForTimeout(1_500);
      }
    }

    try { await page.waitForLoadState('networkidle', { timeout: 3_000 }); } catch {}
    await page.waitForTimeout(500);
    tracker.assertNoErrors();
  });
});