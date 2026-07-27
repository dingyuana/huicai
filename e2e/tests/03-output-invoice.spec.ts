/**
 * 冒烟测试 3：销项发票页面
 * 验证销项发票列表页面能正常打开，无"系统繁忙"错误，
 * 且页面包含预期的关键元素（搜索表单、表格、统计卡片）
 */
import { test, expect } from '@playwright/test';
import { login, createErrorTracker } from './helpers';

test.describe('销项发票', () => {
  test('销项发票列表页面正常加载', async ({ page }) => {
    const tracker = createErrorTracker(page);
    await login(page);

    // 导航到销项发票页面
    await page.goto('/tax/output-invoice');
    await page.waitForLoadState('domcontentloaded');

    // 1. 验证页面包含关键元素（用严格选择器避免 strict mode 冲突）
    // 页面标题（用 breadcrumb 中的链接定位）
    await expect(page.getByRole('link', { name: '销项发票' })).toBeVisible({ timeout: 10_000 });
    // 统计卡片区域
    await expect(page.getByText('总发票数')).toBeVisible({ timeout: 10_000 });
    // 筛选按钮组（全部/专用发票/普通发票/红字发票）
    await expect(page.getByRole('radio', { name: '全部' })).toBeChecked();
    await expect(page.getByRole('radio', { name: '专用发票' })).toBeVisible();
    // 表格
    await expect(page.locator('.el-table')).toBeVisible({ timeout: 10_000 });
    // 表头列（用 table thead 限定范围避免 strict mode）
    const table = page.locator('.el-table');
    await expect(table.getByText('发票号')).toBeVisible();
    await expect(table.getByText('开票日期')).toBeVisible();
    await expect(table.getByText('客户')).toBeVisible();
    await expect(table.getByText('金额')).toBeVisible();
    await expect(table.getByText('税额')).toBeVisible();
    // 操作按钮
    await expect(page.getByRole('button', { name: '导入发票' })).toBeVisible();
    await expect(page.getByRole('button', { name: '新增发票' })).toBeVisible();

    // 2. 等待网络静默后断言无 500 错误
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(1_000);
    tracker.assertNoErrors();
  });
});
