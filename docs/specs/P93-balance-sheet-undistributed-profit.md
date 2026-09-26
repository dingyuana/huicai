# P93 SPEC — 资产负债表权益区未分配利润取数

> **版本**：V1.0（已实现，已测试） | **最后修改**：2026-09-24 | **作者**：Hermes
> **状态**：✅ 已实现，ReportServiceImplTest 28/28 通过
> **编号**：HUICAI-SPC-093 | 优先级：P2
> **依据**：会计准则术语对齐 + 老丁指令
> **关联需求**：PRD-019 报表法定结构增强
> **关联SPEC**：P92-B（资产负债表三分小计）、P69-balance-sheet-equality（平衡铁律）、P88①（本年利润显式行）

---

## 0. 背景与口径拍板

### 问题

中国会计准则下，资产负债表所有者权益区的"未分配利润"由两个科目构成：
- **4103** 本年利润
- **4104** 利润分配-未分配利润

原代码仅捕获 4103，且行名显示为"本年利润(含未结转)"。存在两个问题：
1. **术语不规范**：会计准则使用"未分配利润"，非"本年利润"
2. **取数不全**：4104 未被捕获，未来若建立该科目，数据会丢失

### 口径拍板

| 问题 | 拍板 | 实现含义 |
|------|------|----------|
| 行名 | **改为"未分配利润"** | 对齐会计准则术语 |
| 4104 取数 | **走单独捕获，不进 equity loop** | 避免与"未分配利润"合成行重复计入 |
| 合成行 code 字段 | **保持 "4103"** | 4103 是主科目，4104 从属 |

**关键实现决策：4104 不进 equity loop**，而是在循环外单独捕获到 `profit4104`，再与 `profit4103` 合并计算。理由：
- equity loop 只处理 4001/4002/4101，4104 若加进去会产生单独一行"利润分配"，与"未分配利润"合成行重复计入
- 4103/4104 的区分由主分类逻辑处理，合成行统一展示，符合准则口径

---

## 1. 契约

### 1.1 代码改动

**文件**：`backend/src/main/java/com/huicai/base/report/service/impl/ReportServiceImpl.java`

#### 改动 1：变量声明（第 48 行后）

```java
BigDecimal profit4103 = BigDecimal.ZERO;
BigDecimal profit4104 = BigDecimal.ZERO;  // 新增
```

#### 改动 2：4104 捕获块（第 102-104 行后）

```java
if (code.equals("4103")) {
    profit4103 = signed;
} else if (code.equals("4104")) {   // 新增
    profit4104 = signed;             // 新增
} else {
    equity.add(row);
    totalEquityExProfit = totalEquityExProfit.add(signed);
}
```

#### 改动 3：未分配利润计算（第 152 行）

```java
// 旧
BigDecimal currentYearProfit = profit4103.add(currentPeriodProfit);
// 新
BigDecimal currentYearProfit = profit4103.add(profit4104).add(currentPeriodProfit);
```

#### 改动 4：行名修改（第 165 行）

```java
// 旧
cypRow.put("name", "本年利润(含未结转)");
// 新
cypRow.put("name", "未分配利润");
```

cypRow 的 code 字段保持 "4103" 不变。equity Map 的 key 同步改为 "未分配利润"。

#### 改动 5：注释同步

第 159、161、464、502 行附近的注释里提到"本年利润(含未结转)"的地方，改为"未分配利润"。

### 1.2 测试改动

**文件**：`backend/src/test/java/com/huicai/base/report/service/impl/ReportServiceImplTest.java`

2 处断言字符串同步：
- 第 278 行：`assertEquals("本年利润(含未结转)", ...)` → `assertEquals("未分配利润", ...)`
- 第 491 行：`"本年利润(含未结转)".equals(...)` → `"未分配利润".equals(...)`

---

## 2. 验收标准

| 项 | 标准 | 结果 |
|----|------|------|
| 编译 | `mvn test-compile` 通过 | ✅ EXIT_CODE=0 |
| 测试 | `mvn test -Dtest=ReportServiceImplTest` 通过 | ✅ 28/28 通过 |
| 前端 | 无需改动（全走后端 name 字段） | ✅ 无硬编码 |

---

## 3. 风险与约束

1. **4104 科目当前不存在**：数据库中尚未建立 4104 科目，代码已支持但无数据。未来建立后自动生效。
2. **4104 不得进入 equity loop**：若后续有人误将 4104 加入 equity loop 的 `code.equals("4001") || code.equals("4002") || code.equals("4101")` 判断中，会导致重复计入。本 SPEC 明确禁止。
3. **code 字段语义**：合成行的 code 字段保持 "4103"，表示该行的主科目来源。前端若按 code 字段做特殊处理，需注意 4104 的数据也包含在其中。

---

## 4. 提交记录

| commit | 内容 |
|--------|------|
| `e17d6a6` | feat(report): P93 资产负债表权益区支持4104利润分配，行名改为未分配利润 |

---

## 5. 已知未解决

年初数矛盾（实收资本年初 30 万→期末 20 万）**不是代码问题**：
- 202407 期间没有任何涉及 4001 的凭证分录
- 余额从 202401 的 300000 直接变为 202407 的 200000，缺少衔接凭证
- 根因：测试数据断层，需补建衔接凭证解决，非代码层面
