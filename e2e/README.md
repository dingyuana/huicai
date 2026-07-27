# 慧财财务 E2E 冒烟测试

基于 Playwright 的端到端自动化测试，验证已部署系统的核心功能。

## 快速开始

```bash
cd e2e
npm install -D @playwright/test   # 首次安装
npx playwright test               # 运行全部测试
```

## 测试用例清单

| 文件 | 测试数 | 描述 |
|------|--------|------|
| `01-login.spec.ts` | 2 | 登录成功/失败验证 |
| `02-menu-navigation.spec.ts` | 6 | 6 个一级菜单展开+子菜单点击 |
| `03-output-invoice.spec.ts` | 1 | 销项发票页面内容验证 |
| `04-page-smoke.spec.ts` | 39 | 全量业务页面加载冒烟测试 |
| **合计** | **48** | |

## 覆盖范围

| 模块 | 页面数 | 覆盖 |
|------|--------|------|
| 首页 | 1 | ✅ |
| 财务核心 | 6 | ✅ 凭证/账簿/结账/建账/结转/凭证设置 |
| 业务单据 | 10 | ✅ 业务单据/银行账户/日记账/对账单/待处理/对账/现金/票据/核销/费用 |
| 税务发票 | 3 | ✅ 进项/销项/增值税 |
| 固定资产 | 5 | ✅ 类别/卡片/折旧/处置/盘点 |
| 报表中心 | 4 | ✅ 科目余额/资产负债表/利润表/现金流量表 |
| 系统管理 | 7 | ✅ 用户/角色/菜单/部门/日志/配置/数据维护 |
| 基础数据 | 3 | ✅ 科目摘要/会计期间/客商档案 |
| 登录 | 2 | ✅ 成功/失败 |
| **总计** | **41** | **全部覆盖** |

## 已知问题（测试已捕获，后端待修复）

| 页面 | 错误 | 原因 |
|------|------|------|
| 科目余额表 | API 500: `/reports/subject-balance?period=202607` | 后端报表服务异常 |
| 资产负债表 | API 500: `/reports/balance-sheet?period=202607` | 后端报表服务异常 |
| 利润表 | API 500: `/reports/income-statement?period=202607` | 后端报表服务异常 |
| 现金流量表 | API 500: `/reports/cash-flow?period=202607` | 后端报表服务异常 |
| 客商档案 | Console 401 Unauthorized | 权限配置问题 |

## 运行命令

```bash
npx playwright test                            # 全部（无头）
npx playwright test --reporter=list            # 列表模式
npx playwright test --list                     # 列出所有测试
npx playwright test 01-login                   # 单个文件
npx playwright test --headed                   # 有头模式（看浏览器操作）
npx playwright test --debug                    # 逐行调试
npx playwright show-report                     # 查看 HTML 报告
npx playwright show-trace test-results/.../trace.zip  # 回放失败会话
```

## 环境变量

- `E2E_BASE_URL` — 目标地址（默认 `http://129.211.7.254:3001`）

## 测试模式

### 页面加载冒烟测试（04-page-smoke.spec.ts）

所有 39 个业务页面统一通过参数化循环测试：
1. 登录 → 导航到页面
2. 验证主内容区域可见
3. 拦截所有 `/api/` 的 500 响应和 console.error
4. 断言无错误

### 错误检测（createErrorTracker）

`helpers.ts` 中的 `createErrorTracker()` 在**网络层**拦截错误，不依赖页面文本显示：
- 注册 `page.on('response')` 监听器，捕获 `/api/` 的 500 响应
- 注册 `page.on('console')` 监听器，捕获 console.error
- 必须在 `page.goto()` 前注册，确保不遗漏请求