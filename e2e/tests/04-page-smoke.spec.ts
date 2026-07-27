/**
 * 冒烟测试 4：全量页面加载
 * 参数化遍历所有业务页面，验证：
 * 1. 页面能正常加载（无 500 错误）
 * 2. 主内容区域可见
 * 3. 无 console.error
 * 
 * @标签：@smoke @regression
 */
import { test, expect } from '@playwright/test';
import { login, createErrorTracker } from './helpers';

/** 所有业务页面：路径 + 期望页面标题关键词 */
const PAGES = [
  // ─── 首页 ───
  { path: '/dashboard', title: '首页' },

  // ─── 财务核心 ───
  { path: '/finance/voucher', title: '凭证管理' },
  { path: '/finance/ledger', title: '账簿查询' },
  { path: '/finance/period-close', title: '期末结账' },
  { path: '/finance/beginning-balance', title: '期初建账' },
  { path: '/finance/carryover-guide', title: '结转向导' },
  { path: '/finance/voucher-setup', title: '凭证设置' },

  // ─── 业务单据 ───
  { path: '/finance/business-doc', title: '业务单据' },
  { path: '/finance/bank-account', title: '银行账户' },
  { path: '/finance/bank-journal', title: '银行日记账' },
  { path: '/finance/bank-statement', title: '银行对账单' },
  { path: '/finance/pending-pool', title: '待处理流水' },
  { path: '/finance/bank-reconciliation', title: '银行对账' },
  { path: '/finance/cash-journal', title: '现金日记账' },
  { path: '/finance/ticket', title: '票据管理' },

  // ─── 应收应付 ───
  { path: '/arap/reconciliation-workbench', title: '核销工作台' },
  { path: '/arap/expense', title: '费用报销单' },

  // ─── 税务发票 ───
  { path: '/tax/input-invoice', title: '进项发票' },
  { path: '/tax/vat', title: '增值税计算' },

  // ─── 固定资产 ───
  { path: '/asset/category', title: '资产类别' },
  { path: '/asset/card', title: '资产卡片' },
  { path: '/asset/depreciation', title: '折旧计提' },
  { path: '/asset/disposal', title: '资产处置' },
  { path: '/asset/inventory', title: '资产盘点' },

  // ─── 报表中心 ───
  { path: '/report/subject-balance', title: '科目余额表' },
  { path: '/report/balance-sheet', title: '资产负债表' },
  { path: '/report/income-statement', title: '利润表' },
  { path: '/report/cash-flow', title: '现金流量表' },

  // ─── 系统管理 ───
  { path: '/system/user', title: '用户管理' },
  { path: '/system/role', title: '角色管理' },
  { path: '/system/menu', title: '菜单管理' },
  { path: '/system/dept', title: '部门管理' },
  { path: '/system/audit-log', title: '操作日志' },
  { path: '/system/classification-rule', title: '分类规则' },
  { path: '/basis/config', title: '系统参数' },
  { path: '/system/clear-data', title: '数据维护' },

  // ─── 基础数据 ───
  { path: '/basis/account-and-summary', title: '科目摘要' },
  { path: '/basis/period', title: '会计期间' },
  { path: '/basis/party', title: '客商档案' },
];

test.describe('@smoke @regression 全量页面加载', () => {
  for (const pageConfig of PAGES) {
    test(`【${pageConfig.title}】${pageConfig.path} 加载正常`, async ({ page }) => {
      const tracker = createErrorTracker(page);
      await login(page);

      // 导航到目标页面 — 使用 load 状态而非 networkidle（避免轮询页面卡住）
      await page.goto(pageConfig.path);
      await page.waitForLoadState('load');

      // 给异步 API 请求完成时间（缓冲期，避免漏掉短请求）
      await page.waitForTimeout(2_000);

      // 验证主内容区域可见
      const mainContent = page.locator('.el-main, .app-main, main, [class*=\"main\"]').first();
      await expect(mainContent).toBeVisible({ timeout: 15_000 });

      // 可选尝试 networkidle（但超时即放弃，不阻塞测试）
      try {
        await page.waitForLoadState('networkidle', { timeout: 3_000 });
      } catch {
        // 页面有持续轮询请求时 networkidle 可能超时，忽略不影响结果
      }

      tracker.assertNoErrors();
    });
  }
});