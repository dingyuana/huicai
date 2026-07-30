/**
 * E2E 冒烟测试 7：财务核心页面加载
 *
 * 覆盖凭证管理、账簿查询、期末结账等财务核心模块。
 * 这些页面通过 URL 直接导航验证。
 *
 * @标签：@smoke @core @finance
 */
import { test, expect } from '@playwright/test';
import { login, createErrorTracker } from './helpers';

const PAGES = [
  { path: '/finance/voucher', name: '凭证管理', expectedElement: '.el-table, .el-card, main' },
  { path: '/finance/ledger', name: '账簿查询', expectedElement: '.el-card, main, .app-main' },
  { path: '/finance/period-close', name: '期末结账', expectedElement: '.el-card, main, .app-main' },
  { path: '/finance/voucher-setup', name: '凭证设置', expectedElement: '.el-table, .el-card, main' },
  { path: '/finance/carryover-guide', name: '结转向导', expectedElement: '.el-card, main, .app-main' },
  { path: '/finance/beginning-balance', name: '期初建账', expectedElement: '.el-card, main, .app-main' },
  { path: '/finance/bank-reconciliation', name: '银行对账', expectedElement: '.el-table, .el-card, main' },
  { path: '/finance/pending-pool', name: '待处理流水', expectedElement: '.el-table, .el-card, main' },
];

test.describe('财务核心页面加载', () => {
  for (const pageConfig of PAGES) {
    test(`【${pageConfig.name}】页面加载正常，无 500 错误`, async ({ page }) => {
      const tracker = createErrorTracker(page);
      await login(page);

      await page.goto(pageConfig.path);
      await page.waitForLoadState('networkidle');
      await page.waitForTimeout(2_000);

      // 验证页面有可见的主内容区域
      const selectors = pageConfig.expectedElement.split(', ');
      let found = false;
      for (const sel of selectors) {
        const el = page.locator(sel).first();
        if (await el.isVisible({ timeout: 3_000 }).catch(() => false)) {
          found = true;
          break;
        }
      }
      expect(found, `页面【${pageConfig.name}】应包含可见的主内容区域`).toBeTruthy();

      await page.waitForLoadState('networkidle');
      await page.waitForTimeout(500);
      tracker.assertNoErrors();
    });
  }
});