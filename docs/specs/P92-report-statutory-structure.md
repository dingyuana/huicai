# P92-A SPEC — 现金流量表本年累计金额

> **版本**：V0.1（契约草案，**未实现、未测试**） | **最后修改**：2026-09-24 | **作者**：Hermes
> **状态**：⚠️ 契约已定稿，代码未落地
> **编号**：HUICAI-SPC-092 | 优先级：P1
> **依据**：PRD-019 §1（P92-A）+ DSN-018 §3/§4（P92-A 行）
> **关联需求**：R-145
> **关联SPEC**：P88-report-statement-correctness（累计数口径）、P89-report-display-drilldown（导出抬头/千分位）
> **test_ref**：（待实现后绑定）预期 ReportServiceImplTest 新增 3 例 + ReportExportTest 新增 1 例 + CashFlowYtdRealDBTest 1 例

---

## 0. 背景

PRD-019 §1 已定 P92-A 为独立小批次。代码级摸底结论：

- `cashFlowStatement(period)`（ReportServiceImpl.java:209）返回**扁平 Map**，非 `items` 数组（PRD 草案此处表述不准，已在 SPEC 修正）。
- 实际是**两次独立取数**：`cashFlowData(period)` 算三大活动流入/流出/净额；`cashSubjectBalance(period)` 取 `begin_cash`/`end_cash` 做勾稽。
- 现有"本期金额"= 单期间流量，**本年累计是全新取数**，前端无法算出。

### 关键决策：不复制 SQL，改 mapper 为期间范围参数

`cashFlowData` 的注解 SQL 有 40+ 行，含 `flow_type` 的 EXISTS 子查询判定（区分投资/经营活动，靠对手方科目 15%/16%/17%/18%/19% 判定）。**复制成第二份 SQL 会导致两处 flow_type 判定各自演化**——将来任何一处判定规则变更都会漏掉另一处，产生口径漂移。这是财务取数的硬伤。

故选**改签名**：`cashFlowData(period)` → `cashFlowData(startPeriod, endPeriod)`，SQL 只存在一份。代价是连带改 5 处测试 mock + 1 处生产调用（P89-C 踩过 Mapper 签名连带破坏的坑，本次已完整清点）。

### 现成范式（不用新写）

- 期间范围过滤：`cumulativeData(yearStart, period)`（ReportDataMapper.java:53-70，`v.period >= #{yearStart} AND v.period <= #{period}`）——P88② 给利润表做累计数用的，**同一模式**。
- 年初期间计算：`period.substring(0, 4) + "01"`（ReportServiceImpl.java:165，既有写法）。

---

## 1. 契约

### 1.1 后端 — ReportDataMapper

**文件**：`backend/src/main/java/com/huicai/base/report/mapper/ReportDataMapper.java`（现 :126）

```java
// 改前
List<Map<String, Object>> cashFlowData(@Param("period") String period);

// 改后
List<Map<String, Object>> cashFlowData(@Param("startPeriod") String startPeriod,
                                       @Param("endPeriod") String endPeriod);
```

**SQL 改动（仅此一处）**：`WHERE` 段 `v.period = #{period}` → `v.period >= #{startPeriod} AND v.period <= #{endPeriod}`。

> 其余 40+ 行 SQL **一字不改**，尤其 flow_type 的 EXISTS 判定。

### 1.2 后端 — ReportServiceImpl

**文件**：`backend/src/main/java/com/huicai/base/report/service/impl/ReportServiceImpl.java`（现 :209-259）

```java
String yearStart = period.substring(0, 4) + "01";
List<Map<String, Object>> rows = reportDataMapper.cashFlowData(period, period);          // 本期
List<Map<String, Object>> ytd =  reportDataMapper.cashFlowData(yearStart, period);       // 本年累计
```

抽取现有单期间聚合逻辑为私有方法（**不复制代码**），本期与 YTD 各调一次：

```java
private Map<String, BigDecimal> aggregateFlow(List<Map<String, Object>> rows) { /* 现有 switch 聚合 */ }
```

**返回 Map 新增 7 个字段**（camelCase，仅新增、不改既有）：

| 字段 | 含义 |
|------|------|
| `operatingInYtd` / `operatingOutYtd` / `operatingNetYtd` | 经营活动本年累计流入/流出/净额 |
| `investingInYtd` / `investingOutYtd` / `investingNetYtd` | 投资活动本年累计 |
| `financingInYtd` / `financingOutYtd` / `financingNetYtd` | 筹资活动本年累计 |
| `totalNetYtd` | 本年累计净流量 |
| `openingCashYtd` / `closingCashYtd` | 本年累计期初/期末现金 |
| `cashCheckDiffYtd` / `cashCheckOkYtd` | 本年累计勾稽校验 |

### 1.3 关键口径 — 本年累计的期初现金

这是本项唯一的口径判断，需在实现前确认：

```
openingCashYtd = cashSubjectBalance(yearStart).begin_cash   // 年初期间(1月)的期初余额
closingCashYtd = openingCashYtd + totalNetYtd
```

**理由**：本年累计的期初现金 = 本年 1 月 1 日的现金余额 = 1 月凭证的期初余额。若误用查询期间 `period` 的 begin_cash，累计列的期初会变成当月月初，跨年时明显错误。

### 1.4 前端 — CashFlowView

**文件**：`frontend/src/views/report/cash-flow/CashFlowView.vue`

1. 加第 3 列（在"金额"列之后）：
```html
<el-table-column label="本年累计金额" align="right" width="180">
  <template #default="{ row }">
    <span :class="amountClass(row.bold, row.ytd, row.warn)">{{ fmtAmount(row.ytd) }}</span>
  </template>
</el-table-column>
```
2. `rows` computed 每项加 `ytd` 字段，取 `r.xxxYtd`。
3. 期初/期末现金行沿用 `fixed: true`（P91 已确立的骨架行保护，累计列同样不可隐藏）。

### 1.5 后端导出 — exportCashFlow

**文件**：ReportServiceImpl.java:382（现 headers 3 列）

```java
String[] headers = {"项目", "行次", "本期金额", "本年累计金额"};
rows.add(List.of("经营活动现金流入", "1", data.get("operatingIn"), data.get("operatingInYtd")));
// ... 每行同步加 Ytd 取值
```

> **导出框架不需改**：`writeExcel`（:272）的 `cols = headers.length`，merge 跨列自动跟随。P89-D 踩过的坑是 3 列表做 `merge(2,2)` 单格合并——本次 merge 为 `(0,0,0,3)` 四列跨合并，合法，**无需 `cols >= 4` 守卫**。

---

## 2. 验收（BDD）

- **P92A-BD1**：Given 某企业 202407–202410 均有现金流凭证 When 查 202410 现金流量表 Then 各行"本年累计金额" = 202401–202410 该类别累计，"本期金额"不变
- **P92A-BD2**：Given 查询期间为年初期间（如 202601）When 渲染 Then 本期金额 == 本年累计金额
- **P92A-BD3**：Given 期初现金 When 本年累计列渲染 Then `openingCashYtd` = 年初期间的 `begin_cash`（非查询期间的 begin_cash）
- **P92A-BD4**：Given 本年累计勾稽不一致 When 渲染 Then 累计列显示差异警告行，本期列不受影响
- **P92A-BD5**：Given 导出 Excel When 打开 Then 表头含"本年累计金额"列，共 4 列，与前端一致

---

## 3. 测试计划（待实现时落地）

| 层级 | 内容 | 数量 |
|------|------|------|
| L1 单测 | `cashFlowStatement` YTD 字段存在且非空、本期/YTD 互不污染、年初期间本期==累计 | 3 例（改 `ReportServiceImplTest`） |
| L1 单测 | 导出 4 列表头 + 逐格取值（POI 读回校验，照 ReportExportTest 现有写法） | 1 例 |
| L3 RealDB | 真实库跨年数据验证 YTD = 年初至本期累计，且与 `cashSubjectBalance` 勾稽 | 1 例（新建 `CashFlowYtdRealDBTest`） |

**RealDB 命名铁律**：用 `*RealDBTest` 后缀，避免覆盖既有 mock 测试（P84 BUG-3 教训）。

### 需连带更新的测试 mock（Mapper 签名变更连带）

```
src/test/java/com/huicai/base/report/service/impl/ReportExportTest.java:173  cashFlowData(any())
src/test/java/com/huicai/base/report/service/impl/ReportExportTest.java:253  cashFlowData(PERIOD)
src/test/java/com/huicai/base/report/service/impl/ReportServiceImplTest.java:321  cashFlowData("202606")
src/test/java/com/huicai/base/report/service/impl/ReportServiceImplTest.java:402  cashFlowData("202607")
src/test/java/com/huicai/base/report/service/impl/ReportServiceImplTest.java:420  cashFlowData("202607")
```

改法：`cashFlowData(any(), any())` / `cashFlowData(PERIOD, PERIOD)` / `cashFlowData("202606", "202606")`。
注意 `ReportServiceImpl` 现在会**调两次** mapper，stub 需覆盖同一返回或分别 stub。

---

## 4. 改动面清单

| 层 | 文件 | 改动 |
|----|------|------|
| DB | — | **无迁移**（纯查询层改动） |
| 后端 | `ReportDataMapper.java` | mapper 签名 + SQL 期间条件（1 处） |
| 后端 | `ReportServiceImpl.java` | cashFlowStatement 双查 + 抽聚合方法 + 7 字段；exportCashFlow 4 列 |
| 后端 | `ReportService.java` | 无（接口签名不变） |
| 后端 | 测试 5 处 | mapper mock 签名连带 |
| 前端 | `CashFlowView.vue` | 加累计列 + rows 加 ytd 字段 |
| 前端 | `report.ts` | 无（API 参数不变，只多返回字段） |
| 部署 | `frontend/dist` | 重建，dist 时间戳须晚于源码 |

**零 DB 迁移**是本批次成本可控的核心原因。

---

## 5. 不做的事

| 项 | 理由 |
|----|------|
| 资产负债表分类小计（P92-B） | 需新增 `t_subject.account_type`，会计口径待老丁拍板（PRD-019 §2） |
| 复制 `cashFlowData` 为 YTD 变体 | 40+ 行含 flow_type 判定的 SQL 复制会导致口径漂移，改签名为唯一方案 |
| 前端计算累计 | 后端无数据支撑，前端无法算出三大活动分类累计 |
| 利润表营业外收支/所得税层级 | 后端无对应数据字段，PRD-019 §3 已裁剪 |
| 现金流量表间接法 | PRD-018 §6 已裁剪（无数据基础） |

---

## 6. 变更记录

| 版本 | 日期 | 内容 |
|------|------|------|
| V0.1 | 2026-09-24 | 契约草案。代码级摸底后修正 PRD 草案两处表述：①`cashFlowStatement` 返回扁平 Map 非 `items` 数组；②"照抄 trendData"不准——trendData 是趋势聚合，正确范式为 P88② 已有的 `cumulativeData(yearStart, period)`。确定"改 mapper 为期间范围参数"方案，避免复制 40+ 行含 flow_type 判定的 SQL 导致口径漂移 |

---

## 7. 验证边界（诚实声明）

**本 SPEC 是 V0.1 契约草案，代码未落地，所有测试计划均未执行。**

- ❌ **未实现**：mapper 签名、service 双查、前端累计列、导出 4 列 均无代码改动。
- ❌ **未测试**：§3 全部 5 例测试不存在。
- ❌ **未验证**：§1.3 的 `openingCashYtd = 年初期间 begin_cash` 口径**未经老丁确认**，也未用真实数据验算。这是本项唯一的财务口径判断，实现前需拍板。
- ✅ **已核实**：改动面清点准确——1 处生产调用、5 处测试 mock、前端组件与导出复用关系均已 grep 确认。
- ✅ **已核实**：A/B 代码完全独立（后端方法分离、mapper 分离、前端组件分离，A 对 B 字段引用数为 0），A 可单独交付不依赖 B 的口径拍板。
