/**
 * 冒烟测试 2：菜单导航
 * 验证所有一级菜单都能展开，子菜单可点击，页面无 500 错误
 */
import { test, expect } from '@playwright/test';
import { login, expectNoServerErrors } from './helpers';

// 菜单项配置：name = 侧边栏显示的一级菜单名称，firstSubMenu = 第一个子菜单的文本
const MENUS = [
  { name: '基础数据', firstSubMenu: '科目摘要' },
  { name: '财务核心', firstSubMenu: '凭证管理' },
  { name: '业务单据', firstSubMenu: '银行日记账' },
  { name: '税务发票', firstSubMenu: '销项发票' },
  { name: '固定资产', firstSubMenu: '资产卡片' },
  { name: '报表中心', firstSubMenu: '科目余额表' },
];

test.describe('菜单导航', () => {
  for (const menu of MENUS) {
    test(`展开【${menu.name}】并点击第一个子菜单`, async ({ page }) => {
      await login(page);

      // 点击一级菜单展开（Element Plus 侧边栏的 menuitem）
      const menuItem = page.getByRole('menuitem', { name: menu.name });
      await menuItem.click();
      // 等待子菜单项出现并可点击
      const firstSub = page.getByRole('menuitem', { name: menu.firstSubMenu });
      await expect(firstSub).toBeVisible({ timeout: 8_000 });
      await firstSub.click();
      // 等待路由跳转完成 + Vue 渲染
      await page.waitForTimeout(1_500);

      // 验证页面加载成功（主内容区域可见）
      const mainContent = page.locator('.el-main, .app-main, main, [class*="main"]').first();
      await expect(mainContent).toBeVisible({ timeout: 10_000 });
      // 验证无 500 错误提示
      await expectNoServerErrors(page);
    });
  }
});
