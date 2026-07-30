/**
 * E2E 冒烟测试 6：核心业务页面加载
 *
 * 验证各核心业务页面能正常加载，无 500 错误。
 * 使用 createErrorTracker 在网络层拦截 /api/ 的 500 响应。
 * 直接通过 URL 导航（不依赖菜单点击），减少测试间耦合。
 *
 * @标签：@smoke @core
 */
import { test, expect } from '@playwright/test';
import { login, createErrorTracker } from './helpers';

// 核心页面配置：path = 路由路径，name = 显示名称，expectedElement = 页面加载后预期可见的元素
const PAGES = [
  { path: '/finance/bank-statement', name: '银行对账单', expectedElement: '.el-table, .el-card, main' },
  { path: '/finance/business-doc', name: '业务单据', expectedElement: '.el-table, .el-card, main' },
  { path: '/finance/bank-account', name: '银行账户', expectedElement: '.el-table, .el-card, main' },
  { path: '/finance/bank-journal', name: '银行日记账', expectedElement: '.el-table, .el-card, main' },
  { path: '/finance/cash-journal', name: '现金日记账', expectedElement: '.el-table, .el-card, main' },
  { path: '/finance/ticket', name: '票据管理', expectedElement: '.el-table, .el-card, main' },
  { path: '/arap/settlement', name: '往来核销', expectedElement: '.el-table, .el-card, main' },
  { path: '/arap/reconciliation-workbench', name: '核销工作台', expectedElement: '.el-table, .el-card, main' },
  { path: '/arap/expense', name: '费用报销单', expectedElement: '.el-table, .el-card, main' },
];

test.describe('核心业务页面加载', () => {
  for (const pageConfig of PAGES) {
    test(`【${pageConfig.name}】页面加载正常，无 500 错误`, async ({ page }) => {
      const tracker = createErrorTracker(page);
      await login(page);

      // 直接导航到目标页面
      await page.goto(pageConfig.path);
      await page.waitForLoadState('networkidle');

      // 等待 Vue 渲染 + 异步请求完成
      await page.waitForTimeout(2_000);

      // 验证页面主内容可见（table / card 或 main 区域）
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

      // 断言无 500 错误
      await page.waitForLoadState('networkidle');
      await page.waitForTimeout(500);
      tracker.assertNoErrors();
    });
  }
});