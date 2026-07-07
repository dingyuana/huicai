# 慧财财务 — 测试方法总结与检查清单

> **编号**：HUICAI-TST-007
> **版本**：V1.0 | **修改日期**：2026-07-07 | **修改人**：Hermes | **修改内容**：添加编号头部

```
┌─────────────────────────────────────────────────────────────┐
│                      测试金字塔                              │
├─────────────────────────────────────────────────────────────┤
│  e2e (Playwright)     ← 3 个测试文件，浏览器完整流程          │
│  ────────────────────────────────────────────────────────   │
│  组件 (Vitest)         ← 2 个测试文件，API/Store/组件逻辑     │
│  ────────────────────────────────────────────────────────   │
│  单元 (JUnit 5)        ← 37 个测试文件，389 用例             │
│  ────────────────────────────────────────────────────────   │
│  状态机契约             ← 负向断言辅助类 + 检查清单           │
└─────────────────────────────────────────────────────────────┘
```

---

## 2. 后端测试方法

### 2.1 执行命令

```bash
cd backend
mvn test              # 运行所有测试
mvn test -Dtest=VoucherStateMachineServiceImplTest  # 运行单个测试类
```

### 2.2 测试分类

| 类型 | 注解 | 用途 | 示例 |
|------|------|------|------|
| **单元测试** | `@ExtendWith(MockitoExtension.class)` | 测 Service 逻辑，Mock 所有依赖 | `VoucherStateMachineServiceImplTest` |
| **集成测试** | `@SpringBootTest` + `@MockBean` | 测 Controller + Service，Mock 持久层 | `HuicaiE2EIntegrationTest` |
| **E2E 测试** | `@SpringBootTest` + MockMvc | 测完整 HTTP 请求链路 | `TaxIntegrationTest` |

### 2.3 正向 + 负向断言

每个状态转换方法必须同时验证：

```java
@Test
void confirm_shouldChangeStatusOnly() {
    // 正向：状态正确变更
    assertEquals(InvoiceStatus.CONFIRMED, inv.getStatus());
    
    // 负向：不该做的没做
    StateMachineTestHelper.verifyNoVoucherCreated(voucherMapper, voucherEntryMapper);
}
```

### 2.4 状态机测试检查清单

- [ ] 每个 `transition` 方法有独立测试
- [ ] 每个测试至少 1 条负向断言
- [ ] 负向断言覆盖所有可能被意外污染的 Mapper
- [ ] 异常路径也包含负向断言
- [ ] 使用 `StateMachineTestHelper.verifyNoXxx()` 系列方法

---

## 3. 前端测试方法

### 3.1 执行命令

```bash
cd frontend
npm test              # 运行 Vitest 单元测试
npx playwright test   # 运行 Playwright e2e 测试
```

### 3.2 测试分类

| 类型 | 工具 | 用途 | 示例 |
|------|------|------|------|
| **API 单元** | Vitest + `vi.mock(request)` | 测 API 函数调用 | `system.api.test.ts` |
| **组件单元** | Vue Test Utils + Vitest | 测组件渲染和交互 | `auth.store.test.ts` |
| **e2e** | Playwright | 测浏览器完整流程 | `sales-invoice-import.spec.ts` |

### 3.3 正向 + 负向断言

```typescript
// API 层
test('confirmOutputInvoice 不应误调 markVouchered', async () => {
  mockRequest.post.mockResolvedValue(undefined)
  await taxApi.confirmOutputInvoice(1)
  
  // 正向：confirm 端点被调用
  StateMachineTestHelper.assertApiCalled(mockRequest.post, '/confirm')
  // 负向：mark-vouchered 端点未被调用
  StateMachineTestHelper.assertApiNotCalled(mockRequest.post, 'mark-vouchered')
})

// 组件层
test('PENDING_REVIEW 状态不应显示"生成凭证"按钮', async () => {
  const wrapper = mount(OutputInvoiceList, { props: { /* status=PENDING_REVIEW */ } })
  // 负向：生成凭证按钮不应该存在
  StateMachineTestHelper.assertElementNotExists(wrapper, '[data-action="markVouchered"]')
})

// e2e 层
test('审核通过后凭证数不应增加', async ({ page }) => {
  const countBefore = await page.locator('.voucher-row').count()
  await page.click('text=审核通过')
  await page.waitForResponse('**/confirm')
  // 负向：凭证数不变
  await expect(page.locator('.voucher-row')).toHaveCount(countBefore)
})
```

---

## 4. 接口覆盖检测

### 4.1 检测目的

确保 `mvn test` / `npm test` 能检测到：
1. **后端所有端点已实现** — 没有遗漏的 Controller
2. **前端所有 API 调用有对应后端** — 没有悬空的调用
3. **前后端路径一致** — 没有拼写错误

### 4.2 检测方法

```bash
# 提取后端所有端点
grep -rn '@RequestMapping\|@PostMapping\|@GetMapping\|@PutMapping\|@DeleteMapping' \
  backend/src/main/java/com/huicai --include='*.java'

# 提取前端所有 API 调用
grep -rn "request\.\(get\|post\|put\|delete\)" frontend/src/api --include='*.ts'

# 对比匹配
python3 -c "
import re
# 后端端点
backend = [
  '/api/v1/tax/output-invoices/{id}/confirm',
  '/api/v1/tax/output-invoices/{id}/mark-vouchered',
  # ... 更多
]
# 前端调用
frontend = [
  '/api/v1/tax/output-invoices/${id}/confirm',
  '/api/v1/tax/output-invoices/${id}/mark-vouchered',
  # ... 更多
]
# 匹配
for be in backend:
  matched = any(re.match('^' + re.escape(be).replace(r'\{[^}]+\}', '[^/]+') + '$', fe) for fe in frontend)
  if not matched:
    print(f'❌ 后端有前端无: {be}')
for fe in frontend:
  matched = any(re.match('^' + re.escape(fe).replace(r'\$\{[^}]+\}', '[^/]+') + '$', be) for be in backend)
  if not matched:
    print(f'⚠️ 前端有后端无: {fe}')
"
```

### 4.3 当前覆盖情况

| 指标 | 数值 |
|------|------|
| 后端端点总数 | 185 |
| 前端 API 调用总数 | 223 |
| 匹配成功 | 待完整匹配 |
| 后端有前端无 | 待排查 |
| 前端有后端无 | 待排查 |

---

## 5. 状态机测试契约

### 5.1 核心原则

```
状态转换方法
  ├─ ✅ 正向：该做的做了（状态变更、端点调用、按钮显示）
  └─ ❌ 负向：不该做的没做（不创建凭证、不误调端点、不存在按钮）
```

### 5.2 负向断言辅助类

| 后端 Java | 前端 TS |
|-----------|---------|
| `StateMachineTestHelper.verifyNoVoucherCreated()` | `StateMachineTestHelper.assertApiNotCalled()` |
| `StateMachineTestHelper.verifyNoDocumentCreated()` | `StateMachineTestHelper.assertElementNotExists()` |
| `StateMachineTestHelper.verifyNoInsert()` | `StateMachineTestHelper.assertFunctionNotCalled()` |

### 5.3 检查清单

- [ ] 每个状态转换方法有独立测试
- [ ] 每个测试至少 1 条负向断言
- [ ] 负向断言覆盖所有可能被意外污染的 Mapper/端点/按钮
- [ ] 异常路径也包含负向断言
- [ ] 使用辅助类简化断言

---

## 6. 问题排查

### 6.1 BusinessDocEdit 路由问题

**现象**：用户报告"找不到 BusinessDocEdit 路由定义"

**根因分析**：
1. 路由定义存在（`base.ts:157`）
2. 组件文件存在（`BusinessDocEdit.vue`）
3. **权限 `doc:edit` 不存在** — 后端没有定义此权限
4. 路由守卫检查 `hasPermission('doc:edit')` 返回 false
5. 用户被重定向到 `/403`

**解决方案**：
1. 后端添加权限 `doc:edit`（在 `t_permission` 表中）
2. 或修改路由权限为已存在的权限（如 `doc:list`）
3. 或移除权限检查（不推荐）

### 6.2 后端启动失败

**现象**：`PropertySourcesPlaceholderConfigurer` 异常

**根因分析**：
1. 配置文件中引用了未定义的属性
2. 可能是 `${xxx}` 占位符没有对应的值

**解决方案**：
1. 检查 `application.yml` / `application-dev.yml` 中的占位符
2. 确保所有 `${xxx}` 都有对应的配置值
3. 或提供默认值 `${xxx:default}`

---

## 7. 相关文件

| 文件 | 说明 |
|------|------|
| `backend/src/test/java/com/huicai/common/test/StateMachineTestHelper.java` | 后端负向断言辅助类 |
| `frontend/src/__tests__/helper/StateMachineTestHelper.ts` | 前端负向断言辅助类 |
| `docs/process/state-machine-test-checklist.md` | 状态机测试契约检查清单 |
| `backend/src/test/java/com/huicai/module/finance/service/impl/VoucherStateMachineServiceImplTest.java` | 现有状态机测试参考 |
| `frontend/src/__tests__/auth.store.test.ts` | 现有前端测试参考 |
| `frontend/src/__tests__/system.api.test.ts` | 现有前端测试参考 |
