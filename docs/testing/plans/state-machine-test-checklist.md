# 状态机测试契约与检查清单

> **编号**：HUICAI-TST-003
> **版本**：V1.0 | **修改日期**：2026-07-07 | **修改人**：Hermes | **修改内容**：添加编号头部

**问题**：状态机方法的副作用（side effect）在测试中容易被忽略。
一个真实案例：`OutputInvoiceStateMachineServiceImpl.confirm()` 不仅改变了状态
（PENDING_REVIEW → CONFIRMED），还通过 `postProcessAfterInvoiceConfirm()` 自动创建了
业务单、应收单和**凭证**——但 UI 上"生成凭证"是独立按钮，不应在审核阶段触发。

**教训**：每个状态转换如果只测"状态对了"，不测"没有多做事"，这类缺陷就会漏到生产。

---

## 契约

每个状态转换方法必须同时验证两件事：

| 类型 | 验证内容 | 工具 |
|------|----------|------|
| ✅ 正向断言 | "该做的事做了"——状态正确变更 | `assertEquals(目标状态, entity.getStatus())` |
| ❌ 负向断言 | "不该做的事没做"——无意外副作用 | `verify(mapper, never()).insert(any())` |

**缺一不可**。只测正向不测负向 = 测试未完成。

---

## 检查清单

### 1. 编写测试时 —— 逐项确认

- [ ] 每个 `transition` 方法（`submitForReview`/`confirm`/`reject`/`revert`/`void`/`markVouchered` 等）都有独立测试方法
- [ ] 每个测试方法至少包含 **1 条负向断言**
- [ ] 负向断言覆盖了所有**可能被该状态机意外污染**的 Mapper：

  | 状态机实现 | 必须检查的负向 Mapper | 原因 |
  |------------|----------------------|------|
  | `OutputInvoiceStateMachineServiceImpl` | `voucherMapper`, `voucherEntryMapper` | 审核阶段不应生凭证 |
  | `ReceivableStateMachineServiceImpl` | `voucherMapper`, `docMapper` | 应收确认不应生凭证/单据 |
  | `PayableStateMachineServiceImpl` | `voucherMapper`, `docMapper` | 应付确认不应生凭证/单据 |
  | `VoucherStateMachineServiceImpl` | 无（只做校验不写 DB） | 跳过 |

- [ ] 使用 `StateMachineTestHelper.verifyNoXxx()` 系列方法简化负向断言
- [ ] 异常路径也包含负向断言（方法抛异常后，确保无意外 insert）

### 2. 新增状态机方法时

- [ ] 实现新方法时**提前写好测试**（TDD 风格）
- [ ] 先在测试里定义**不该做什么**（`never()` verify），再写实现
- [ ] 新方法如果引入了新的副作用 Mapper，需追加到检查清单表中

### 3. Code Review 时

- [ ] 变更涉及 `*StateMachineServiceImpl` 类
- [ ] 对应的 `*StateMachineServiceImplTest` 文件**有变更**（或新增）
- [ ] 测试中**存在 `never()` 调用** → 搜索 `verify.*never` 确认
- [ ] 负向断言覆盖了**新增代码新引入**的所有 Mapper
- [ ] 所有测试通过后合并

### 4. CI / Git Hook 可选

```bash
# 示例：自动检测状态机变更是否缺少对应测试
git diff --name-only HEAD~1 | grep StateMachineServiceImpl.java | \
  while read f; do
    test_file="${f/main\/java/test\/java}" 
    test_file="${test_file/ServiceImpl/ServiceImplTest}"
    if [ ! -f "$test_file" ]; then
      echo "❌ 状态机变更缺少对应测试: $f → $test_file"
      exit 1
    fi
    # 检查是否包含 never() 断言
    if ! grep -q 'never()' "$test_file"; then
      echo "❌ 测试缺少负向断言 (never() verify): $test_file"
      exit 1
    fi
  done
```

---

## 使用示例

### OutputInvoice 状态机 —— 完整测试模板

```java
@ExtendWith(MockitoExtension.class)
class OutputInvoiceStateMachineServiceImplTest {

    @Mock OutputInvoiceMapper invoiceMapper;
    @Mock VoucherMapper voucherMapper;
    @Mock VoucherEntryMapper voucherEntryMapper;
    @Mock BusinessDocMapper docMapper;
    @Mock BusinessDocEntryMapper docEntryMapper;
    @Mock ReceivableMapper receivableMapper;

    private OutputInvoiceStateMachineServiceImpl service;
    // ... @BeforeEach 注入全部 mock

    @Test
    void confirm_shouldChangeStatusOnly() {
        // 正向：状态变更
        assertEquals(InvoiceStatus.CONFIRMED, inv.getStatus());

        // 负向：无凭证、无额外单据
        StateMachineTestHelper.verifyNoVoucherCreated(voucherMapper, voucherEntryMapper);
        StateMachineTestHelper.verifyNoReceivableCreated(receivableMapper);
    }
}
```

### 异常路径也要负向断言

```java
@Test
void confirm_wrongStatus_shouldThrowAndNotCreateVoucher() {
    // given: 发票状态为 PENDING_CONFIRM（不是 PENDING_REVIEW）
    OutputInvoiceEntity inv = StateMachineTestHelper.createInvoice(1L, InvoiceStatus.PENDING_CONFIRM);
    when(invoiceMapper.selectById(1L)).thenReturn(inv);

    // when
    assertThrows(BusinessException.class, () -> service.confirm(1L, 1L));

    // then：抛异常后也不应产生任何副作用
    StateMachineTestHelper.verifyNoInsert(voucherMapper, docMapper, receivableMapper);
}
```

---

## 需补测试的状态机（当前缺口）

| 状态机实现 | 当前测试 | 状态 |
|------------|----------|------|
| `OutputInvoiceStateMachineServiceImpl` | ❌ 无 | 高优先级 — 已发现缺陷 |
| `ReceivableStateMachineServiceImpl` | ❌ 无 | 中 |
| `PayableStateMachineServiceImpl` | ❌ 无 | 中 |
| `VoucherStateMachineServiceImpl` | ✅ `VoucherStateMachineServiceImplTest` | 低（无写操作，缺口在异常路径） |

---

## 前端测试模式

前端同样适用"正向 + 负向断言"模式，分三层：

| 层 | 工具 | 负向断言方式 |
|----|------|-------------|
| **API 单元** | Vitest + `vi.mock(request)` | `assertApiNotCalled(spy, 'mark-vouchered')` |
| **组件单元** | Vue Test Utils + Vitest | `assertElementNotExists(wrapper, '[data-action=...]')` |
| **e2e** | Playwright | `expect(voucherCountAfter).toBe(voucherCountBefore)` |

### 前端状态机测试模板

```typescript
// ===== API 层 =====
test('confirmOutputInvoice 不应误调 markVouchered', async () => {
  mockRequest.post.mockResolvedValue(undefined)
  await taxApi.confirmOutputInvoice(1)

  // 正向：confirm 端点被调用
  StateMachineTestHelper.assertApiCalled(mockRequest.post, '/confirm')
  // 负向：mark-vouchered 端点未被调用
  StateMachineTestHelper.assertApiNotCalled(mockRequest.post, 'mark-vouchered')
})

// ===== 组件层 =====
test('PENDING_REVIEW 状态不应显示"生成凭证"按钮', async () => {
  const wrapper = mount(OutputInvoiceList, { props: { /* status=PENDING_REVIEW */ } })
  // 负向：生成凭证按钮不应该存在
  StateMachineTestHelper.assertElementNotExists(wrapper, '[data-action="markVouchered"]')
})

// ===== e2e 层 =====
test('审核通过后凭证数不应增加', async ({ page }) => {
  const countBefore = await page.locator('.voucher-row').count()
  await page.click('text=审核通过')
  await page.waitForResponse('**/confirm')
  // 负向：凭证数不变
  await expect(page.locator('.voucher-row')).toHaveCount(countBefore)
})
```

### 相关文件

- `src/__tests__/helper/StateMachineTestHelper.ts` — 前端状态机负向断言辅助类

## 相关文件

- `src/test/java/com/huicai/common/test/StateMachineTestHelper.java` — 负向断言辅助类
- `src/test/java/com/huicai/module/finance/service/impl/VoucherStateMachineServiceImplTest.java` — 现有状态机测试参考
