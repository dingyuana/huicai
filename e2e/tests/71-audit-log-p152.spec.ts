/**
 * SPEC-P152 错误留痕 + 操作日志 —— E2E 实机走查。
 *
 * <p>目标环境：本地起的服务（E2E_BASE_URL=http://127.0.0.1:3001）。
 * 覆盖：① 操作日志页按真实字段渲染（此前 action/resourceType 等虚构键导致
 * 整列恒空）；② 登录审计可查（AUTH/LOGIN 由本轮 API 触发产生）；
 * ③ 前端上报端点真实落库并可被页面过滤出来（FRONTEND/JS_ERROR）。</p>
 */
import { test, expect } from '@playwright/test';
import { login, createErrorTracker } from './helpers';

test.use({ navigationTimeout: 90_000, actionTimeout: 20_000 });

test.describe('P152 操作日志页（字段对齐 + 错误留痕可查）', () => {
  test.beforeEach(async ({ request }) => {
    // 造一条前端错误上报（直连后端 8000；dev 同源则走 vite 代理亦可）
    await request.post('http://127.0.0.1:8000/api/v1/system/audit-log/client', {
      data: {
        level: 'ERROR',
        message: `e2e-p152-boom-${Date.now()}`,
        stack: 'Error: e2e injected\n    at Spec.ts:1:1',
        url: 'http://app/#/e2e',
      },
    });
  });

  test('日期范围过滤出数：真实列渲染（模块/操作/IP/结果/耗时）', async ({ page }) => {
    const tracker = createErrorTracker(page);
    await login(page);
    await page.goto('/system/audit-log');
    await page.waitForLoadState('networkidle');

    // 未选日期 = 空态引导（R1：归档视图必须带日期条件）
    await expect(page.getByText('请先选择日期范围')).toBeVisible();

    const today = new Date();
    const ymd = (d: Date) =>
      `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;

    await page.getByPlaceholder('开始日期').fill(ymd(new Date(today.getTime() - 86400_000)));
    await page.keyboard.press('Enter');
    await page.getByPlaceholder('结束日期').fill(ymd(today));
    await page.keyboard.press('Enter');
    await page.getByRole('button', { name: '查询' }).click();
    await page.waitForResponse((r) => r.url().includes('/audit-log/page') && r.ok(), { timeout: 20_000 });

    const table = page.getByTestId('audit-log-table');
    await expect(table.locator('.el-table__row').first()).toBeVisible({ timeout: 10_000 });

    // 登录审计（beforeEach 的 login() 即产生 AUTH/LOGIN）——模块列 + 结果列出真值
    await expect(table.locator('.el-tag', { hasText: 'AUTH' }).first()).toBeVisible();
    await expect(table.getByText('LOGIN').first()).toBeVisible();
    await expect(table.locator('.el-table__row').first().getByText(/成功|失败|警告/).first()).toBeVisible();

    tracker.assertNoErrors();
  });

  test('前端错误上报（FRONTEND/JS_ERROR）在页面可过滤、详情显示堆栈', async ({ page }) => {
    await login(page);
    await page.goto('/system/audit-log');
    await page.waitForLoadState('networkidle');

    const today = new Date();
    const ymd = (d: Date) =>
      `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
    await page.getByPlaceholder('开始日期').fill(ymd(new Date(today.getTime() - 86400_000)));
    await page.keyboard.press('Enter');
    await page.getByPlaceholder('结束日期').fill(ymd(today));
    await page.keyboard.press('Enter');

    // 模块下拉选「前端错误」（el-form-item 的 label 不保证 a11y 关联，用表单项容器定位）
    await page.locator('.el-form-item', { hasText: '操作模块' }).locator('.el-select').click();
    await page.getByRole('option', { name: '前端错误' }).click();
    await page.getByRole('button', { name: '查询' }).click();
    await page.waitForResponse((r) => r.url().includes('/audit-log/page') && r.ok(), { timeout: 20_000 });

    const table = page.getByTestId('audit-log-table');
    const row = table.locator('.el-table__row', { hasText: 'JS_ERROR' }).first();
    await expect(row).toBeVisible({ timeout: 10_000 });
    // 状态列：fail ⇒ 「失败」红标
    await expect(row.getByText('失败')).toBeVisible();

    // 详情弹窗：after_data 标签随模块变化为「前端堆栈」，内容含注入堆栈
    await row.getByRole('button', { name: '详情' }).click();
    const dialog = page.getByTestId('audit-detail-dialog');
    await expect(dialog).toBeVisible();
    await expect(dialog.getByText('前端堆栈')).toBeVisible();
    await expect(dialog.locator('.stack-block')).toContainText('e2e injected');
  });
});
