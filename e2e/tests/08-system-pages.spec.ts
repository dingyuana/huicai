/**
 * E2E 冒烟测试 8：系统管理 + 基础数据页面
 *
 * 覆盖系统管理（用户/角色/菜单/部门/日志/分类规则/数据维护）
 * 和基础数据（科目/期间/摘要/参数）页面。
 *
 * @标签：@smoke @system @basis
 */
import { test, expect } from '@playwright/test';
import { login, createErrorTracker } from './helpers';

const PAGES = [
  // ─── 系统管理 ───
  { path: '/system/user', name: '用户管理', expectedElement: '.el-table, .el-card, main' },
  { path: '/system/role', name: '角色管理', expectedElement: '.el-table, .el-card, main' },
  { path: '/system/menu', name: '菜单管理', expectedElement: '.el-table, .el-card, main' },
  { path: '/system/dept', name: '部门管理', expectedElement: '.el-table, .el-card, main' },
  { path: '/system/audit-log', name: '操作日志', expectedElement: '.el-table, .el-card, main' },
  { path: '/system/classification-rule', name: '分类规则', expectedElement: '.el-table, .el-card, main' },
  { path: '/system/clear-data', name: '数据维护', expectedElement: '.el-card, main, .app-main' },

  // ─── 基础数据 (未在其他 spec 中单独覆盖的路径) ───
  { path: '/basis/account-and-summary', name: '科目摘要', expectedElement: '.el-table, .el-card, main' },
  { path: '/basis/period', name: '会计期间', expectedElement: '.el-table, .el-card, main' },
  { path: '/basis/party', name: '客商档案', expectedElement: '.el-table, .el-card, main' },
  { path: '/basis/config', name: '系统参数', expectedElement: '.el-table, .el-card, main' },
  { path: '/finance/voucher-template-ref', name: '模板参考库', expectedElement: '.el-table, .el-card, main' },
];

test.describe('系统管理 + 基础数据页面加载', () => {
  for (const pageConfig of PAGES) {
    test(`【${pageConfig.name}】页面加载正常，无 500 错误`, async ({ page }) => {
      const tracker = createErrorTracker(page);
      await login(page);

      await page.goto(pageConfig.path);
      await page.waitForLoadState('load');
      await page.waitForTimeout(2_000);

      // 验证主内容区域可见
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

      try {
        await page.waitForLoadState('networkidle', { timeout: 3_000 });
      } catch { /* 持续轮询页面不阻塞 */ }
      await page.waitForTimeout(500);
      tracker.assertNoErrors();
    });
  }
});