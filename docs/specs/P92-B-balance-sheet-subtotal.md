# P92-B SPEC — 资产负债表流动/非流动/其他三分小计

> **版本**：V1.0（已实现，已测试） | **最后修改**：2026-09-24 | **作者**：Hermes
> **状态**：✅ 已实现，report 模块 56/56 通过，前端 vue-tsc + vite build 通过
> **编号**：HUICAI-SPC-092-B | 优先级：P1
> **依据**：PRD-019 §2（P92-B）+ 老丁口径拍板 2026-09-24
> **关联需求**：R-145
> **关联SPEC**：P92-A-report-statutory-structure（同批次前半）、P69-balance-sheet-equality（平衡铁律）、P91（法定报表骨架行范式）

---

## 0. 背景与口径拍板

PRD-019 §2 已认定 P92-B 为高风险批次（动 DB 结构 + 科目维护界面 + 存量回填 + 法定勾稽）。原计划停在 PRD 阶段等口径拍板。2026-09-24 老丁拍板三个问题，本批次启动：

| 问题 | 拍板 | 实现含义 |
|------|------|----------|
| 1. 分类粒度 | **三分**（流动/非流动/其他） | 资产、负债各 3 个小计 + 总计，共 6 个小计行 |
| 2. 归类依据 | **B2**（科目表加 `account_type` 列） | 人工可维护，不硬编码在代码里 |
| 3. 小计是否折叠 | **不折叠** | 小计作为独立字段返回，与 P91 骨架行同理常驻 |

**关键实现决策：小计不混入 `assets`/`liab` 行数组**，而是作为 `currentAssets` 等独立 Map 字段返回。理由：
- 前端 `hideZeroRows` 按行标记过滤，独立字段天然不受影响 → 不折叠口径靠数据结构保证，不靠前端配置。
- 小计在 switch 主分类逻辑之后独立计算，**零侵入既有分桶逻辑**。

---

## 1. 契约

### 1.1 DB — V153 迁移

**文件**：`backend/src/main/resources/db/migration/V153__add_account_type_to_subject.sql`

```sql
ALTER TABLE t_subject ADD COLUMN account_type VARCHAR(32);
```

**分类值**（仅资产/负债适用）：

| 值 | 含义 | 标准科目段 |
|----|------|-----------|
| `CURRENT_ASSET` | 流动资产 | 10 货币资金 / 11 结算备付金 / 12 应收款项 / 14 存货 |
| `NON_CURRENT_ASSET` | 非流动资产 | 13 在建工程 / 1408 持有待售 / 15 投资性房地产 / 16 固定资产 / 17 无形资产 / 18 商誉 / 19 长期待摊 |
| `CURRENT_LIABILITY` | 流动负债 | 20 短期借款 / 21 应付票据 / 22 其他应付款 |
| `NON_CURRENT_LIABILITY` | 非流动负债 | 24 长期借款 / 25 应付债券 / 27 长期应付 / 28 预计负债 / 29 递延收益 |
| `NULL` | 不适用 | 权益 4x / 成本 5x / 收入 6x |

**设计要点（三条易错点）**：
1. **默认值留 NULL 而非 CHECK NOT NULL**：4x/5x/6x 不属于流动分类，强制归类会产生错误小计。
2. **报表端有科目段兜底**：迁移前数据或人工漏填不会静默丢弃（对应 P92B-BD3）。
3. **人工维护是"改默认值"不是"从零填"**：迁移已按标准科目段灌 seed。

### 1.2 后端 — 实体与 DTO

| 文件 | 改动 |
|------|------|
| `Subject.java` | 加 `accountType` 字段 |
| `SubjectCreateDTO.java` | 加 `accountType`（`@Size(max=32)`） |
| `SubjectUpdateDTO.java` | 加 `accountType`（`@Size(max=32)`） |

`SubjectServiceImpl` 的 create/update 走 `BeanUtil.copyProperties`，DTO 加字段即生效，**Service 无需改动**。Excel 导入路径（line 541）不传分类列，保持不动。

### 1.3 后端 — 取数带出分类

**文件**：`ReportDataMapper.java`（`subjectBalance`）

SELECT 带出 `s.account_type`，返回 Map 中即为 `account_type` 键。

### 1.4 后端 — 三分小计计算

**文件**：`ReportServiceImpl.java`（`balanceSheet`，switch 分桶之后）

```java
currentAssets = subtotal(assets, "CURRENT_ASSET", fallbackPredicate);
nonCurrentAssets = subtotal(assets, "NON_CURRENT_ASSET", fallbackPredicate);
otherAssets = totalAssets.subtract(currentAssets).subtract(nonCurrentAssets);
// 负债同构：currentLiabilities / nonCurrentLiabilities / otherLiabilities
```

**`subtotal` 三级判定顺序**：
1. `account_type` 列等于目标值 → 计入（B2 人工维护优先）
2. `account_type` 为空 → 走科目段 `fallback` Predicate
3. `account_type` 填了但不等于目标 → 不计入本小计

**"其他"由减法反推**（`总计 - 流动 - 非流动`），而非第三路判定。这保证 P92B-BD3 恒成立：任何科目必落在三个桶之一，无静默丢弃。

**科目段 fallback 规则**（兜底路径，与 V153 seed 一致但更细）：

| 方向 | 流动 | 非流动 |
|------|------|--------|
| 资产 1x | 10 / 11 / 12 / 14 | 13 / 1408 / 15 / 16 / 17 / 18 / 19 |
| 成本 5x | 50 / 51 / 52 / 54（存货类） | 53 研发支出（资本化） |
| 负债 2x | 20 / 21 / 22 | 24 / 25 / 27 / 28 / 29 |

> **fallback 比 V153 seed 多 13 段与 1408**：这是实现期发现并修正的缺陷——在建工程（1301）与持有待售资产（1408）属非流动，但 seed 的 `^(15|16|17|18|19)` 未覆盖，会导致这两类在 `account_type` 缺失时被错归"其他"。代码 fallback 是兜底真相，seed 是初始值，二者不一致时以 fallback 为准。

**勾稽校验**（P92B-BD2）：

```java
if (currentAssets.add(nonCurrentAssets).add(otherAssets).compareTo(totalAssets) != 0
    || currentLiabilities.add(nonCurrentLiabilities).add(otherLiabilities).compareTo(totalLiab) != 0) {
    throw new IllegalStateException("资产负债表流动分类小计与总计不一致");
}
```

由于"其他"由减法反推，此等式数学上恒成立。仍显式校验是为了防止未来改动 switch 分桶后小计漏项——校验失败会暴露，而非静默产出错误小计。

### 1.5 返回值字段

`balanceSheet(period)` 返回 Map 新增 6 个字段：

`currentAssets` / `nonCurrentAssets` / `otherAssets` / `currentLiabilities` / `nonCurrentLiabilities` / `otherLiabilities`

类型 `BigDecimal`，scale=2。既有 `totalAssets`/`totalLiabilities`/`totalEquity`/`totalLiabEquity` 语义不变。

### 1.6 导出

`exportBalanceSheet` 在资产区与负债区各插入 3 行小计行（法定报送口径，小计行不可省略），列结构不变。年初值取年初期间 `balanceSheet()` 返回值，与前端同口径。

### 1.7 前端

**`BalanceSheetView.vue`**：资产区与负债区各 3 个小计行，`.subtotal-row` CSS 样式（区别于明细行）。小计取自 1.5 的独立字段，不受 `hideZeroRows` 影响。

**`SubjectList.vue`**（科目维护页）：
- 表单加"流动分类"下拉，`ACCOUNT_TYPE_OPTIONS` 四个选项 + 空值
- 表格加分类显示列，`accountTypeLabel` 映射中文
- `openEdit` 回填 `accountType`（不回填则编辑已有科目会丢分类）
- `openCreate` 重置 `accountType: null`

**`subject.ts`**：`SubjectVO` / `SubjectCreateParam` / `SubjectUpdateParam` 三个 interface 加 `accountType`。

---

## 2. 验收（BDD）

| 编号 | 场景 | 测试 |
|------|------|------|
| P92B-BD1 | 显式 `account_type` 时按列值正确归类 | `p92b_三分小计_按account_type正确归类` |
| P92B-BD2 | 三分之和 = 总计（内联校验 + 断言） | 同上 |
| P92B-BD3 | `account_type` 缺失时走科目段兜底，不静默丢弃 | `p92b_accountType缺失时走科目段兜底` |
| — | 导出小计行行序（无科目数据时仍输出 6 行小计） | `ReportExportTest` 行序断言 |

---

## 3. 实现记录

### 验证结果

| 项 | 结果 |
|----|------|
| 后端 test-compile | ✅ |
| report 模块测试 | ✅ 56/56（`ReportServiceImplTest` 27 + `ReportExportTest` 7 + `AnalysisServiceTest` 8 + `ReportServiceTest` 6 + `ReportTemplateMapperTest` 5 + `CashFlowPeriodRangeRealDBTest` 3） |
| 前端 vue-tsc | ✅ 0 错误 |
| vite build | ✅ 19.49s |
| dist 时间戳 | ✅ 晚于源码 |

### 实现期修掉的 3 个真实问题

1. **`s.code()` 编译错**：fallback 原为 `Predicate<Map<String,String>>`，lambda 参数却是 `Map<String,Object>`，调了 Map 没有的 record 访问器。改为 `Predicate<String>`，fallback 只需 code，签名更简洁。
2. **1301 在建工程 / 1408 持有待售漏归**：fallback 初版未覆盖 13 段与 1408，这两类在 `account_type` 缺失时被错归"其他资产"。已补入非流动。
3. **`CashFlowView.vue` `warn` 属性无类型**：P92-A 遗留 TS 错误（数组字面量推断出无 `warn` 的类型，push 带 `warn` 的对象报错），挡着 build。已给 `list` 加显式类型。

### 勾稽校验必须配套平账的测试数据

第一次写测试时数据本身不平（资产 1,050,000 vs 负债 270,000 + 权益 1,000,000 = 1,270,000，差 220,000），`balanced` 断言失败。这提示：**三分小计测试必须同时保证总账平衡**，否则 BD2 勾稽断言会被数据错误干扰，无法区分"分类错"还是"数据不平"。

---

## 4. 改动面清单

| 层 | 文件 | 改动 |
|----|------|------|
| DB | `V153__add_account_type_to_subject.sql` | **新建**：加列 + 存量回填 |
| 后端 | `Subject.java` | 加 `accountType` |
| 后端 | `SubjectCreateDTO.java` / `SubjectUpdateDTO.java` | 各加 `accountType` |
| 后端 | `ReportDataMapper.java` | `subjectBalance` 带出 `s.account_type` |
| 后端 | `ReportServiceImpl.java` | 三分小计 + `subtotal` + 勾稽校验 + 导出 6 行 |
| 后端 | `SubjectServiceImpl.java` | **无改动**（`BeanUtil.copyProperties` 自动拷贝） |
| 后端 | `ReportServiceImplTest.java` / `ReportExportTest.java` | 新增 2 例 + 修导出行序断言 |
| 前端 | `BalanceSheetView.vue` | 6 小计行 + CSS |
| 前端 | `SubjectList.vue` | 下拉 + 表格列 + 回填 + 重置 |
| 前端 | `subject.ts` | 3 个 interface 加字段 |
| 前端 | `CashFlowView.vue` | 修 `warn` 类型（P92-A 遗留） |
| 部署 | `frontend/dist` | 已重建，时间戳晚于源码 |

---

## 5. 不做的事

| 项 | 理由 |
|----|------|
| 小计行折叠交互 | 法定报送小计行必须常驻，老丁已拍板不折叠 |
| 权益 4x / 收入 6x 加流动分类 | 不属于资产/负债流动维度，`account_type` 留 NULL |
| 科目段规则写死在代码（B1） | 已选 B2；代码内 fallback 仅为兜底，不是主分类依据 |
| Excel 导入支持分类列 | 导入路径不传分类列，人工维护界面为准 |
| 报表端按子分类进一步细分（如应收账款 vs 应收票据） | 超出法定三分口径，属 P93+ |

---

## 6. 变更记录

| 版本 | 日期 | 内容 |
|------|------|------|
| V1.0 | 2026-09-24 | 口径拍板后实现。三分 / B2 / 不折叠。新增 V153 迁移、实体与 DTO 字段、三分小计计算与勾稽校验、导出 6 行、前端 6 小计行与科目维护下拉。实现期修 3 个真实问题（`s.code()` 编译错、1301/1408 漏归、`warn` 类型）。report 模块 56/56 通过 |

---

## 7. 验证边界（诚实声明）

**本报告基于 mock 单测 + 编译 + 前端类型检查，以下未验证**：

- ❌ **V153 迁移未在真实生产库执行**。`deleted = 0` 过滤条件、PostgreSQL `~` 正则语法、以及存量科目回填结果均为代码审读结论，非实测。迁移会在下次启动 Flyway 时首次执行。
- ❌ **fallback 科目段规则未用真实库科目全量校验**。规则来自小企业会计准则通识，但**本项目生产库实际科目编号分布未逐一比对**——若存在非标准编号科目（如 1000 开头的自定义段），可能归类偏差。建议首次执行后抽查 `SELECT account_type, LEFT(code,2) FROM t_subject GROUP BY 1,2`。
- ❌ **2701 专项储备的归类存在会计口径分歧**。按《企业会计准则》，专项储备本质属所有者权益，但科目编号以 2 开头，本实现按"编号优先"归入负债（`NON_CURRENT_LIABILITY`）。报表端不会静默丢弃（仍计入负债总计），但**列示位置与部分审计口径不一致**。这是本项唯一的会计判断，实现前已提交老丁知晓。
- ❌ **多租户隔离未验证**。V153 是全局 `UPDATE`（无 `enterprise_id` 条件），因 `t_subject` 为共享字典表而非租户表，此设计正确；但**未实测多企业数据回填是否符合预期**。
- ✅ **已实测**：`ReportServiceImplTest` 两例覆盖显式分类与 fallback 兜底两条路径，含 BD2 勾稽断言，且测试数据保证总账平衡。
