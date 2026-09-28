# P94 SPEC — 报表年初口径统一、零值行规则与勾稽预警增强（P0 批次1）

> **版本**：V1.0（草案） | **最后修改**：2026-09-24 | **作者**：Sisyphus
> **状态**：✅ 已审核通过（批次1/4，老丁 2026-09-24；拍板：年初小计走后端 begin 口径聚合、悬空保护默认保留金额最大明细行、1012 按科目白名单纳入）
> **编号**：HUICAI-SPC-094 | 优先级：P0（信任级——报表年初列可信度）
> **依据**：两轮四表深度技术评估（资产负债表期初 vs 科目余额表期初"精神分裂"、有合计无明细悬空、勾稽预警缺页面级提示）
> **关联需求**：REQ-2026-090（年初口径统一）、REQ-2026-091（零值行规则）、REQ-2026-092（勾稽预警增强）——已登记 REQUIREMENTS_REGISTRY.md §八 V1.15
> **关联SPEC**：P88（③勾稽基础）、P89-A/B（格式与对比列）、P92-B（三分小计常驻）、P93（未分配利润行名）
> **test_ref（规划）**：`ReportServiceImplTest#p94_balanceSheet_yearStartSubtotalEqualsDetailSum`、`#p94_balanceSheet_yearStartMatchesSubjectBalanceOpening`、`BalanceSheetView`（组件测试，规划）零值行规则 2 例、`ReportServiceImplTest#p94_cashCheck_covers1012AndAlert`

---

## 0. 背景与根因（代码 trace，全部已核验）

### 0.1 评估结论：先纠偏两条不成立的指控

| 评估指控 | 核验结论 | 证据 |
|---|---|---|
| "资产负债表与科目余额表是两套数据源" | ❌ **不成立** | `ReportServiceImpl.java` L31-32 `subjectBalanceTable()` 与 L36-38 `balanceSheet()` **同调** `reportDataMapper.subjectBalance(period)`（`ReportDataMapper.java` L30），科目余额表亦走同一方法——单源 |
| "流动资产合计写死单点映射" | ❌ **不成立** | `ReportServiceImpl.java` L131-144 三分小计由 `subtotal()`（L223）对已分类行按 `account_type` + 科目段兜底**动态聚合**（P92-B，含 P92B-BD2 勾稽断言） |

### 0.2 成立的指控 → 两个根因

**根因 A（REQ-090）：年初列内部口径分裂 —— 明细用 1月期初，小计/合计用 1月期末**

- 明细行年初值：`BalanceSheetView.vue` L212-215 `yearStartValue(code)` = `yearStartRows.find(...).begin_balance`，即 **period=YYYY01 的期初余额（真正的年初）**
- 小计/合计年初值：`BalanceSheetView.vue` L199/L235 `yearStartData = balanceSheet(yearStartPeriod)`，而后端 `balanceSheet()` 全程聚合 **`end_balance`**（`ReportServiceImpl.java` L64 及 switch 各 case）——即 **1月期末（1月末数）**
- 导出侧同病：`ReportServiceImpl.java` L466-475 导出年初列取 `ysData = balanceSheet(yearStart)`，并注释"与前端同口径"（P89-B 选择，前端 L225-227 注释同步）
- `yearStartPeriod()`：`frontend/src/utils/period.ts` L19 = `period.slice(0,4)+'01'`
- **后果**：1月有发生额时，年初列"明细加总 ≠ 小计"，且小计与科目余额表期初对不上 → 用户看到的"BS 期初 vs 余额表期初精神分裂"即此

> 另注：P93 §5 已记录**测试数据断层**（实收资本 202401=300000 → 202407=200000 无衔接凭证）是运营项，非代码问题；本 SPEC 只治口径，不治数据断层。

**根因 B（REQ-091）：零值行只看期末 → "有合计无明细"悬空**

- `BalanceSheetView.vue` L217 `isZeroRow = (r) => Number(r.end_balance || 0) === 0` —— **只检查期末余额**
- L43/L113 注释：**P92-B 三分小计行常驻，不受 hideZeroRows 影响**
- **后果**：明细行"年初≠0、期末=0"被隐藏，而其所属小计行常驻并显示年初值 → 出现"非流动资产合计年初 100,000，下方无任何明细科目"的悬空行
- 同模式：`SubjectBalanceView.vue` L17/L78/L80-84、`CashFlowView.vue` L17/L48/L82

**现状可用部分（REQ-092 基线）**：P88③ 已实现 `cashCheckDiff/cashCheckOk`（`ReportServiceImpl.java` L335-340，本年累计版 L345-352）+ `CashFlowView.vue` L75-76 表内警示行；缺口：① 无页面级黄色 Alert ② `cashSubjectBalance` SQL 用 `s.code LIKE '100%'`（`ReportDataMapper.java` L84）→ 覆盖 1001/1002/1009，**不含 1012 其他货币资金**

**已实现项（本批不含开发，仅部署核验）**：P93 已把权益行名改为"未分配利润"（`ReportServiceImpl.java` L169），全库已无"含未结转"字样 → 评估基于旧构建，验收时重启后端截图核对即可。

---

## 1. 输入契约

- `balanceSheet(period)` / `subjectBalanceTable(period)` / `cashFlowStatement(period)`：入参不变（period，YYYYMM）
- REQ-090 预期新增（草案）：`balanceSheet` 返回补充 `yearStart*` 口径字段或新增 `beginBalanceSheet(yearPeriod)` 服务方法，**以 begin_balance 为聚合基数**
- REQ-091：前端 2 个独立复选框状态（本地 ref，无后端入参）
- REQ-092：`cashSubjectBalance` SQL 的科目范围扩至 `s.code IN ('1001','1002','1009','1012')`（或 `LIKE '100%' OR LIKE '101%'`，实现时按科目表核对）
- 权限：沿用报表查询权限，无新增鉴权面

## 2. 输出契约

- **REQ-090**：年初列小计/合计 = 同口径 begin 聚合；不变量 `年初列逐行加总 == 年初小计`（容差 0.01）；`年初合计 vs 科目余额表同期期初合计` 差异字段（草案：`yearStartCheckDiff`）
- **REQ-091**：行可见性 = 任一展示列（年初/期末/本期借/贷）非零即显示；悬空保护二选一配置（强制保留金额最大明细行 / 隐藏该汇总行）；两个独立复选框：「隐藏无发生额且无余额科目」「隐藏报表标准空白行」
- **REQ-092**：`cashCheckOk/cashCheckDiff` 口径扩至 1001+1002+1009+1012；页面级黄色 Alert（ElAlert，advice 级别）；导出 Excel 同步输出预警行
- 错误响应：沿用全局错误码字典，本批不新增阻断型错误码

## 3. 状态流转与副作用

- 报表只读聚合，**无状态机**
- 副作用声明：`DB_READ ONLY → t_subject/t_subject_balance/t_voucher* → SQL 聚合`；**无任何 INSERT/UPDATE/DELETE**
- 负向断言：任何路径不得写业务表、不得调整余额、不得自动修数（铁律 #1 人是唯一审核主体、#2 AI/预警只建议）

## 4. 异常处理

| 场景 | 处理 | 级别 |
|---|---|---|
| 年初期间（YYYY01）无余额数据 | 年初列显示 0/空（现状 `yearStartAvailable` 兜底），不抛错 | INFO |
| 年初对账差异 ≥ 0.01 | 提示行/Alert，**不阻断** | WARN |
| 勾稽差异 ≥ 0.01 | 页面黄色 Alert + 导出提示行，**不阻断、不改数** | WARN |
| 1012 无数据 | 等价于 0，口径向后兼容 | INFO |
| 新增字段异常/计算失败 | 降级为现状行为（旧口径返回）+ 日志，不 500 | ERROR（日志） |

事务：本批纯只读，无事务需求；若 REQ-090 触及后端字段扩展，仍为只读方法，不涉及铁律 #11 事务红线。

---

## 验收标准（BDD）

### 场景 1：年初列小计与明细同口径（REQ-090）
- **Given** 1 月存在非零发生额的账套
- **When** 打开任意期间资产负债表
- **Then** 年初列每个小计行 == 其明细行年初值加总（容差 0.01）
- **And** 负向断言：年初列小计不再等于 1 月期末聚合值（旧口径）

### 场景 2：年初合计与科目余额表期初对账（REQ-090）
- **Given** 同一账套、同一期间
- **When** 对比资产负债表年初合计与科目余额表期初合计
- **Then** 差异 < 0.01
- **And** 差异 ≥ 0.01 时输出 `yearStartCheckDiff` 提示且不改数

### 场景 3：年初非零、期末为零的明细行不被隐藏（REQ-091）
- **Given** 某明细行年初 = 100,000、期末 = 0，且「隐藏零值行」开启
- **When** 渲染资产负债表
- **Then** 该明细行可见（可见性按任一展示列非零判定）

### 场景 4：悬空保护——不出现"有合计无明细"（REQ-091）
- **Given** 某小计行年初值 ≠ 0，但其全部明细行按旧规则会被过滤
- **When** 渲染报表
- **Then** 按配置强制保留金额最大明细行，或隐藏该小计行（二选一），绝不同屏出现"合计有值、明细为空"
- **And** 负向断言：P92-B 三分小计勾稽断言（BD2）仍然通过

### 场景 5：两个零值行选项独立生效（REQ-091）
- **Given** 仅勾选「隐藏无发生额且无余额科目」
- **When** 切换「隐藏报表标准空白行」
- **Then** 两者互不联动、各自只影响约定范围（资产负债表/科目余额表/现金流量表同步生效）

### 场景 6：勾稽预警覆盖 1012 且页面级提示（REQ-092）
- **Given** 1001+1002+1009+1012 期末合计与现金流量表现金行差异 ≥ 0.01
- **When** 打开现金流量表
- **Then** 页面顶部黄色 Alert 显示差异金额，表内警示行保留，导出 Excel 含预警行
- **And** 差异 < 0.01 时无 Alert（现状 `cashCheckOk` 回归不变）

### 场景 7：只读铁律（全需求）
- **Given** 执行上述全部操作
- **When** 全流程完成
- **Then** 无任何业务表写入、无状态变更（副作用仅 READ）
- **And** 未分配利润行名部署核验：重启后端后权益区显示"未分配利润"（P93 回归）

**测试约束**：每个场景对应一个 `@Test`；Then 至少含一个正向断言 + 一个负向断言；核心口径测试用真实 DB（Testcontainers），Mock 测不出 DB 约束（陷阱 4.3-7）。

---

## 竞品对标（铁律 #15）

| 竞品 | 年初列口径 | 零值行 | 勾稽预警 | 慧财差异 |
|---|---|---|---|---|
| 用友 U8/NC | 年初列 = 科目年初余额全量聚合，明细与小计强制同源 | 零行可配置，按整行判断 | 表内差异栏 + 结账前校验 | 慧财年初小计误用 1 月末——**口径低于行业基线** |
| 金蝶云星空 | 期初栏取期间期初，小计同口径 | 「隐藏零行」默认关，按行全部展示列判断 | 勾稽不平红字提示 | 慧财默认开零值行且只看期末，制造悬空 |
| QuickBooks / Xero | Opening Balance 与期末分列清晰 | 不隐藏含期初余额的行 | 差异即显示 on-screen warning | 慧财缺页面级 Alert（REQ-092 补齐） |
| SAP FI（F.01） | 期初/期末/本期发生三栏同源 | 层级折叠不丢行 | 状态灯式勾稽展示 | 慧财预警仅表内一行 |

**结论**：本批三项均为把慧财拉到行业基线，无超前设计。变更审计（铁律 #16）：变更原因=两轮评估证实的口径分裂与悬空；对标结论=上表；影响范围=资产负债表/现金流量表前端与导出、`cashSubjectBalance` SQL；已同步登记 REQ-2026-090~092 与版本历史 V1.15。

---

## 版本历史

| 版本 | 日期 | 作者 | 变更 |
|---|---|---|---|
| V1.0 | 2026-09-24 | Sisyphus | 初稿：REQ-090/091/092 批次1，全部根因经 file:line 核验；纠偏两条不成立指控；委派写手故障（限流+模型路由）后按 AGENTS §4.5-16 直写 |
| V1.1 | 2026-09-24 | Sisyphus | 批次1/4 老丁审核通过；三项拍板落定：年初小计=后端 begin 口径聚合、悬空保护=保留金额最大明细行、1012=科目白名单 |
