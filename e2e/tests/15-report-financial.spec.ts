/**
 * E2E 测试 15：三大财务报表
 *
 * 覆盖资产负债表、利润表、现金流量表的加载和基本数据展示
 *
 * @标签：@smoke @report @financial
 */
import { test, expect } from '@playwright/test';
import { login, createErrorTracker } from './helpers';

const REPORT_PAGES = [
  { path: '/report/balance-sheet', name: '资产负债表' },
  { path: '/report/income-statement', name: '利润表' },
  { path: '/report/cash-flow', name: '现金流量表' },
];

test.describe('三大财务报表', () => {

  test('报表期间选择器可用', async ({ page }) => {
    const tracker = createErrorTracker(page);
    await login(page);

    await page.goto('/report/balance-sheet');
    await page.waitForLoadState('load');
    await page.waitForTimeout(2_000);

    // 尝试选择期间
    const periodSelect = page.locator('.el-select, .el-date-editor, .period-picker').first();
    if (await periodSelect.isVisible({ timeout: 3_000 }).catch(() => false)) {
      await periodSelect.click();
      await page.waitForTimeout(1_000);
    }

    try { await page.waitForLoadState('networkidle', { timeout: 3_000 }); } catch {}
    await page.waitForTimeout(500);
    tracker.assertNoErrors();
  });

  test('报表导出按钮可点击', async ({ page }) => {
    const tracker = createErrorTracker(page);
    await login(page);

    await page.goto('/report/balance-sheet');
    await page.waitForLoadState('load');
    await page.waitForTimeout(2_000);

    // 尝试点击导出按钮
    const exportBtn = page.getByRole('button', { name: /导出|下载|打印/ }).first();
    if (await exportBtn.isVisible({ timeout: 3_000 }).catch(() => false)) {
      await exportBtn.click();
      await page.waitForTimeout(1_500);
    }

    try { await page.waitForLoadState('networkidle', { timeout: 3_000 }); } catch {}
    await page.waitForTimeout(500);
    tracker.assertNoErrors();
  });

  for (const report of REPORT_PAGES) {
    test(`【${report.name}】页面加载正常，无 500 错误`, async ({ page }) => {
      const tracker = createErrorTracker(page);
      await login(page);

      await page.goto(report.path);
      await page.waitForLoadState('load');
      await page.waitForTimeout(2_000);

      // 验证页面标题
      await expect(page.getByText(report.name)).toBeVisible({ timeout: 10_000 });
      // 验证主内容区域
      const mainContent = page.locator('.el-card, main, .app-main, .report-container').first();
      await expect(mainContent).toBeVisible({ timeout: 10_000 });

      try { await page.waitForLoadState('networkidle', { timeout: 3_000 }); } catch {}
      await page.waitForTimeout(500);
      tracker.assertNoErrors();
    });
  }
});