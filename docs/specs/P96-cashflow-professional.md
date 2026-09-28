# P96 SPEC — 现金流量表：凭证级现金流项目绑定、筹资活动识别与间接法补充资料（P1 批次3）

> **版本**：V1.0（草案） | **最后修改**：2026-09-24 | **作者**：Sisyphus
> **状态**：📝 SPEC 草案待审核（分批送审 批次3/4）
> **编号**：HUICAI-SPC-096 | 优先级：P1（专业性——准则符合度）
> **依据**：两轮四表深度技术评估（"现金流量表是科目映射法，预付设备款错归经营、筹资活动永远为 0、无间接法补充资料"）
> **关联需求**：REQ-2026-095（凭证级现金流项目绑定与筹资识别）、REQ-2026-096（间接法补充资料）——已登记 REQUIREMENTS_REGISTRY.md §八 V1.15
> **关联SPEC**：P88③（现金闭环与勾稽）、P94（现金口径含 1012，批次1）
> **test_ref（规划）**：`ReportServiceImplTest#p96_cashFlow_borrowingGoesFinancingIn`、`#p96_cashFlow_equityInjectionFinancingIn`、`#p96_cashFlow_prepaidEquipmentInvestingOut`、`#p96_cashFlow_threeNetsEqualCashDelta`、`#p96_indirectMethod_reconcilesToDirectOperatingNet`、`#p96_flowAssignment_manualOverrideWins`

---

## 0. 背景与根因（代码 trace，全部已核验）

### 0.1 现行取数：科目段映射法，结构性缺失筹资

`ReportDataMapper.java` `cashFlowData`（L94-127）：从凭证分录中银行存款(1002)借贷发生额，按**对方科目** CASE：

- 对方 `s2.code LIKE '15%' OR '16%' OR '17%' OR '18%' OR '19%'` → `INVESTING_IN/OUT`（L105-106、L113-114）
- **ELSE → `OPERATING_IN/OUT`**
- **全 SQL 无任何产出 `FINANCING_*` 的分支**

而 `ReportServiceImpl.java` L375-376 已写 `case "FINANCING_IN"/"FINANCING_OUT"` 汇总分支——**上游永远不给值的死路径**。

### 0.2 评估指控核验

| 指控 | 结论 | 证据 |
|---|---|---|
| 预付账款(1123)设备款错归经营活动 | ✅ **成立** | 1123 不匹配 15-19% → ELSE → OPERATING（设备预付款应为投资活动） |
| 筹资活动永远为 0 | ✅ **成立** | SQL 无 FINANCING 分支（L105-114） |
| 无凭证级现金流项目 | ✅ **成立** | `V111__create_cash_flow_table.sql` 已建 `t_voucher_cash_flow`（voucher_id / flow_type: OPERATING_*, INVESTING_*, FINANCING_* / amount，含 RLS 行级安全与索引），但全 Java 代码仅 `SchemaValidator`、`SystemClearController` 引用——**从未写入、从未读取**（休眠表） |
| 现金勾稽口径缺 1012 | ✅ **成立** | `ReportDataMapper.java` L84 `s.code LIKE '100%'` → 1001/1002/1009，不含 1012（归 REQ-092/P94 治理，本批引用同一口径） |

### 0.3 现有可用基础

- P88③ 闭环已存在：`openingCash/closingCash/cashCheckDiff/cashCheckOk`（`ReportServiceImpl.java` L335-352，本年累计版同步）
- 期间累计聚合：`FlowSums.aggregate(reportDataMapper.cashFlowData(yearStart, period))`（L302-304）
- 迁移基线：`db/migration/` 已占用至 **V154** → 本批新迁移用 **V155+**（实现前复核，Flyway 版本不得重复——陷阱 4.4-9/10）

---

## 1. 输入契约

### REQ-095 现金流项目绑定与筹资识别
- **规则配置（草案）**：借贷科目组合 → `flow_type` 规则表（新增 `t_cash_flow_rule`，V155+），覆盖：
  - 筹资：2001 短期借款 / 2501 长期借款（借入 → FINANCING_IN；归还本金 → FINANCING_OUT）、4001 实收资本（资本注入 → FINANCING_IN）、4104 利润分配（支付股利 → FINANCING_OUT）
  - 投资：购置 1601 固定资产 / 17xx 无形资产等（→ INVESTING_OUT）；1123 设备预付款结算至 16xx（→ INVESTING_OUT，**修正现行错归**）
  - 经营：其余（保底 OPERATING_*，与现状兼容）
- **凭证级人工指定（草案）**：凭证分录/凭证录入界面增加现金流项目字段（人是唯一审核主体：人工指定优先于规则，修改留审计——铁律 #1/#5）
- **存量回填（草案）**：人工触发「批量重建」端点，按规则回填 `t_voucher_cash_flow`，带事务
- **降级读取**：`t_voucher_cash_flow` 无记录的期间/凭证 → 回退现行 `cashFlowData` 科目段算法，保证不断报

### REQ-096 间接法补充资料
- 输入：利润表净利润、资产负债表相关科目期初/期末、直接法经营活动净额（只读聚合）
- 无新增用户输入

## 2. 输出契约

- `cashFlowStatement` 输出在现有六组基础上：
  - FINANCING_IN/OUT/净额 由死路径变为真实值（字段名不变，向后兼容 P88③/P83 消费方）
  - 不变量：**经营+投资+筹资三类净额之和 == 期末现金 − 期初现金**（容差 0.01，并入 P88③ 勾稽）
  - 新增（草案）：`supplement` 附表数组——净利润 + 折旧/摊销等非付现 + 财务费用 + 经营性应收应付变动（应收/预收/存货/应付/预付期初期末差剔除投资筹资影响）→ 经营活动现金流量净额
  - 附表勾稽：`间接法经营活动净额 == 直接法经营活动净额`（容差 0.01）
- 流量类型明细可追溯到 voucher_id（行级）

## 3. 状态流转与副作用

- 报表读取：**只读聚合**，无状态机
- 写路径仅两处，均**人工触发**：
  | 操作 | 副作用 | 约束 |
  |---|---|---|
  | 凭证过账时规则分配流量 / 人工指定保存 | `DB_INSERT/UPDATE → t_voucher_cash_flow` | `@Transactional(rollbackFor = Exception.class)`（铁律 #11）；`deleted=0` 逻辑删除（#12）；审计快照（#5） |
  | 批量重建端点（人工点击） | `DB_DELETE(逻辑)+INSERT → t_voucher_cash_flow` | 同上；**绝不修改凭证/科目余额/报表事实** |
- 负向断言：任何路径不得改写 `t_voucher` 状态、不得写 `t_subject_balance`、不得自动生成记账凭证（人是唯一审核主体）；DTO/VO 隔离（#13）、BusinessException（#14）

## 4. 异常处理

| 错误码（草案） | HTTP | 级别 | 提示文案 |
|---|---|---|---|
| `CF_001` | 400 | WARN | "现金流项目规则冲突：同一科目组合命中多条规则" |
| `CF_002` | 400 | WARN | "流量类型不在合法枚举内" |
| `CF_003` | 409 | ERROR | "批量重建进行中，请稍后重试"（分布式锁） |
| `CF_004` | 422 | WARN | "勾稽失败：三类净额之和与现金变动差异 ≥ 0.01"（提示不阻断） |
| — | 200 | INFO | 无流量分录 → 回退科目段算法（降级，不报错） |
| — | 200 | INFO | 附表勾稽差异 ≥ 0.01 → 附表标注警示（不阻断） |

事务回滚：`CF_001/002` 抛 `BusinessException` → 全部回滚；重建端点失败整批回滚。

---

## 验收标准（BDD）

### 场景 1：借款与还款归筹资（REQ-095）
- **Given** 已过账凭证：借 1002 银行存款 10,000 / 贷 2001 短期借款 10,000
- **When** 生成现金流量表
- **Then** FINANCING_IN = 10,000
- **And** 负向断言：OPERATING_IN 不得同时计入该 10,000（不双计）

### 场景 2：资本注入与股利支付（REQ-095）
- **Given** 凭证：借 1002 / 贷 4001 实收资本（注入）；另一张：借 4104 利润分配 / 贷 1002（付股利）
- **When** 生成现金流量表
- **Then** 前者 FINANCING_IN、后者 FINANCING_OUT，金额分别等于票面

### 场景 3：预付设备款归投资（REQ-095）
- **Given** 凭证：借 1123 预付账款 / 贷 1002（设备预付）；及其结算凭证 1123→1601
- **When** 生成现金流量表
- **Then** 付款环节落 **INVESTING_OUT**（修正现状 OPERATING_OUT）
- **And** 负向断言：不得再计入经营活动

### 场景 4：三类净额勾稽现金变动（REQ-095）
- **Given** 含经营/投资/筹资混合发生额的期间
- **When** 生成现金流量表
- **Then** 经营净额 + 投资净额 + 筹资净额 == 期末现金 − 期初现金（容差 0.01，口径含 1012，与 P94/REQ-092 一致）

### 场景 5：人工指定优先与降级回退（REQ-095）
- **Given** 某凭证人工指定流量类型与规则不一致
- **When** 批量重建
- **Then** 人工指定保留（规则不覆盖）
- **And** 另一无流量分录的存量凭证 → 回退科目段算法仍能出表（负向断言：不因缺数据 500）

### 场景 6：间接法附表勾稽（REQ-096）
- **Given** 含折旧摊销、经营性往来变动的期间
- **When** 生成补充资料
- **Then** 净利润 + 各调节项合计 == 间接法经营活动净额，且与直接法经营净额差异 < 0.01
- **And** 负向断言：附表生成过程无任何表写入（只读）

### 场景 7：只读与事务红线（全需求）
- **Given** 执行报表查询（不含重建）
- **When** 完成
- **Then** 无任何写操作
- **And** 批量重建中途异常 → `t_voucher_cash_flow` 整批回滚、凭证与余额表零变更

**测试约束**：每场景一个 `@Test`；SQL/CASE 分支必须跑真实 DB 测试（Mock 测不出 SQL 分支——陷阱 4.3-7）。

---

## 竞品对标（铁律 #15）

| 竞品 | 现金流量表生成 | 筹资活动 | 间接法附表 | 慧财差异 |
|---|---|---|---|---|
| 用友 U8/NC | 凭证录入「现金流量项目」列（手工+规则双轨），流量明细表可追溯 | 完整（借款/还款/分红/资本） | 准则必备附表，一键生成 | 慧财：无凭证级绑定、筹资恒 0、无附表 |
| 金蝶云星空 | 现金流量项目库 + 凭证关联 + 自动分配 | 完整 | 间接法附表内置 | 慧财三者皆缺（V111 表休眠） |
| SAP FI | Cash flow item 挂凭证行 | 完整 | 间接法为报表格式内置 | 慧财仅科目段推断 |
| QuickBooks | 按交易类型自动归类 + 手工改 | 有 Loan/Capital 交易类型 | 无（境外准则差异） | 慧财缺交易类型维度 |

**准则依据**：《企业会计准则第 31 号——现金流量表》要求按经营/投资/筹资三类列报，并在附注中披露将净利润调节为经营活动现金流量的间接法资料——**间接法补充资料是列报必备项，非可选增强**。变更审计（铁律 #16）：原因=评估证实筹资分支死路径+1123 错归+V111 休眠；对标=上表；影响范围=`ReportDataMapper/ReportServiceImpl` 取数、V155+ 新迁移、凭证录入字段（草案）；已登记 REQ-2026-095/096（V1.15）。

---

## 版本历史

| 版本 | 日期 | 作者 | 变更 |
|---|---|---|---|
| V1.0 | 2026-09-24 | Sisyphus | 初稿：REQ-095/096 批次3，SQL 死路径与休眠表经 file:line/grep 核验；委派写手故障后按 AGENTS §4.5-16 直写 |
