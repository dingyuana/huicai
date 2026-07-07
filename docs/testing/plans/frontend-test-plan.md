# 慧财财务 — 前端测试方案

> **编号**：HUICAI-TST-008
> **版本**：V1.0 | **修改日期**：2026-07-07 | **修改人**：Hermes
> **修改内容**：初始创建——前端测试体系从零搭建方案
> **关联文档**：[TESTING_STANDARD.md](standards/TESTING_STANDARD.md)、[全链路测试方法论](plans/full-stack-test-methodology.md)

---

## 一、现状评估

| 维度 | 现状 | 目标 |
|------|------|------|
| 测试文件数 | 2个 | 30+ |
| 测试框架 | 无（仅有2个散落文件） | Vitest + Vue Test Utils |
| 覆盖范围 | 几乎为零 | 核心API模块 + 关键页面组件 |
| CI集成 | 无 | GitHub Actions 自动执行 |
| E2E测试 | 无 | Playwright（远期） |

---

## 二、技术选型

| 工具 | 用途 | 版本 |
|------|------|------|
| Vitest | 单元测试框架 | latest |
| @vue/test-utils | Vue组件测试工具 | latest |
| @testing-library/vue | DOM测试辅助 | latest |
| msw (Mock Service Worker) | API Mock | latest |
| Playwright | E2E浏览器测试（远期） | latest |

---

## 三、测试分层

### L1: API模块测试（优先）

测试 `src/api/modules/*.ts` 中的每个API函数是否正确构造请求。

```
src/api/modules/
├── voucher.ts      → voucher.test.ts
├── tax.ts          → tax.test.ts
├── bankStatement.ts → bankStatement.test.ts
├── budget.ts       → budget.test.ts
├── expense.ts      → expense.test.ts
├── reconciliation.ts → reconciliation.test.ts
└── ... 共12个模块
```

**测试模式**：
```typescript
import { describe, it, expect, vi } from 'vitest'
import { getVoucherPage, submitVoucher } from '../voucher'

vi.mock('@/api/request', () => ({
  default: { get: vi.fn(), post: vi.fn() }
}))

describe('voucher API', () => {
  it('getVoucherPage 应传递分页参数', async () => {
    await getVoucherPage({ current: 1, size: 20 })
    expect(request.get).toHaveBeenCalledWith('/vouchers/page', { params: { current: 1, size: 20 } })
  })
})
```

### L2: 组件测试（核心页面）

测试关键页面的状态机按钮显示逻辑。

| 优先级 | 页面 | 测试要点 |
|--------|------|---------|
| P0 | VoucherList.vue | 状态驱动按钮显示/隐藏 |
| P0 | OutputInvoiceList.vue | 发票状态机按钮 + AI推荐按钮 |
| P0 | BankStatementView.vue | 流水分类 + 确认/审核按钮 |
| P1 | ReconciliationWorkbench.vue | 核销匹配推荐展示 |
| P1 | BudgetList.vue | 预算状态 + 提交按钮 |
| P1 | ExpenseList.vue | 报销状态机按钮 |
| P2 | ReportView.vue | 报表数据加载 |

**测试模式**：
```typescript
import { mount } from '@vue/test-utils'
import VoucherList from '../VoucherList.vue'

describe('VoucherList 状态机按钮', () => {
  it('DRAFT状态显示提交按钮', () => {
    const wrapper = mount(VoucherList, { props: { row: { status: 'DRAFT' } } })
    expect(wrapper.find('[data-test="submit-btn"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="audit-btn"]').exists()).toBe(false)
  })
})
```

### L3: E2E测试（远期）

使用 Playwright 测试核心业务流程：
- 登录 → 发票导入 → 审核 → 生成凭证 → 过账
- 登录 → 银行流水导入 → 分类 → 确认 → 核销

---

## 四、实施计划

| 阶段 | 内容 | 工时 | 优先级 |
|------|------|------|--------|
| 阶段1 | Vitest环境搭建 + 配置 | 0.5d | P0 |
| 阶段2 | 12个API模块测试 | 2d | P0 |
| 阶段3 | 6个核心页面组件测试 | 2d | P0 |
| 阶段4 | CI集成（GitHub Actions） | 0.5d | P1 |
| 阶段5 | Playwright E2E（远期） | 3d | P3 |

---

## 五、CI配置

```yaml
# .github/workflows/frontend-test.yml
name: Frontend Tests
on: [push, pull_request]
jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with: { node-version: '20' }
      - run: cd frontend && npm ci
      - run: cd frontend && npx vitest run --coverage
```

---

## 六、验收标准

| 指标 | 目标 |
|------|------|
| API模块测试覆盖率 | 12/12 模块 |
| 核心页面测试覆盖 | 6/6 页面 |
| 测试用例总数 | 100+ |
| CI执行时间 | < 30s |
| 状态机按钮显示逻辑 | 100%覆盖 |

---

> **文档结束。**