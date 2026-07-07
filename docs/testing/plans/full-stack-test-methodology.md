# 慧财财务 — 全链路测试方法论

> **编号**：HUICAI-TST-002
> **版本**：V1.0 | **修改日期**：2026-07-07 | **修改人**：Hermes | **修改内容**：添加编号头部

本文档定义慧财财务（huicai）项目的**全链路测试方法论**，覆盖前端→后端→数据库的完整调用链，确保每个设计文档定义的接口、路由、组件都存在且正确连接。

测试分为四个层级：

```
┌─────────────────────────────────────────────────────────────┐
│  L1: 接口覆盖检测（API Coverage）                            │
│  检测前端 API 调用 ↔ 后端端点的匹配关系                      │
│  工具: Python 脚本 (scripts/check_api_coverage.py)          │
├─────────────────────────────────────────────────────────────┤
│  L2: 路由覆盖检测（Route Coverage）                          │
│  检测前端路由 ↔ 组件文件的匹配关系                            │
│  工具: Python 脚本 (scripts/check_route_coverage.py)        │
├─────────────────────────────────────────────────────────────┤
│  L3: 状态机副作用检测（StateMachine Side-Effect）            │
│  检测状态转换方法是否产生不该有的副作用                       │
│  工具: StateMachineTestHelper (Java + TypeScript)            │
├─────────────────────────────────────────────────────────────┤
│  L4: 功能测试（Functional Test）                             │
│  正向断言 + 负向断言                                          │
│  工具: JUnit 5 + Vitest + Playwright                        │
└─────────────────────────────────────────────────────────────┘
```

---

## L1: 接口覆盖检测

### 目标

确保前端发起的每一个 API 调用，后端都有对应的端点处理；后端定义的每一个端点，前端都有调用方。

### 检测方法

```
前端 API 调用 (src/api/**/*.ts)
  │
  ├─ 提取所有 request.get/post/put/delete 的 URL
  │   例: request.post('/tax/output-invoices/1/confirm')
  │
  └─ 与后端端点匹配
       后端端点 (Controller @RequestMapping + @PostMapping/GetMapping/...)
         例: @PostMapping("/output-invoices/{id}/confirm")
             @RequestMapping("/api/v1/tax")
             完整路径: /api/v1/tax/output-invoices/{id}/confirm
```

### 匹配规则

| 前端格式 | 后端格式 | 匹配方式 |
|----------|----------|----------|
| `${id}` | `{id}` | 正则替换后精确匹配 |
| `${statementId}` | `{statementId}` | 同上 |
| `/api/v1/...` | `/api/v1/...` | 去除前缀后匹配 |

### 检测脚本

```bash
# 运行接口覆盖检测
python scripts/check_api_coverage.py
```

### 输出示例

```
=== 接口覆盖检测报告 ===
✅ 匹配成功: 185/185 (100%)
❌ 后端有前端无: 0
⚠️ 前端有后端无: 0

--- 前端有后端无 (可能已废弃) ---
⚠️ /api/v1/tax/output-invoices/${id}/mark-vouchered  ← 后端无此端点
```

### 正向测试

```typescript
// 每个前端 API 函数都有对应的后端端点
test('confirmOutputInvoice 调用正确的后端端点', async () => {
  mockRequest.post.mockResolvedValue(undefined)
  await taxApi.confirmOutputInvoice(1)
  expect(mockRequest.post).toHaveBeenCalledWith('/tax/output-invoices/1/confirm')
})
```

### 负向测试

```typescript
// 前端不应调用不存在的后端端点
test('前端不应调用未实现的后端端点', () => {
  // 通过接口覆盖检测脚本自动发现
  // 如果前端调用了后端不存在的端点，CI 失败
})
```

---

## L2: 路由覆盖检测

### 目标

确保前端路由表中的每一个路由，都有对应的组件文件；组件文件中的每一个路由引用，都有对应的路由定义。

### 检测方法

```
路由表 (src/router/routes/base.ts)
  │
  ├─ 提取所有 { path, name, component }
  │   例: { path: 'finance/business-doc/edit', name: 'BusinessDocEdit',
  │        component: () => import('@/views/finance/business-doc/BusinessDocEdit.vue') }
  │
  └─ 检查组件文件是否存在
       路径: src/views/finance/business-doc/BusinessDocEdit.vue
```

### 检测脚本

```bash
# 运行路由覆盖检测
python scripts/check_route_coverage.py
```

### 输出示例

```
=== 路由覆盖检测报告 ===
✅ 路由定义数: 50
✅ 组件文件存在: 50/50 (100%)

--- 路由定义缺失 ---
❌ 组件 BusinessDocEdit.vue 存在，但路由表中无对应路由
❌ 路由 BusinessDocEdit 定义存在，但组件文件不存在
```

### 正向测试

```typescript
// 路由存在且可访问
test('BusinessDocEdit 路由可访问', async ({ page }) => {
  await page.goto('/finance/business-doc/edit?mode=create')
  await expect(page.locator('.page-title')).toHaveText('新增单据')
})
```

### 负向测试

```typescript
// 路由不存在时应返回 404
test('不存在的页面应返回 404', async ({ page }) => {
  await page.goto('/non-existent-page')
  await expect(page.locator('.error-page')).toBeVisible()
})
```

---

## L3: 状态机副作用检测

### 目标

确保状态转换方法只改变状态，不产生不该有的副作用（如提前生成凭证、创建业务单等）。

### 检测方法

```
状态转换方法
  │
  ├─ 正向断言：状态正确变更
  │   assertEquals(目标状态, entity.getStatus())
  │
  └─ 负向断言：无意外副作用
       verify(mapper, never()).insert(any())
```

### 工具

- **后端**: `StateMachineTestHelper` (Java)
- **前端**: `StateMachineTestHelper` (TypeScript)

### 检测脚本

```bash
# 运行状态机副作用检测
mvn test -Dtest=OutputInvoiceStateMachineServiceImplTest
npm test -- --testNamePattern="状态机"
```

### 输出示例

```
=== 状态机副作用检测报告 ===
✅ OutputInvoiceStateMachineServiceImpl.confirm() — 无意外副作用
❌ OutputInvoiceStateMachineServiceImpl.confirm() — 检测到意外创建凭证
   原因: postProcessAfterInvoiceConfirm() 调用了 businessDocService.generateVoucher()
```

---

## L4: 功能测试

### 目标

确保每个功能模块的接口、组件、页面都正常工作，正向功能正确，负向功能被阻止。

### 测试金字塔

```
                    ┌─────────────┐
                    │   E2E 测试   │  Playwright (3 个测试)
                    │  浏览器完整流程 │
                    └─────────────┘
               ┌─────────────────────┐
               │  组件单元测试         │  Vue Test Utils
               │  组件渲染 + 交互      │
               └─────────────────────┘
        ┌─────────────────────────────┐
        │  API 单元测试                │  Vitest
        │  API 函数 + 请求/响应        │
        └─────────────────────────────┘
```

### 正向测试

```typescript
// API 层
test('confirmOutputInvoice 调用正确的后端端点', async () => {
  mockRequest.post.mockResolvedValue(undefined)
  await taxApi.confirmOutputInvoice(1)
  expect(mockRequest.post).toHaveBeenCalledWith('/tax/output-invoices/1/confirm')
})

// 组件层
test('PENDING_REVIEW 状态显示"审核通过"按钮', async () => {
  const wrapper = mount(OutputInvoiceList, { props: { /* status=PENDING_REVIEW */ } })
  expect(wrapper.find('[data-action="confirm"]').exists()).toBe(true)
})

// E2E 层
test('审核通过流程', async ({ page }) => {
  await page.goto('/tax/output-invoice')
  await page.click('text=审核通过')
  await page.waitForResponse('**/confirm')
  await expect(page.locator('.status-badge')).toHaveText('已确认')
})
```

### 负向测试

```typescript
// API 层
test('confirmOutputInvoice 不应误调 markVouchered', async () => {
  mockRequest.post.mockResolvedValue(undefined)
  await taxApi.confirmOutputInvoice(1)
  StateMachineTestHelper.assertApiNotCalled(mockRequest.post, 'mark-vouchered')
})

// 组件层
test('PENDING_REVIEW 状态不应显示"生成凭证"按钮', async () => {
  const wrapper = mount(OutputInvoiceList, { props: { /* status=PENDING_REVIEW */ } })
  StateMachineTestHelper.assertElementNotExists(wrapper, '[data-action="markVouchered"]')
})

// E2E 层
test('审核通过后凭证数不应增加', async ({ page }) => {
  const countBefore = await page.locator('.voucher-row').count()
  await page.click('text=审核通过')
  await page.waitForResponse('**/confirm')
  await expect(page.locator('.voucher-row')).toHaveCount(countBefore)
})
```

---

## CI 集成

### GitHub Actions 配置

```yaml
name: Full Stack Test

on: [push, pull_request]

jobs:
  api-coverage:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - run: python scripts/check_api_coverage.py

  route-coverage:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - run: python scripts/check_route_coverage.py

  backend-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - run: cd backend && ./mvnw test

  frontend-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - run: cd frontend && npm ci && npm test

  e2e-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - run: cd frontend && npx playwright install --with-deps chromium
      - run: cd frontend && npx playwright test
```

---

## 相关文件

| 文件 | 说明 |
|------|------|
| `scripts/check_api_coverage.py` | 接口覆盖检测脚本 |
| `scripts/check_route_coverage.py` | 路由覆盖检测脚本 |
| `docs/process/state-machine-test-checklist.md` | 状态机测试契约检查清单 |
| `backend/src/test/java/com/huicai/common/test/StateMachineTestHelper.java` | 后端状态机负向断言辅助类 |
| `frontend/src/__tests__/helper/StateMachineTestHelper.ts` | 前端状态机负向断言辅助类 |
