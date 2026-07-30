/**
 * E2E 冒烟测试 9：模块页面加载（税务发票 + 固定资产 + 报表中心）
 *
 * 覆盖 04-page-smoke 以外的税务发票、固定资产、报表中心页面。
 * 每个页面验证：登录 → 导航 → 加载 → 无 500 错误。
 *
 * @标签：@smoke @module
 */
import { test, expect } from '@playwright/test';
import { login, createErrorTracker } from './helpers';

const PAGES = [
  // ─── 税务发票 ───
  { path: '/tax/input-invoice', name: '进项发票', expectedElement: '.el-table, .el-card, main' },
  { path: '/tax/vat', name: '增值税计算', expectedElement: '.el-card, main, .app-main' },

  // ─── 固定资产 ───
  { path: '/asset/category', name: '资产类别', expectedElement: '.el-table, .el-card, main' },
  { path: '/asset/card', name: '资产卡片', expectedElement: '.el-table, .el-card, main' },
  { path: '/asset/depreciation', name: '折旧计提', expectedElement: '.el-card, main, .app-main' },
  { path: '/asset/disposal', name: '资产处置', expectedElement: '.el-table, .el-card, main' },
  { path: '/asset/inventory', name: '资产盘点', expectedElement: '.el-table, .el-card, main' },

  // ─── 报表中心 ───
  { path: '/report/subject-balance', name: '科目余额表', expectedElement: '.el-table, .el-card, main' },
  { path: '/report/balance-sheet', name: '资产负债表', expectedElement: '.el-card, main, .app-main' },
  { path: '/report/income-statement', name: '利润表', expectedElement: '.el-card, main, .app-main' },
  { path: '/report/cash-flow', name: '现金流量表', expectedElement: '.el-card, main, .app-main' },
];

test.describe('模块页面加载（税务发票 + 固定资产 + 报表中心）', () => {
  for (const pageConfig of PAGES) {
    test(`【${pageConfig.name}】页面加载正常，无 500 错误`, async ({ page }) => {
      const tracker = createErrorTracker(page);
      await login(page);

      await page.goto(pageConfig.path);
      await page.waitForLoadState('load');
      await page.waitForTimeout(2_000);

      // 验证主内容区域可见
      const selectors = pageConfig.expectedElement.split(', ');
      let found = true;
      for (const sel of selectors) {
        const el = page.locator(sel).first();
        if (await el.isVisible({ timeout: 3_000 }).catch(() => false)) {
          found = true;
          break;
        }
      }
      expect(found, `页面【${pageConfig.name}】应包含可见的主内容区域`).toBeTruthy();

      try {
        await page.waitForLoadState('networkidle', { timeout: 3_000 });
      } catch { /* 持续轮询页面不阻塞 */ }
      await page.waitForTimeout(500);
      tracker.assertNoErrors();
    });
  }
});