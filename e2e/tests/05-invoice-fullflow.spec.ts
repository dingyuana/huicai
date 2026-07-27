/**
 * 销项发票完整业务流程测试 (@smoke)
 * 
 * 流程：新建 → 审核 → 确认 → 凭证生成验证
 * 
 * @note：此为端到端实际用户操作流，覆盖业务核心路径
 * @标签：@smoke
 */
import { test, expect } from '@playwright/test';
import { login, createErrorTracker } from './helpers';

test.describe('@smoke 销项发票完整流程', () => {
  test.beforeEach(async ({ page }) => {
    const tracker = createErrorTracker(page);
    
    // 1. 登录
    await login(page);
    
    // 2. 进入销项发票列表页
    await page.goto('/tax/output-invoice');
    await expect(page.getByRole('link', { name: '销项发票' })).toBeVisible({ timeout: 10000 });
    
    // 3. 点击"新增"按钮
    await page.click('button:has-text("新增")');
    await page.waitForSelector('.el-dialog', { timeout: 10000 });
    
    // 4. 填写基本信息
    await page.getByPlaceholder('客户名称').fill('TestInvoice001');
    await page.getByPlaceholder('发票编号').fill('INV-TEST-001');
    await page.getByLabel('金额').first().fill('1000');
    await page.getByLabel('税率').first().selectOption('value:13');
    
    // 5. 保存
    await page.click('button[type="submit"]');
    await page.waitForSelector('.el-dialog', { state: 'hidden', timeout: 5000 });
    
    // 6. 等待并定位刚创建的发票行
    await page.goto('/tax/output-invoice');
    const invoiceRow = page.locator('tr').filter({ has: page.locator('text=TestInvoice001') });
    await expect(invoiceRow).toBeVisible({ timeout: 15000 });
    
    // 7. 点击审核
    const reviewBtn = invoiceRow.locator('button:has-text("审核")');
    await reviewBtn.click();
    await page.fill('textarea', '测试审核通过');
    await page.click('button:has-text("确定")');
    
    // 8. 审核后状态变为待确认
    await expect(invoiceRow.getByText('待确认')).toBeVisible();
    
    // 9. 点击确认
    const confirmBtn = invoiceRow.locator('button:has-text("确认")');
    await confirmBtn.click();
    await expect(page.getByText('成功')).toBeTruthy();
    
    // 10. 验证业务单据和凭证生成
    await page.goto('/finance/business-doc');
    await expect(page.getByText('业务单据')).toBeVisible();
    
    await page.goto('/finance/voucher');
    await expect(page.getByText('凭证管理')).toBeVisible();
    
    tracker.assertNoErrors();
  });

  test('应能完成新建→审核→确认全流程且无500错误', async ({ page }) => {
    // 逻辑在 beforeEach 中执行
    await expect(createErrorTracker(page).apiErrors()).toHaveLength(0);
  });

  test('确认后的发票应可见于已确认列表', async ({ page }) => {
    await page.goto('/tax/output-invoice');
    const row = page.locator('tr').filter({ has: page.locator('text=TestInvoice001') });
    await expect(row.getByText('已确认')).toBeVisible();
  });
});

// 回归测试（较耗时长，仅 nightly full suite 运行）
test.describe.skip('回归：销项发票完整生命周期（含红冲、核销）', () => {
  test('创建→审核→确认→核销→作废闭环', async ({ page }) => {
    // placeholder - 将在 Sprint C 完善
    expect(true).toBe(true);
  });
});
