# P32: AmountExpressionResolver 重写为 BigDecimal（消除金额精度漂移风险）

> 状态：**待执行（2026-06-25 创建，C1 审计落地）**
> 优先级：**Critical**（财务软件核心模块）
> 依据：C1 审计发现 + skill `huicai-java-backend` §0 + DESIGN.md §10 引用
> 工期：单批交付，预计 1-2 个 commit
> 风险：🟡 中（需要重测精度回归，但调用方接口不变）

---

## 0. 背景与目标

### 问题
文件 `backend/src/main/java/com/huicai/common/util/AmountExpressionResolver.java` 内部用 `double` 计算金额，最后才转 `BigDecimal`：

```java
// line 31 (当前实现)
double result = new Parser(expr).parse();
return BigDecimal.valueOf(result).setScale(2, RoundingMode.HALF_UP);
```

中间运算精度漂移不可控：
- `0.1 + 0.2` → `0.30000000000000004`（double） → `setScale(2, HALF_UP)` → 偶然正确
- `1234567.89 * 0.0001` → double 漂移后转 BigDecimal 可能变 `123.46` 而非 `123.456789`
- 复杂模板表达式（`{{amount}} * (1 + {{tax_rate}})`）多次运算后误差累积

### 触发场景
调用方：`TemplateEngine.java:57` 调 `evaluateTemplate()`
用途：凭证模板引擎计算分录金额（VoucherTemplate 配置驱动）
**影响**：借贷可能不平（虽然 Service 层有兜底校验，但违反财务铁律）

### 目标
将 Parser 内部全部 `double` 替换为 `BigDecimal`，**接口签名不变**，调用方 0 改动。

---

## 1. 改动清单

| # | 改动 | 文件 | 风险 |
|---|------|------|------|
| 1 | `Parser.parse()` 返回类型 `double` → `BigDecimal` | `AmountExpressionResolver.java` | 🟡 中 |
| 2 | `parseAddSub()` 改用 `.add()` / `.subtract()` | 同上 | 🟡 中 |
| 3 | `parseMulDiv()` 改用 `.multiply()` / `.divide()` + `MathContext.DECIMAL64` | 同上 | 🟡 中 |
| 4 | `parseUnary()` 负号改用 `.negate()` | 同上 | 🟢 低 |
| 5 | `parsePrimary()` 数字字面量 `new BigDecimal(token)` | 同上 | 🟢 低 |
| 6 | `evaluate()` 入口仍然 `setScale(2, HALF_UP)` 返回 2 位小数 | 同上 | 🟢 低 |
| 7 | 新增 5+ 精度漂移回归测试 | `AmountExpressionResolverTest.java` | 🟢 低 |
| 8 | 验证 `TemplateEngine` 调用方 0 改动 | - | 🟢 低 |

---

## 2. 关键技术要点

### 2.1 除法精度策略
**推荐方案**：`MathContext.DECIMAL64`（16 位有效数字，覆盖所有财务金额场景）

```java
private static final MathContext MC = MathContext.DECIMAL64;

// 除法
BigDecimal result = left.divide(right, MC);
```

**备选方案**：在 `evaluate()` 入口统一处理精度
```java
public static BigDecimal evaluate(String expression) {
    // ... 解析
    return result.setScale(2, RoundingMode.HALF_UP);
}
```

### 2.2 负号处理
```java
// 旧
private double parseUnary() {
    if ("-".equals(tok)) { pos++; return -parsePrimary(); }
    // ...
}

// 新
private BigDecimal parseUnary() {
    if ("-".equals(tok)) { pos++; return parsePrimary().negate(); }
    // ...
}
```

### 2.3 数字字面量
```java
// 旧
return Double.parseDouble(tok);

// 新
return new BigDecimal(tok);
```

### 2.4 加减乘除
```java
// 旧
left += parseMulDiv();
left -= parseMulDiv();
left *= parseMulDiv();
left /= parseMulDiv();

// 新
left = left.add(parseMulDiv(), MC);
left = left.subtract(parseMulDiv(), MC);
left = left.multiply(parseMulDiv(), MC);
left = left.divide(parseMulDiv(), MC); // MC 已有精度+舍入规则
```

---

## 3. 测试要求

### 3.1 现有 9 个测试必须全绿
文件 `src/test/java/com/huicai/common/util/AmountExpressionResolverTest.java` 已覆盖：
- 加法 / 减法 / 乘法 / 除法
- 复合运算 / 括号 / 小数
- 空表达式 → 0
- 负数 / 大数（假设存在）

### 3.2 新增精度漂移回归测试（必须）
```java
@Test
void evaluate_double_trap_0_1_plus_0_2() {
    // 关键: 0.1 + 0.2 在 double 下是 0.30000000000000004
    // 重写后必须 == 0.30
    assertEquals(0, new BigDecimal("0.30").compareTo(
        AmountExpressionResolver.evaluate("0.1+0.2")
    ));
}

@Test
void evaluate_large_amount_multiplication() {
    // 1234567.89 * 0.0001, double 漂移后可能是 123.46 (错)
    // BigDecimal 应得 123.456789
    BigDecimal result = AmountExpressionResolver.evaluate("1234567.89*0.0001");
    assertEquals(0, new BigDecimal("123.456789").compareTo(result));
}

@Test
void evaluate_subtraction_precision() {
    // 100 - 99.99 = 0.01 (double 漂移可能得 0.010000000000005116)
    assertEquals(0, new BigDecimal("0.01").compareTo(
        AmountExpressionResolver.evaluate("100-99.99")
    ));
}

@Test
void evaluate_template_with_tricky_values() {
    // 模板表达式 + 漂移值
    Map<String, BigDecimal> vars = new HashMap<>();
    vars.put("a", new BigDecimal("0.1"));
    vars.put("b", new BigDecimal("0.2"));
    BigDecimal result = AmountExpressionResolver.evaluateTemplate("{{a}}+{{b}}", vars);
    assertEquals(0, new BigDecimal("0.30").compareTo(result));
}

@Test
void evaluate_division_non_terminating() {
    // 1/3 不应抛 ArithmeticException (非无限小数循环)
    // 用 MathContext.DECIMAL64 时结果 = 0.3333333333333333 (16 位有效数字)
    BigDecimal result = AmountExpressionResolver.evaluate("1/3");
    assertNotNull(result);
    assertTrue(result.compareTo(BigDecimal.ZERO) > 0);
}
```

### 3.3 验证 TemplateEngine 调用方 0 改动
- `TemplateEngine.java:57` 调 `evaluateTemplate()` 行为应保持一致
- 跑 `TemplateEngineTest`（如存在）必须全绿
- 跑 `VoucherTemplateServiceImplTest` 必须全绿

### 3.4 跑 mvn test 全绿
基线 389/0/0 + 新增 5+ = 394+/0/0

---

## 4. 验收标准

- [ ] `AmountExpressionResolver.java` 全文无 `double` / `float` 关键字（除注释外）
- [ ] `grep "double\|float" AmountExpressionResolver.java` 只匹配注释行
- [ ] `mvn test -Dtest=AmountExpressionResolverTest` 全绿（含新增 5 个 case）
- [ ] `mvn test` 全绿（基线 389 + 新增 5+ = 394+/0/0）
- [ ] `TemplateEngine` 调用代码无任何改动
- [ ] git diff 显示仅 `AmountExpressionResolver.java` + `AmountExpressionResolverTest.java` 2 个文件

---

## 5. 提交规范

按 skill `huicai-java-backend` §8.3 铁律：
- commit body 严禁加 TODO/待办/⚠️/🚨 标注
- 首行 < 50 字符
- 建议 message 格式：
  ```
  refactor(P32): AmountExpressionResolver 内部计算改用 BigDecimal
  
  消除金额表达式解析的 double 精度漂移风险
  ```

---

## 6. 注意事项

### 6.1 接口签名不变
```java
// 这两个方法签名 0 改动
public static BigDecimal evaluate(String expression)
public static BigDecimal evaluateTemplate(String template, Map<String, BigDecimal> variables)
```

### 6.2 不要改返回精度
- `evaluate()` 返回 `BigDecimal.setScale(2, HALF_UP)` — 保持 2 位小数
- 内部计算用 `MathContext.DECIMAL64`（16 位有效数字）
- 现有 9 个测试期望精度 2 位，**重写后必须保持 2 位**

### 6.3 TemplateEngine 调用方 0 改动验证
- 跑 `git grep "AmountExpressionResolver"` 应只有 2 个文件：
  - `TemplateEngine.java`（调用方）
  - `AmountExpressionResolverTest.java`（测试）
- 跑 `git diff TemplateEngine.java` 应**无任何改动**

### 6.4 不破坏现有 SAFE_PATTERN
```java
private static final String SAFE_PATTERN = "^[0-9+\\-*/().\\s]+$";
// 不允许: 字母/变量/函数（防注入）
// 模板变量走 evaluateTemplate() 单独处理
```

---

## 7. 执行入口

### 方案 A：等 OpenCode profile 修好后委派
- 创建 kanban 卡片，assign `backend` profile
- 卡片 body 直接复用本文档 §1-§6

### 方案 B：Hermes 直接重写（备选）
- 适用于 OpenCode 长期不可用
- 文件改动 2 个：AmountExpressionResolver.java + AmountExpressionResolverTest.java
- 不影响其他文件

### 方案 C：人工自己改
- 老丁自行编辑，重测后 commit

---

## 8. 关联文档

- C1 审计报告：会话内 9/9 综合审计（2026-06-25）
- skill `huicai-java-backend` §8.6（避免双精度陷阱通用规则）
- DESIGN.md §10.5（迁移规范）
- V54 任务：docs/tasks/P32-...（本任务）
- 调用方：`TemplateEngine.java:57`

---

**起草依据**：C1 审计结论（2026-06-25 21:18）
**起草人**：Hermes（C1 审计自动化产出）
**审核状态**：待老丁审阅
