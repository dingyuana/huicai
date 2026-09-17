# P77 SPEC — 折旧与资产统计报表（资产分类汇总 + 折旧计提汇总）

> **版本**：V1.0 | **最后修改**：2026-09-17 | **作者**：Hermes
> **状态**：📝 草案（待审核）
> **编号**：HUICAI-SPC-077 | 优先级：P1
> **依据**：竞品差距核查（DSN-竞品差距与管理类报表核查.md G-2）+ DSN-固定资产管理.md §8
> **目标**：提供资产分类汇总（家底快照）+ 折旧计提汇总（部门×类别 × 期间），补齐"整本资产账"的管理视图
> **工期**：2 天（后端聚合 + 前端视图）

> **关联需求**：REQ-2026-087（折旧与资产统计报表）
> **依赖 SPEC**：S-23（固定资产全生命周期）、资产计提服务

## 版本历史

| 版本 | 日期 | 变更 |
|---|---|---|
| V1.0 | 2026-09-17 | 初版。基于代码实证（AssetCardEntity/AssetDepreciationEntity）设计资产分类汇总 + 折旧计提汇总 |

---

## 0. 背景与问题

现状盘点（代码实证）：

1. **数据载体已就绪**：`t_asset_card`（`AssetCardEntity`：`assetCode/assetName/categoryId/deptId/originalValue/residualValue/usefulLife/depreciationMethod/status/accumulatedDepreciation/netValue`）+ `t_asset_depreciation`（`AssetDepreciationEntity`：`assetId/period/depreciationAmount/accumulatedDepreciation/netValue`）。
2. **缺口**：端点只有 `/{id}/depreciation`（单卡片折旧明细）+ `/depreciate/{period}`（批量计提），**无"整本资产账"的聚合汇总**——按类别看家底（多少原值、折了多少、还剩多少净值）、按部门×类别看某段期间计提了多少。
3. **竞品基线**：金蝶"多维度折旧报表统计"。

**业务影响**：无法回答"某部门固定资产投入多少、净值率多少"，资产盘点/处置决策无汇总底稿。

---

## 1. 竞品对标

| 竞品 | 资产报表 | 关键设计 | 结论 |
|------|---------|---------|------|
| **金蝶云星空** | 固定资产报表（资产类别汇总表、折旧统计表、资产明细表） | 按类别汇总原值/累计折旧/净值；折旧统计按期间；多维度 | ✅ 本 SPEC 对标类别汇总 + 折旧统计 |
| **用友 U8** | 资产报表（卡片汇总、折旧明细） | 类别 × 原值/净值 | ✅ |
| **SAP** | Asset by category / depreciation | 类别维度 + 期间折旧 | ✅ |

**结论**：三家一致"类别家底快照 + 期间折旧统计"两表。慧财现有单卡片查询，缺聚合表达层。

---

## 2. 改动清单总览

| # | 优先级 | 改动 | 文件 | 风险 | 状态 |
|---|--------|------|------|------|------|
| 1 | P0 | 资产分类汇总端点 `GET /api/sme/asset/v1/reports/category-summary`（类别 × 数量/原值/累计折旧/净值/本期应提/净值率） | `AssetReportController`（新建）+ `AssetCategorySummaryVO` + Service 聚合 | ✅ 低（纯聚合） | 📝 待开发 |
| 2 | P0 | 折旧计提汇总端点 `GET /api/sme/asset/v1/reports/depreciation-summary`（期间区间 × 部门×类别） | 同上 | ✅ 低 | 📝 待开发 |
| 3 | P0 | 两表 Excel 导出（`/export`） | 同上 | ✅ 低 | 📝 待开发 |
| 4 | P0 | 前端资产统计视图（分类汇总 + 折旧统计 + 导出） | `frontend/src/views/asset/report/AssetStatView.vue` | ✅ 低 | 📝 待开发 |

---

## 3. 四段模板（输入/输出/状态/异常）

### 3.1 输入契约

**报表 A（资产分类汇总）**：`GET /api/sme/asset/v1/reports/category-summary`
- **必填**：`period`（YYYYMM，本期应提折旧的计提期）
- **可选**：`category_id`（按类别过滤）
- 前置：数据权限拦截器注入 enterprise_id（铁律#6）

**报表 B（折旧计提汇总）**：`GET /api/sme/asset/v1/reports/depreciation-summary`
- **必填**：`period_from`、`period_to`（YYYYMM 区间，`period_from ≤ period_to`）
- **可选**：`group_by`（`CATEGORY` 默认 / `DEPT` / `DEPT_CATEGORY` 交叉）
- 前置：同上

### 3.2 输出契约

**报表 A（category-summary）**：

```jsonc
{
  "period": "202609",
  "rows": [
    { "categoryId": 1, "categoryName": "电子设备", "qty": 34,
      "originalValue": 260000.00, "accumulatedDepreciation": 130000.00,
      "netValue": 130000.00, "currentDepreciation": 5400.00, "netRatio": 0.50 }
  ],
  "total": { "qty": 120, "originalValue": 1500000.00, "netValue": 820000.00 }
}
```

**报表 B（depreciation-summary）**：

```jsonc
{
  "periodFrom": "202601", "periodTo": "202606", "groupBy": "DEPT_CATEGORY",
  "rows": [
    { "dimKey": "研发部/电子设备", "deptId": 2, "deptName": "研发部",
      "categoryId": 1, "categoryName": "电子设备",
      "assetCount": 20, "depreciated": 45000.00,
      "openingAccumulated": 90000.00, "closingAccumulated": 135000.00 }
  ],
  "total": { "depreciated": 210000.00 }, "consistent": true
}
```

**口径定义（对齐代码实证）**：

- **报表 A 范围**：`t_asset_card` `status ∈ (IN_USE, STOPPED)`（未处置资产，排除 DISPOSED/SCRAPPED/DRAFT），按 `categoryId` 分组。
  - `originalValue` = Σ 卡片原值；`accumulatedDepreciation` = Σ 卡片累计折旧；`netValue` = Σ 卡片净值；
  - `currentDepreciation` = 该类别卡片在 `period` 的应提折旧，**复用计提服务同一算法源**（直线法/双倍余额递减法），不重复实现公式；
  - `netRatio` = netValue / originalValue（originalValue=0 返回 null）。
- **报表 B 范围**：`t_asset_depreciation` 期间区间内记录，按 `group_by` 分组（部门取 `t_asset_card.deptId`，类别取 `categoryId`）；
  - `depreciated` = Σ 区间内 `depreciationAmount`；`assetCount` = 去重资产数；
  - `openingAccumulated` = 区间前一期累计折旧（取 `period_from` 前一个月的 `accumulatedDepreciation`，无则 0）；`closingAccumulated` = `period_to` 末累计折旧。
- **恒等式**：`openingAccumulated + depreciated == closingAccumulated`（每行校验，`BigDecimal.compareTo`；失败标 `consistent=false`）。
- **金额**：全部 `BigDecimal`（铁律#7）。
- **导出**：EasyExcel，列同上 + 合计行。

### 3.3 状态流转（数据口径，非状态机）

```
t_asset_card（status，只读纳入口径）
  IN_USE / STOPPED(=IDLE) → 纳入分类汇总
  DISPOSED / SCRAPPED     → 排除
  DRAFT                   → 排除
```

> 报表只读，**不触发任何计提动作**（计提仍走 `depreciate/{period}` 人工入口，铁律#1）。

### 3.4 异常处理

| 场景 | 处理 | 错误码 |
|------|------|--------|
| `period`/`period_from`/`period_to` 缺失或格式非法（非 6 位） | `BusinessException`（400） | P77_001 |
| 报表 B `period_from > period_to` | `BusinessException`（400） | P77_002 |
| `group_by` 非法 | `BusinessException`（400） | P77_003 |
| 类别/期间无数据 | 返回空 rows + total 全 0，不报错 | — |
| 恒等式断言失败 | 日志告警 + 返回标 `consistent=false`，不阻断 | P77_004（WARN） |
| 数据隔离 | 拦截器注入 enterprise_id | — |

**事务**：纯聚合只读，无写操作，不加 `@Transactional`。

---

## 4. BDD 验收场景

### 场景 1：分类汇总口径正确（L2 集成，Testcontainers PG16）

```gherkin
Given 类别"电子设备"下 2 张 IN_USE 卡片（原值 10000/20000，累计折旧 5000/10000，净值 5000/10000），1 张 DISPOSED 卡片
When 查询 category-summary?period=202609
Then 该行 qty=2, originalValue=30000.00, accumulatedDepreciation=15000.00, netValue=15000.00
And 负向断言：DISPOSED 卡片不计入（qty=2 而非 3）
```

### 场景 2：本期应提复用计提算法（L1，Service 纯逻辑）

```gherkin
Given 一张 IN_USE 卡片（原值 10000，残值 1000，年限 5，直线法，已提 1 期）
When 查询该卡片 currentDepreciation
Then 与 计提服务 depreciate 算出的单期金额一致（同一算法源，不另写公式）
```

### 场景 3：折旧计提汇总恒等式（L2 集成，🟡 数据口径）

```gherkin
Given 研发部/电子设备 类别在 202601-202606 计提 45000，期初累计折旧 90000
When 查询 depreciation-summary?period_from=202601&period_to=202606&group_by=DEPT_CATEGORY
Then 该行 openingAccumulated=90000.00, depreciated=45000.00, closingAccumulated=135000.00
And openingAccumulated + depreciated == closingAccumulated
```

### 场景 4：期间参数守卫（L2 Controller）

```gherkin
Given period_from=202606&period_to=202601（from>to），或 group_by=FOO
When 调用 depreciation-summary
Then 分别返回 400 P77_002 / P77_003
```

### 场景 5：处置资产排除（L2 集成）

```gherkin
Given 一张 DISPOSED 卡片、一张 IN_USE 卡片同属一类别
When 查询 category-summary
Then 仅 IN_USE 计入（负向断言：DISPOSED 不出现在 qty/originalValue/netValue）
```

### 场景 6：数据权限隔离（L2 集成，🟡 跨租户）

```gherkin
Given 企业 A 的资产卡片
When 企业 B 用户查询 category-summary
Then 结果不含企业 A 资产（拦截器注入 enterprise_id）
```

---

## 5. 影响范围

| 维度 | 影响 |
|------|------|
| 数据库 | 无 schema 变更（纯聚合查询） |
| 后端 | 新建 `AssetReportController` + 2 VO + Service 聚合（复用计提算法源） |
| 前端 | +1 视图（AssetStatView.vue，两表 Tab）+ 路由注册 |
| 测试 | +6 测试（6 场景） |
| API | 新增 4 端点（2 查询 + 2 导出） |

---

## 6. 遗留事项

- **资产处置损益报表**（远期）：处置/报废的账面净值 vs 处置收入差异，另立项
- **资产台账明细导出**（后续）：逐卡片全字段 Excel（现有 `/page` 已可导出，增强列配置）
- **净值率预警**（远期）：低于阈值高亮，与报表中心指标告警框架联动

---

```yaml
# === MACHINE-READABLE CONTRACT ===
contract_version: "1.0"
entity: AssetCardEntity
module: sme-asset
table: t_asset_card

states:
  DRAFT:
    description: "草稿（分类汇总排除）"
    initial: true
    terminal: false
  IN_USE:
    description: "在用（纳入分类汇总）"
    initial: false
    terminal: false
  STOPPED:
    description: "停用（=IDLE 别名，纳入分类汇总）"
    initial: false
    terminal: false
  DISPOSED:
    description: "已处置（排除）"
    initial: false
    terminal: true
  SCRAPPED:
    description: "已报废（排除，预留态）"
    initial: false
    terminal: true

transitions:
  - id: T-01
    from: DRAFT
    to: IN_USE
    trigger: activate
    precondition: "status == DRAFT"
    postcondition: "status = IN_USE；开始纳入分类汇总口径"
    side_effects: []
    test_ref: test_activate_includes_in_summary
  - id: T-02
    from: IN_USE
    to: DISPOSED
    trigger: dispose
    precondition: "status in (IN_USE, STOPPED)"
    postcondition: "status = DISPOSED；排除出分类汇总（负向断言）"
    side_effects: []
    test_ref: test_disposed_excluded_from_summary
  - id: T-03
    from: IN_USE
    to: STOPPED
    trigger: stop
    precondition: "status == IN_USE"
    postcondition: "status = STOPPED；仍纳入分类汇总"
    side_effects: []
    test_ref: test_stopped_still_included

constraints:
  - id: C-01
    type: business
    rule: "报表只读，不触发计提（计提走 depreciate/{period} 人工入口，铁律#1）"
    enforcement: "Service 无写操作，只 SELECT 聚合"
  - id: C-02
    type: business
    rule: "分类汇总仅 status in (IN_USE, STOPPED)；本期应提复用计提算法源，不重复公式"
    enforcement: "Service 调既有折旧计算服务取 currentDepreciation"
  - id: C-03
    type: immutability
    rule: "折旧统计恒等式 opening + depreciated == closing，失败标 consistent=false 不阻断"
    enforcement: "BigDecimal.compareTo"
  - id: C-04
    type: business
    rule: "全部金额 BigDecimal NUMERIC(18,2)"
    enforcement: "VO 字段 BigDecimal"

acceptance_tests:
  - id: AT-001
    description: "分类汇总口径正确"
    method: test_category_summary_scope
    assertion: "qty=2 仅 IN_USE/STOPPED，DISPOSED 排除"
    status: missing
  - id: AT-002
    description: "本期应提复用算法源"
    method: test_current_depreciation_reuses_service
    assertion: "currentDepreciation == 计提服务单期金额"
    status: missing
  - id: AT-003
    description: "折旧统计恒等式"
    method: test_depreciation_identity
    assertion: "openingAccumulated + depreciated == closingAccumulated"
    status: missing
  - id: AT-004
    description: "期间参数守卫"
    method: test_period_params_guard
    assertion: "from>to 返回 400 P77_002；group_by 非法 400 P77_003"
    status: missing
  - id: AT-005
    description: "处置资产排除"
    method: test_disposed_excluded
    assertion: "DISPOSED 卡片不计入 qty/originalValue/netValue"
    status: missing
  - id: AT-006
    description: "数据权限隔离"
    method: test_tenant_isolation
    assertion: "跨企业查询返回空（enterprise_id 拦截）"
    status: missing

out_of_scope:
  - "资产处置损益报表（远期）"
  - "计提动作本身（S-23 / depreciate 端点负责）"
  - "schema 变更（无）"

dependencies:
  - spec: S-23
    relation: "资产卡片 5 态状态机 + 折旧算法源（本 SPEC 读侧依赖）"
```

> **文档结束**。关联：[S-23-固定资产全生命周期管理](./S-23-固定资产全生命周期管理.md) | [DSN-固定资产管理](../design/DSN-固定资产管理.md) §8
