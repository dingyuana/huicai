# P89 SPEC — 报表显示与交互（千分位/对比列/数字穿透/导出抬头）

> **版本**：V1.3 | **最后修改**：2026-09-24 | **作者**：Hermes
> **状态**：✅ 已实现（四项全部交付，测试通过，commit 8df6c7e / 0414991 / 7804583 / b906e3f / 569450f）
> **编号**：HUICAI-SPC-089 | 优先级：P1（体验级）
> **依据**：PRD-018 §2 P89 行 + §4.2 P89 显示与交互
> **关联需求**：R-141（需求矩阵）
> **test_ref**：ReportExportTest#exportBalanceSheet_writes_xlsx_bytes、#exportExcel_header_rows_match_spec、#exportAllFour_write_non_empty_content、VoucherPageSubjectIdRealDBTest#subjectId_只返回含该科目分录的凭证、#subjectId_排除已删除分录、#subjectId_为空时退化为原查询_不破坏既有列表行为、#subjectId_与期间联合过滤_不串期间

---

## 0. 背景与四项交付

P88 修完信任级缺陷后，报表"数据可信"但"不好用"。P89 补四个体验项，全部为展示层/交互层改动，**不改任何取数口径与账务逻辑**。

| 子项 | 内容 | commit |
|------|------|--------|
| P89-A | 千分位 + 负数标红 + 合计行加粗；消除 4 处内联 `toFixed(2)` 重复 | 8df6c7e |
| P89-B | 对比列：利润表"上期金额"、资产负债表"年初数" | 0414991 |
| P89-C | 数字穿透：科目余额表点击科目 → 凭证列表（按科目过滤）→ 凭证明细，2 跳 | 本笔 |
| P89-D | 导出 Excel 加抬头（标题/期间/制表人/制表日期）+ 前端 blob 接线 | 7804583 |

## 1. 契约（四段）

### 1.1 P89-C 数字穿透

**输入**
- `VoucherQueryDTO.subjectId`（`Long`，可选）：仅返回含该科目分录的凭证
- 前端路由 query：`/finance/voucher?period=&subjectId=&subjectName=`

**输出**
- 复用既有 `POST /vouchers/page`，响应结构 `IPage<VoucherVO>` 不变，仅结果集收窄
- 不变量：`subjectId` 非空时，返回的每一笔凭证都至少有一条未删除分录满足 `e.subject_id = subjectId`

**SQL（VoucherMapper.xml `selectVoucherPage`）**
```sql
<if test="subjectId != null">
    AND v.id IN (
        SELECT e.voucher_id FROM t_voucher_entry e
        WHERE e.subject_id = #{subjectId} AND e.deleted = 0
    )
</if>
```
`IN` 子查询天然去重：同一凭证含多条命中分录只返回一次。

**副作用**
- `selectVoucherPage` 从 10 参变 11 参（`subjectId` 插在 `sourceDocNo` 与 `scope` 之间）→ 所有调用点与 mock 签名必须同步，否则 `NoSuchMethodError`
- `ReportDataMapper.subjectBalance` 的 SELECT 增加 `s.id AS subject_id`：穿透需要科目 ID，原来只返 code/name 不够。保留原 `s.id` 不动（`balanceSheet`/`incomeStatement`/导出 三处共用该 mapper，靠 `s.id` 关联）

**穿透跳数（验收口径）**
1. 科目余额表点击科目编码 → 凭证列表（已按科目过滤）
2. 凭证列表点击凭证 → 凭证明细（既有功能）

共 2 跳，满足 PRD-018 §5 `P89-BD2`。

### 1.2 P89-A 金额显示

`frontend/src/utils/format.ts` 三个纯函数，4 张报表页统一引用：
- `formatAmount(v)`：千分位 + 2 位小数，`null/undefined` 归零
- `isNegative(v)`：负数判定（含 `-0` 防护）
- `amountClass(opts, value)`：返回 class 字符串，负数 → `amount-negative`（红），合计行 → `amount-bold`

全局样式在 `frontend/src/styles/report-amount.css`，不重复写各页 CSS。

### 1.3 P89-B 对比列

| 报表 | 对比列 | 取数方式 |
|------|--------|----------|
| 利润表 | 上期金额 | 取上一期间同期数据（`prevPeriod`） |
| 资产负债表 | 年初数 | 取本年期初期间余额（`yearStartPeriod`） |

降级策略：上期无数据或查询失败时**隐藏对比列**，不影响本期报表展示。期间工具函数在 `frontend/src/utils/period.ts`。

### 1.4 P89-D 导出抬头

`ReportServiceImpl.writeExcel` 结构（4 行抬头 + 数据）：
```
第 0 行：报表标题（跨列合并）
第 1 行：期间（左） + 制表人 + 制表日期（右）
第 2 行：表头
第 3 行起：数据
```
- 制表人取 `SecurityUtils.getCurrentUsername()`，取不到（无 SecurityContext）时兜底"未知"而非抛错
- 制表人行：`cols >= 4` 才合并到末列；现金流量表仅 3 列，单格合并会抛 `Merged region must contain 2 or more cells`

## 2. 关键决策与踩坑记录

| # | 问题 | 结论 |
|---|------|------|
| 1 | 穿透能否纯前端实现 | 不能。PRD §4.2 原写"既有端点，前端加路由参数"——但 `VoucherQueryDTO` 没有科目字段，凭证列表接口无法按科目过滤，必须改后端 |
| 2 | 改公共 Mapper 签名 | `selectVoucherPage` 10→11 参连带 3 个测试文件需同步：`VoucherServiceImplTest`（3 处 `when`+3 处 `verify` matcher 个数）、`DataIsolationAuditTest`（直接传实参 `null`，不是 matcher，需补第 10 个 null）。漏改则 `NoSuchMethodError` |
| 3 | 报表 mapper 返回 Map 的 key | `@Select` 返回 `Map<String,Object>` 时 key 是 SQL 别名（下划线），**不经过 Jackson 的 camelCase 转换**。SELECT 写 `s.id AS subject_id`，前端必须取 `row.subject_id`（不能取 `row.subjectId`） |
| 4 | 穿透与 sessionStorage 恢复的优先级 | 穿透优先。报表跳转是用户明确意图，被历史筛选覆盖会让穿透形同无效 |
| 5 | 导出文件能否直接测内容 | 可以且必须。只测"导出成功"抓不到抬头行错位——Excel 里"看起来正常"。用 `XSSFWorkbook` 读回逐格断言四行结构 |
| 6 | hutool 5.8.28 `merge` 签名 | 必须是 6 参 `merge(row1, row2, col1, col2, text, false)`；单参/2 参/4 参均不存在；没有 `getRowWriteData` 方法 |
| 7 | POI 单格合并 | `merge(2,2)` 抛 `Merged region must contain 2 or more cells`。列数少于 4 时退化不合并 |
| 8 | blob 导出走 axios 拦截器 | `request.ts` 响应拦截器需前置判断 `response.config.responseType === 'blob'` 直接 return；否则 blob 当 JSON 解析，`body.code` 为 undefined，误判为失败弹错 |
| 9 | 改多行 SELECT 的安全做法 | 本次写 patch 时把 `debit_total` 别名误删（old/new 写反）。该 mapper 被 3 个方法共用，加列前必须逐字核对别名完整性 |

## 3. 不做的事

- **P90 保留项**：预付/预收贷方余额重分类提示、辅助核算展开、异常高亮 —— 属 P90（P2），本轮不做
- **不改取数口径**：对比列只读既有接口，不新增后端聚合；穿透只收窄结果集，不改凭证数据
- **不做跨期穿透**：穿透限定单个期间（与报表期间一致），跨期下钻属明细账能力，不在本项范围

## 4. 验收

- P89-BD1：报表任一金额千分位右对齐，负数红色 ✅
- P89-BD2：报表科目行点击 → 2 跳内达该科目凭证明细 ✅
- PRD §2 导出验收：导出 Excel 含报表标题与期间 ✅（另含制表人/制表日期）

## 5. 测试覆盖

| 测试 | 用例数 | 说明 |
|------|--------|------|
| `VoucherPageSubjectIdRealDBTest` | 4 | 真实 DB（Testcontainers）验证穿透 SQL：命中过滤、多分录去重、已删除分录不参与匹配、null 退化为原查询、叠加期间不串期 |
| `ReportExportTest` | 5 | 含 xlsx 落盘 + POI 读回逐格断言四行抬头结构 |
| `ReportServiceImplTest` | 22 | 报表取数回归（`subjectBalance` mapper 共用，确认加列不破坏） |
| `VoucherServiceImplTest` + `VoucherControllerTest` | 3 + 8 | Mapper 签名变更后的参数透传回归 |

合计 42 个用例全绿。

> 注：`DataIsolationAuditTest` 标 `@SlowTest`，被 pom 默认 `excludedGroups=slow` 排除，本轮改动未在其运行时路径上验证；仅保证其编译通过（实参补参已修）。

## 6. 变更记录

| 版本 | 日期 | 内容 |
|------|------|------|
| V1.0 | 2026-09-24 | 初版。P89 四项交付，记录 9 项踩坑（含 Mapper 签名连带破坏、Map key 下划线、POI 单格合并、blob 拦截器） |
| V1.1 | 2026-09-24 | 补齐余额方向列 + 导出审核人（P89 转 ✅）；导出方向列中文统一；3 列表导出补逐格断言。ReportExportTest 增至 7 例 |
| V1.2 | 2026-09-24 | 新增浏览器 E2E `e2e/p89drilldown.spec.ts`（2 例）实测穿透链路，验证边界由"未实测"更新为已实测 |
| V1.3 | 2026-09-24 | 修 `e2e/report-financial.spec.ts` 资产负债表用例间歇失败（`subject-balance` 请求漏拦截打到真实后端）；`waitForTimeout(500)` 改 `waitForURL`；补 `latestClosedPeriod` mock 字段。`report-financial` + `p89drilldown` 现 6/6 全绿 |

## 7. 验证边界（诚实声明）

**已实测**：浏览器 E2E `e2e/p89drilldown.spec.ts`，2 例全过：
- **API 断言**：科目余额表行含 `subject_id`（下划线 key，未转 camelCase）；凭证按 `subjectId` 过滤**产生真缩减**（取样科目 1123 预付账款：全量 2 条 → 命中 1 条，过滤掉 1 条）。只断言"命中数 > 0"是弱验证——若该期间所有凭证都含该科目，2==2 也算过，证明不了过滤生效，故断言 `fTotal < aTotal`。
- **UI 断言**：报表页科目编码可点击，跳转 URL 带 `subjectId`/`period`/`subjectName`。

踩到的坑：期间探测不能只看"有凭证"，科目余额表只统计已结账凭证——实测 202410 有 16 张凭证却 0 发生额（仍是草稿态），必须探测"余额表有借/贷发生额"的期间。

**仍未验证**：

| 未验证项 | 说明 |
|----------|------|
| 导出件视觉渲染 | POI 读回校验的是单元格内容，未用 WPS/Excel 打开确认合并单元格与排版 |
| 全量回归 | 本轮跑的是 `ReportExportTest` + `ReportServiceImplTest` + `VoucherServiceImplTest` + `VoucherControllerTest` + `VoucherPageSubjectIdRealDBTest` + 2 个 E2E 文件，非全量 |
| `DataIsolationAuditTest` | 标 `@SlowTest` 被 `excludedGroups=slow` 排除，只过编译未过运行时（surefire 报告文件从未生成，已确认） |

**修正记录**：
- V1.1 曾写"浏览器端到端点击未在实测"，V1.2 已补做并更新。
- 初版曾写"42 个用例全绿"，表述不准确——实际跑的是上述测试类集合，未覆盖全量测试套件。
- V1.3 修正一处**错误诊断**：`report-financial.spec.ts` 资产负债表用例曾连续 3 次在不同行失败（`.page-title` not found / `about:blank` / 行 51 数据行），据此误判为"预存基础设施抖动、与本次改动无关"，并已写入 SPEC 当作已知问题接受。真实根因是组件 `fetchData` 并发请求 `subjectBalance(ys)`（路径 `/reports/subject-balance`），而测试 mock 只匹配 `balance-sheet`——`subject-balance` 不含该子串故**未被拦截**，请求漏到响应 ~3.6s 的真实后端，后端返回快慢直接导致失败位置漂移。补该 mock 后 6/6 全绿。教训：**失败位置不固定 ≠ 基础设施抖动**，先查是否有请求漏过 mock。

