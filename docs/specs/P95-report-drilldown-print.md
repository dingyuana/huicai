# P95 SPEC — 财务报表全局下钻穿透与 A4 打印预览（P1 批次2）

> **版本**：V1.0（草案） | **最后修改**：2026-09-24 | **作者**：Sisyphus
> **状态**：✅ 已审核通过（批次2/4，老丁 2026-09-24；拍板：打印=全屏 dialog + window.print()、利润表下钻映射=前端常量表（实现前与科目表对证））
> **编号**：HUICAI-SPC-095 | 优先级：P1（交互与输出）
> **依据**：两轮四表深度技术评估（"报表数字是死的，无法下钻""没有打印，只有 Excel 导出"）
> **关联需求**：REQ-2026-093（全局下钻穿透）、REQ-2026-094（A4 打印预览）——已登记 REQUIREMENTS_REGISTRY.md §八 V1.15
> **关联SPEC**：P89-C（余额表→凭证 2 跳穿透）、P89-D（导出抬头）
> **test_ref（规划）**：前端组件/路由测试 `BalanceSheetView.spec#p95_rowClick_navigatesLedgerWithPeriodAndSubject`、`IncomeStatementView.spec#p95_revenueRowClick_mapsTo6001`、`PrintPreview` 分页与签章栏渲染 2 例；负向 `exportUnchanged` 1 例

---

## 0. 背景与现状差距（代码 trace，全部已核验）

### 0.1 下钻：全系统仅一处，且不到明细账

- 唯一穿透：`SubjectBalanceView.vue` L25（`el-link` 点击）+ L130-142 `drillToVouchers()` → 路由 `VoucherList`，query 携带 `period/subjectId/subjectName`（P89-C，**2 跳**：余额表→凭证列表→凭证明细）
- 缺口：
  1. **资产负债表/利润表/现金流量表三表完全不可点**（`BalanceSheetView.vue` 行仅展示，无 click handler）
  2. 穿透链不经过**明细账**：行业标准是 报表 → 明细账（分类簿）→ 凭证 3 跳，慧财只做到 1 跳直达凭证
- 明细账页已存在、可直接复用：`frontend/src/views/finance/ledger/LedgerView.vue`；路由 `frontend/src/router/routes/base.ts` L137-141（path `finance/ledger`，name `LedgerView`，meta.title `账簿查询`，**permission `ledger:list`**，keepAlive）；`sme-base.ts` ~L147 同款路由
- 行可下钻键：`BalanceSheetView.vue` 行含 `code/name`（资产/负债/权益数组）；`IncomeStatementView.vue` L89-95 行为硬编码 label（`一、营业收入` 等）→ 需建立 **label → 科目段映射表**（草案，实现前与科目表对证）

### 0.2 打印：全库零能力

- Grep 全前端：**无 `window.print`、无 `@media print`**（零命中）
- 现有输出仅后端 EasyExcel 导出（如 `exportSubjectBalance`，P89-D 已带抬头/制表人）
- 缺口：法定报表需要 A4 纸质留档（报表名/期间/金额单位/签章栏），Excel 导出不满足签章与分页要求

---

## 1. 输入契约

### REQ-093 下钻穿透
- 触发：报表科目级行（明细行）单击；小计/合计/勾稽行**不可点**（无 click、无 pointer 样式）
- 目标路由：`finance/ledger`（`LedgerView`）
- query 参数（草案）：`period`（当前报表期间）、`subjectId` 或 `subjectCode`（行级）、`direction`（可选，报表上下文）
- 权限：沿用 `ledger:list`；无权限时点击 → `ElMessage` 拦截提示，不跳转（不新增鉴权面、不绕过）
- 复用既有 P89-C 链路：科目余额表 → 凭证保持不变（回归保护）

### REQ-094 A4 打印预览
- 入口：报表工具栏「打印预览」按钮（新，草案）
- 输入：当前报表数据 + 抬头信息（企业抬头、报表名称、所属期间、金额单位「元」）
- 形态（草案，二选一实现时定）：独立打印路由或全屏 dialog + `window.print()`

## 2. 输出契约

- 下钻成功：进入 `LedgerView` 且期间/科目过滤已生效，账簿数据与报表同口径同期间
- 打印输出：
  - A4 纵向；行不跨页截断（`tr` 禁止 break-inside）；`thead` 每页重复（CSS `table-header-group`）
  - 底部签章栏三行：制表人 / 审核人 / 法定代表人（空签章位留白，不预填人名——人是唯一审核主体）
  - `@media print` 隐藏侧栏、导航、操作按钮
  - 打印数字与页面/导出三者完全一致（同一数据源）
- 负向：Excel 导出行为、P89-C 既有穿透、未选中期间的兜底，全部回归不变

## 3. 状态流转与副作用

- 两个特性均**只读**：无状态机、无业务表写操作
- 副作用声明：`DB_READ ONLY → t_subject/t_voucher_entry/t_voucher → LedgerView 查询`；打印为浏览器端渲染，**无服务端落盘**
- 负向断言：穿透不得借道修改任何查询条件以外的数据；打印不得触发导出接口二次计数

## 4. 异常处理

| 场景 | 处理 | 级别 |
|---|---|---|
| 无 `ledger:list` 权限点击下钻 | 拦截 + 提示"无明细账查看权限"，留在原页 | WARN |
| 行无 subjectId/映射不到科目 | 不可点（等同小计行） | INFO |
| 明细账该期间无数据 | LedgerView 空态（现状能力），非穿透错误 | INFO |
| 打印预览期间未选择 | 提示"请先选择期间"（同 `onExport` 兜底） | WARN |
| 打印样式渲染异常 | 降级为浏览器原生打印，不阻断 | ERROR（日志） |

事务：无写操作，不涉及铁律 #11。

---

## 验收标准（BDD）

### 场景 1：资产负债表行下钻明细账（REQ-093）
- **Given** 已登录且具备 `ledger:list` 权限，资产负债表已加载
- **When** 单击「银行存款」明细行
- **Then** 跳转 `finance/ledger`，query 含当前 period + 该行 subjectId/subjectCode
- **And** 负向断言：不跳转到凭证列表（新链路必须经过明细账）

### 场景 2：利润表行下钻映射科目（REQ-093）
- **Given** 利润表已加载
- **When** 单击「一、营业收入」行
- **Then** 明细账定位到映射科目（如 6001 段），期间=报表期间

### 场景 3：小计/合计行不可点（REQ-093）
- **Given** 资产负债表三分小计行与合计行
- **When** 单击这些行
- **Then** 无跳转、无 pointer/hover 反馈（与明细行视觉可区分）

### 场景 4：无权限拦截（REQ-093）
- **Given** 当前用户无 `ledger:list` 权限
- **When** 单击可下钻明细行
- **Then** 原地提示无权限，不发生路由跳转
- **And** 负向断言：不出现 403 白屏、不泄露账簿数据

### 场景 5：打印预览结构完整（REQ-094）
- **Given** 资产负债表已加载
- **When** 点击「打印预览」
- **Then** 渲染含 企业抬头 + 报表名称 + 所属期间 + 金额单位「元」的 A4 版式，底部含制表人/审核人/法定代表人签章栏

### 场景 6：A4 分页与表头重复（REQ-094）
- **Given** 行数超过一页的科目余额表
- **When** 执行打印（浏览器打印预览）
- **Then** 表头每页重复，明细行不被跨页截断
- **And** 负向断言：`@media print` 下侧栏/工具栏/按钮不可见

### 场景 7：三者数字一致 + 既有能力回归（全需求）
- **Given** 同一报表同一期间
- **When** 对比 页面显示 / 打印预览 / Excel 导出三处金额
- **Then** 三者完全一致
- **And** 负向断言：`exportSubjectBalance` 导出内容与现状 diff == 0；科目余额表 P89-C 穿透回归不变

**测试约束**：每场景一个 `@Test`/spec；打印场景用无头浏览器截图断言（`@media print` 模拟）；负向断言强制。

---

## 竞品对标（铁律 #15）

| 竞品 | 下钻穿透 | 打印 | 慧财差异 |
|---|---|---|---|
| 用友 U8/UFO | 报表单元格双击 → 明细账 → 凭证，3 跳标准链 | 打印设置含抬头/签章/分页 | 慧财仅余额表 1 跳直达凭证，无打印 |
| 金蝶云星空 | 报表下钻「明细账→凭证」全系贯通 | 打印模板含签章栏，A4 分页 | 慧财三表不可点、零打印能力 |
| QuickBooks | 报表行 drill into transactions | 内置 Print + 页眉页脚 | 慧财输出仅 Excel |
| SAP FI | FBL1N/FAGLL03 与报表互跳 | SAPscript 表单签章 | 慧财缺 3 跳链路 |

**结论**：下钻与打印是财务软件**基线能力**，本批为补齐而非创新。变更审计（铁律 #16）：原因=评估证实仅 1 处穿透且无打印；对标=上表；影响范围=`BalanceSheetView/IncomeStatementView/CashFlowView` 增加 click 与打印入口、新增打印样式/路由（草案）、无后端变更；已登记 REQ-2026-093/094（V1.15）。

---

## 版本历史

| 版本 | 日期 | 作者 | 变更 |
|---|---|---|---|
| V1.0 | 2026-09-24 | Sisyphus | 初稿：REQ-093/094 批次2，现状差距全部经 file:line 核验；委派写手故障后按 AGENTS §4.5-16 直写 |
| V1.1 | 2026-09-24 | Sisyphus | 批次2/4 老丁审核通过；两项拍板落定：打印=全屏 dialog + window.print()、利润表下钻映射=前端常量表（与科目表对证后实现） |
