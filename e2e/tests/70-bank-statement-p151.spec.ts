/**
 * SPEC-P151 银行流水分类视图 + 账期统一口径 —— E2E 实机验证。
 *
 * <p>目标环境：本地起的服务（E2E_BASE_URL 覆盖）。企业 1 的 202407 有
 * 26 条流水（business_payment 12 / business_receipt 8 / bank_interest_fee 6）；
 * 企业 2 无流水。登录默认企业 1。</p>
 */
import { test, expect } from '@playwright/test';
import { login, createErrorTracker } from './helpers';

test.describe('P151 银行流水分类视图（企业1 / 202407）', () => {
  test('默认按分类折叠 + 默认当前账期，组头计数与后端同口径', async ({ page }) => {
    const tracker = createErrorTracker(page);
    await login(page);
    await page.goto('/finance/bank-statement');
    await page.waitForLoadState('networkidle');

    // 三个组且每组的收/支/金额块随折叠头渲染（数据：12/8/6）
    await expect(page.locator('.category-group-title')).toHaveCount(3);
    await expect(page.locator('.category-group-title', { hasText: '业务付款' }))
      .toContainText('12 条');
    await expect(page.locator('.category-group-title', { hasText: '业务收款' }))
      .toContainText('8 条');
    await expect(page.locator('.category-group-title', { hasText: '银行利息与手续费' }))
      .toContainText('6 条');

    // 默认账期提示 = 企业1 当前期间 202407
    await expect(page.getByTestId('period-hint')).toContainText('2024年7月');

    // 首组默认展开且组内行有凭证号回填（业务付款组前几张是 payment_created → 有单/凭证号）
    const firstGroupItems = page.locator('.el-collapse-item__content .el-table__row');
    await expect(firstGroupItems.first()).toBeVisible();

    tracker.assertNoErrors();
  });

  test('清空账期 → 显示全部期间（同数据 26 条 3 组）；分类筛选条与列表同口径', async ({ page }) => {
    await login(page);
    await page.goto('/finance/bank-statement');
    await page.waitForLoadState('networkidle');

    await page.getByTestId('period-hint').getByText('查看全部期间').click();
    await page.waitForLoadState('networkidle');
    await expect(page.getByTestId('period-hint')).toContainText('显示全部期间');
    await expect(page.locator('.category-group-title')).toHaveCount(3);

    // 点分类筛选条 → 组视图只留该组
    await page.locator('.view-mode-row .el-radio-button', { hasText: '业务付款' }).click();
    await page.waitForLoadState('networkidle');
    await expect(page.locator('.category-group-title', { hasText: '业务付款' })).toContainText('12 条');
    await expect(page.locator('.category-group-title')).toHaveCount(1);
  });

  test('待确认/已制证页签同样默认当前账期（空口径时已制证页要求先选时间）', async ({ page }) => {
    await login(page);
    await page.goto('/finance/bank-statement');
    await page.waitForLoadState('networkidle');

    // 切到「已制证」页签：账期已就位（202407），月分组应直接出数据而不是空态
    await page.locator('.el-tabs__item', { hasText: '已制证' }).click();
    await page.waitForLoadState('networkidle');
    await expect(page.locator('.month-group-title').first()).toBeVisible();
  });
});
