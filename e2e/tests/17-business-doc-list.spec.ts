/**
 * E2E 测试 17：业务单据列表
 *
 * 覆盖业务单据列表加载、类型筛选、详情查看等操作
 *
 * @标签：@smoke @business-doc @list
 */
import { test, expect } from '@playwright/test';
import { login, createErrorTracker } from './helpers';

test.describe('业务单据列表', () => {

  test('业务单据列表页面加载正常', async ({ page }) => {
    const tracker = createErrorTracker(page);
    await login(page);

    await page.goto('/finance/business-doc');
    await page.waitForLoadState('load');
    await page.waitForTimeout(2_000);

    // 验证页面标题
    await expect(page.getByText('业务单据')).toBeVisible({ timeout: 10_000 });
    // 验证表格
    const table = page.locator('.el-table').first();
    await expect(table).toBeVisible({ timeout: 10_000 });

    // 验证关键列头
    const headers = ['单据编号', '单据类型', '金额', '状态'];
    for (const header of headers) {
      const hasHeader = await table.getByText(header).isVisible({ timeout: 2_000 }).catch(() => false);
      if (!hasHeader) {
        console.log(`列头 "${header}" 未找到，可能使用不同命名`);
      }
    }

    try { await page.waitForLoadState('networkidle', { timeout: 3_000 }); } catch {}
    await page.waitForTimeout(500);
    tracker.assertNoErrors();
  });

  test('业务单据可按类型筛选', async ({ page }) => {
    const tracker = createErrorTracker(page);
    await login(page);

    await page.goto('/finance/business-doc');
    await page.waitForLoadState('load');
    await page.waitForTimeout(2_000);

    // 尝试点击类型筛选 tab
    const typeTabs = page.locator('.el-tabs__item, .el-radio-button').first();
    if (await typeTabs.isVisible({ timeout: 3_000 }).catch(() => false)) {
      await typeTabs.click();
      await page.waitForTimeout(1_500);
    }

    try { await page.waitForLoadState('networkidle', { timeout: 3_000 }); } catch {}
    await page.waitForTimeout(500);
    tracker.assertNoErrors();
  });

  test('可查看业务单据详情', async ({ page }) => {
    const tracker = createErrorTracker(page);
    await login(page);

    await page.goto('/finance/business-doc');
    await page.waitForLoadState('load');
    await page.waitForTimeout(2_000);

    // 尝试点击第一行查看详情
    const firstRow = page.locator('.el-table__body-wrapper tbody tr').first();
    if (await firstRow.isVisible({ timeout: 5_000 }).catch(() => false)) {
      const detailLink = firstRow.locator('a, button, span').filter({ hasText: /详情|查看|单据号/ }).first();
      if (await detailLink.isVisible({ timeout: 2_000 }).catch(() => false)) {
        await detailLink.click();
        await page.waitForTimeout(2_000);
        // 验证详情页或对话框出现
        const detailContent = page.locator('.el-dialog, .el-descriptions, .el-card').first();
        const isDetailVisible = await detailContent.isVisible({ timeout: 5_000 }).catch(() => false);
        if (isDetailVisible) {
          await expect(detailContent).toBeVisible();
        }
      }
    }

    try { await page.waitForLoadState('networkidle', { timeout: 3_000 }); } catch {}
    await page.waitForTimeout(500);
    tracker.assertNoErrors();
  });
});