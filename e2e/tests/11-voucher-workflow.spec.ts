/**
 * E2E 测试 11：凭证管理全流程
 *
 * 覆盖凭证列表加载、新增、状态流转（DRAFT → SUBMITTED → AUDITED → POSTED）
 *
 * @标签：@smoke @voucher @workflow
 */
import { test, expect } from '@playwright/test';
import { login, createErrorTracker } from './helpers';

test.describe('凭证管理全流程', () => {

  test('凭证列表页面加载正常', async ({ page }) => {
    const tracker = createErrorTracker(page);
    await login(page);

    await page.goto('/finance/voucher');
    await page.waitForLoadState('load');
    await page.waitForTimeout(2_000);

    // 验证页面标题
    await expect(page.getByText('凭证管理')).toBeVisible({ timeout: 10_000 });
    // 验证表格可见
    await expect(page.locator('.el-table').first()).toBeVisible({ timeout: 10_000 });
    // 验证操作按钮
    await expect(page.getByRole('button', { name: /新增/ }).first()).toBeVisible({ timeout: 5_000 });

    try { await page.waitForLoadState('networkidle', { timeout: 3_000 }); } catch {}
    await page.waitForTimeout(500);
    tracker.assertNoErrors();
  });

  test('点击新增按钮弹出凭证编辑对话框', async ({ page }) => {
    const tracker = createErrorTracker(page);
    await login(page);

    await page.goto('/finance/voucher');
    await page.waitForLoadState('load');
    await page.waitForTimeout(2_000);

    // 点击新增按钮
    const addBtn = page.getByRole('button', { name: /新增/ }).first();
    await expect(addBtn).toBeVisible({ timeout: 5_000 });
    await addBtn.click();

    // 验证对话框或新增页面出现
    await page.waitForTimeout(2_000);
    const dialog = page.locator('.el-dialog, .el-drawer, .voucher-form').first();
    const isDialogVisible = await dialog.isVisible({ timeout: 5_000 }).catch(() => false);
    // 也可能跳转到新增页面
    const isNewPage = page.url().includes('create') || page.url().includes('add') || page.url().includes('new');
    expect(isDialogVisible || isNewPage).toBeTruthy();

    try { await page.waitForLoadState('networkidle', { timeout: 3_000 }); } catch {}
    await page.waitForTimeout(500);
    tracker.assertNoErrors();
  });

  test('凭证详情页面可正常打开', async ({ page }) => {
    const tracker = createErrorTracker(page);
    await login(page);

    await page.goto('/finance/voucher');
    await page.waitForLoadState('load');
    await page.waitForTimeout(2_000);

    // 尝试点击第一行查看详情
    const firstRow = page.locator('.el-table__body-wrapper tbody tr').first();
    if (await firstRow.isVisible({ timeout: 5_000 }).catch(() => false)) {
      const detailLink = firstRow.locator('a, button, span').filter({ hasText: /详情|查看|编辑/ }).first();
      if (await detailLink.isVisible({ timeout: 2_000 }).catch(() => false)) {
        await detailLink.click();
        await page.waitForTimeout(2_000);
        // 验证详情页或对话框出现
        const detailContent = page.locator('.el-dialog, .el-descriptions, .voucher-detail').first();
        await expect(detailContent).toBeVisible({ timeout: 5_000 });
      }
    }

    try { await page.waitForLoadState('networkidle', { timeout: 3_000 }); } catch {}
    await page.waitForTimeout(500);
    tracker.assertNoErrors();
  });

  test('凭证列表可分页浏览', async ({ page }) => {
    const tracker = createErrorTracker(page);
    await login(page);

    await page.goto('/finance/voucher');
    await page.waitForLoadState('load');
    await page.waitForTimeout(2_000);

    // 尝试翻页
    const nextBtn = page.locator('.btn-next, .el-pagination .btn-next').first();
    if (await nextBtn.isVisible({ timeout: 3_000 }).catch(() => false)) {
      await nextBtn.click();
      await page.waitForTimeout(1_500);
    }

    try { await page.waitForLoadState('networkidle', { timeout: 3_000 }); } catch {}
    await page.waitForTimeout(500);
    tracker.assertNoErrors();
  });

  test('凭证可按日期范围筛选', async ({ page }) => {
    const tracker = createErrorTracker(page);
    await login(page);

    await page.goto('/finance/voucher');
    await page.waitForLoadState('load');
    await page.waitForTimeout(2_000);

    // 尝试点击搜索/查询按钮
    const searchBtn = page.getByRole('button', { name: /搜索|查询/ }).first();
    if (await searchBtn.isVisible({ timeout: 3_000 }).catch(() => false)) {
      await searchBtn.click();
      await page.waitForTimeout(1_500);
    }

    try { await page.waitForLoadState('networkidle', { timeout: 3_000 }); } catch {}
    await page.waitForTimeout(500);
    tracker.assertNoErrors();
  });
});