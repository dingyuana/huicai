# 慧财财务 E2E 冒烟测试

基于 Playwright 的端到端自动化测试，验证已部署系统的核心功能。

## 快速开始

```bash
cd e2e
npm install
npx playwright test
```

## 测试用例

| 文件 | 描述 | 测试数 |
|------|------|--------|
| `01-login.spec.ts` | 登录成功/失败 | 2 |
| `02-menu-navigation.spec.ts` | 菜单导航（6 个一级菜单） | 6 |
| `03-output-invoice.spec.ts` | 销项发票页面加载 | 1 |

## 运行选项

```bash
# 列出所有测试
npx playwright test --list

# 运行单个测试文件
npx playwright test 01-login.spec.ts

# UI 模式（需要图形界面）
npx playwright test --ui

# 有头模式（看到浏览器操作）
npx playwright test --headed

# 生成 HTML 报告
npx playwright show-report
```

## 配置

- **目标地址**: `http://129.211.7.254:3001`（可通过 `E2E_BASE_URL` 环境变量覆盖）
- **登录凭证**: admin / admin123
- **浏览器**: 使用系统 Chrome（`channel: 'chrome'`），避免下载 Playwright Chromium
