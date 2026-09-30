# P107 SPEC — 审计发现的存量缺陷修复包（6 项 P0/P1）

> **版本**：V1.0（草案，待老丁审核） | **最后修改**：2026-09-29 | **作者**：opencode
> **编号**：HUICAI-SPC-P107 | 优先级：**P0** | 状态：📋 待审核
> **来源**：2026-09-29 代码功能审计（代理 C）发现的 6 项「读代码即可确认、修复路径明确」的存量缺陷
> **关联需求**：REQ-2026-133 | **前置**：无（可与 P102~P105 并行） | **test_ref**：`CustomerStatementRealDBTest`、`VoucherImmutabilityRealDBTest`、`BankReconciliationRealDBTest`、`AgentBatchServiceTest`
> **与 P102~P105 的分工**：P102~P105 是**架构级基座改造**（安全基座/审计/门禁/文档）；本 SPEC 是**点状缺陷修复**，混入会拖慢基座进度，故独立立项

---

## 0. 缺陷清单（全部已实测，附 file:line）

| # | 缺陷 | 位置 | 症状 | 优先级 |
|---|---|---|---|---|
| D1 | 对账单「发起差异」违反 CHECK 约束 | `CustomerStatementServiceImpl.java:140` `setStatus("DISPUTED")` | `chk_customer_statement_status` 允许集为 `('DRAFT','GENERATED','SENT','CONFIRMED')`，**无 `DISPUTED`** ⇒ 端点 100% 抛 SQL 错 | P0 |
| D2 | 凭证分录物理删除 | `VoucherEntryMapper.xml:138` `DELETE FROM t_voucher_entry WHERE voucher_id=#{voucherId}` | 违反铁律 #3 凭证不可变性 + #12 逻辑删除；调用方 `VoucherServiceImpl:231,253`、`ArapSettlementServiceImpl:558` | P0 |
| D3 | 代理端批量服务空壳却报成功 | `BatchAuditServiceImpl:40`、`BatchCloseServiceImpl:30`、`BatchImportServiceImpl:34` 均 `// TODO` + `item.setSuccess(true)` | 一个对象都没改，API 却返回 `success=N, failed=0` ⇒ 比抛异常更危险（代理会向客户确认已结账） | P0 |
| D4 | 银行对账确认/驳回空壳 | `BankReconciliationServiceImpl:411-424` | `confirmMatch`/`rejectMatch` 只 `return new ConfirmResult(...,"MATCHED")`，**不写 `t_bank_statement.match_status`、不记对账日志** ⇒ UI 显示已匹配，DB 恒 `UNMATCHED`，对账永不收敛 | P0 |
| D5 | 金额精度 `new BigDecimal(double)` | `BankStatementExcelImportService:140`（`new BigDecimal(cell.getNumericCellValue())`）、`TaxServiceImpl:878`（`new BigDecimal(((Number)v).doubleValue())`） | 二进制浮点直转，`12345678.9` 可产出 `12345678.899999999`，**直接落财务表**；违反铁律 #1 | P1 |
| D7 | —— | —— | —— | —— |
| D8 | 银行流水「待人工确认」态违反 CHECK | `BankReconciliationServiceImpl:332` `updateMatch(..., "PENDING_CONFIRM")` | `chk_stmt_match_status` 允许集为 `('UNMATCHED','MATCHED','MANUAL_MATCHED','IGNORED')`，**无 `PENDING_CONFIRM`** ⇒ 自动匹配落到 60-84 分档必抛 SQL 错；该态被 `summarize`/`unmatchedItems`/Controller/Service 注释全链路引用，属**设计意图而非笔误**（D4 修复时暴露） | P0 |

## 5.5 实施结果（2026-09-30，D1~D6 + D8 全部完成）

| 缺陷 | 处置 | 迁移 | 红→绿验证 | 定向回归 |
|---|---|---|---|---|
| D1 | CHECK 补 `DISPUTED`（DROP+ADD 幂等） | `V160__add_disputed_to_customer_statement_status.sql` | 撤 V160（src+target/classes 同时移除）→ `violates chk_customer_statement_status` 红；恢复 2/2 绿 | 2/2 |
| D2 | `deleteByVoucherId` 物理 DELETE → `UPDATE deleted=1`；`selectByVoucherId` 补 `e.deleted=0` **且 JOIN 父表 `v.deleted=0`**；另 4 个零调用方物理 DELETE（`VoucherMapper.deleteBySource/deleteAll`、`VoucherEntryMapper.deleteByVoucherSource/deleteAll`）一并转软删（铁律 #12） | 无（列已存在） | 回退 XML → 3/3 红（`expected 1 but was 0`，分录被物理抹除）；恢复 3/3 绿 | 83/83（含 `VoucherEntryMapperRealDBTest`、`ArapSettlementServiceImplTest` 走 `deleteByVoucherId` 路径） |
| D3 | 三个 Impl 抛 `BusinessException(501, "功能未实现")`，删 `setSuccess(true)` | 无 | 红灯时 "nothing was thrown" 实锤假成功 | 6/6 |
| D4 | `confirmMatch`/`rejectMatch` 落库 + 回写日记账 + 写 `t_bank_reconciliation_log`；人工确认落 `MANUAL_MATCHED`（与自动 `MATCHED` 区分）；存在性/状态门禁 | `V161__bank_reconciliation_log.sql` | `expected <MANUAL_MATCHED> but was <MATCHED>` + 驳回落 `MATCHED` 红；4/4 绿 | 真库 6/6 + Mock 21/21 |
| D5 | `TaxServiceImpl.toBigDecimalSafe` 按整型/浮点分治；Excel 两处改 `BigDecimal.valueOf(val).toPlainString()` | 无 | 3 处真缺陷（P107 只登记 2 处） | Excel 2/2 + Tax 52/52 |
| D6 | 口令改 `${ENV:默认值}`；**JWT 去默认**（缺变量启动失败）；compose 9 处 + 新增 `.env.example` | 无 | 无 `JWT_SECRET` 时 compose/后端双 fail-fast；注入后 `Started HuicaiApplication` | 治理测试 5/5 |
| D8 | CHECK 补 `PENDING_CONFIRM`；`rejectMatch` 放行该中间态（否则 auto-match 产物无法人工驳回，违反人审铁律 #1） | `V162__d8_allow_pending_confirm.sql` | 撤 V162 + 回退约束 → 2/6 红（`violates chk_stmt_match_status`）；恢复 6/6 绿 | 6/6 |

**D2 调查中的重大修正**：`BaseEntity` 第 43-44 行**已带 `@TableLogic private Integer deleted`**，Voucher/VoucherEntry 继承之 ⇒ MP 逻辑删除**本已生效**，此前"Entity 缺 deleted 字段导致物理删"的判断有误（只 grep 实体类体、漏看父类，属 AGENTS §4.2-16 同族）。故 B2 方案中「Entity 补字段」部分**不需要做**，且父凭证删除本就是软删、不存在 CASCADE 抹审计问题；真实缺口仅 `VoucherEntryMapper.xml` 三处。

**新增沉淀（AGENTS §4.2）**：
- **第 19 条 · 「允许集缺设计态」比「写错值」更隐蔽**：功能设计（Controller/Service 注释/summarize 统计）完整依赖 `PENDING_CONFIRM`，而 CHECK 从未允许它 ⇒ 只要分数落 60-84 就 100% 崩。**判据：凡代码写入某状态值，先 `pg_get_constraintdef` 确认它在允许集内；再确认它不是「设计意图里的中间态被 CHECK 漏掉」。**
- **第 20 条 · 判 Entity 字段前必须看父类**：`VoucherEntryMapperRealDBTest` 早就在调 `e.setDeleted(0)`（能编译 ⇒ 父类必有该字段），而我 grep 实体类体未命中就误判「字段缺失」。**判据：任何"Entity 没某字段"的结论，必须同时 grep 父类 `BaseEntity` 与既有测试的 setter 调用。**


## 1. 输入契约

| 输入 | 类型 | 约束 |
|---|---|---|
| `t_customer_statement.status` | 状态机 | 取值必须在 `chk_customer_statement_status` 允许集内（**写前查 `pg_get_constraintdef`，禁凭语感猜**） |
| 凭证/分录 | 财务实体 | 已审核/已过账凭证**只可红冲**，不可物理删除（D2 改为逻辑删除 `deleted=1` 或仅未过账可删） |
| 批量服务 | 端点 | 功能未实现**必须抛 `BusinessException`** 或下线端点，禁止返回「成功」 |
| 金额 | 解析 | 一律 `BigDecimal.valueOf(double)` 或读字符串，禁 `new BigDecimal(double)` |
| 口令 | 配置 | 一律环境变量注入，无明文默认值（禁改 `application-prod.yml`） |

## 2. 输出契约

| 输出 | 验收标准 |
|---|---|
| D1 | `dispute()` 端点返回 200/400 而非 SQL 错；若保留 `DISPUTED` 语义则 CHECK 补该值（V161） |
| D2 | 删除凭证分录后 `deleted=1` 而非物理消失；已过账凭证删除被拒（抛 `BusinessException`） |
| D3 | 三个批量服务：未实现即抛「功能未实现」或端点下线，**绝不返回 success=true** |
| D4 | `confirmMatch` 写 `match_status=MATCHED` + 记对账日志；`rejectMatch` 置 `UNMATCHED` + 记日志 |
| D5 | 导入与税额计算全程 `BigDecimal`，`12345678.9` 往返不丢精度（回归断言） |
| D6 | `grep -rn 'password: huicai123' application*.yml` = 0；JWT 无可用默认密钥 |

## 3. 状态流转

```
D1: SENT/CONFIRMED ──dispute()──▶ DISPUTED(需 CHECK 补值) ──▶ 差异记录落库
D2: 凭证 DRAFT ──删除──▶ 逻辑删除(deleted=1)；POSTED ──删除──▶ 拒绝(抛错, 走红冲)
D3: 批量端点 ──未实现──▶ 抛「功能未实现」/ 下线 (禁 return success)
D4: 流水 UNMATCHED ──confirmMatch──▶ MATCHED + 日志；──rejectMatch──▶ UNMATCHED + 日志
D5: 金额解析 ──double──▶ BigDecimal.valueOf (无精度损失)
D6: 口令 ──明文──▶ ${ENV_VAR} 注入 (无默认值或空串)
```

## 4. 异常处理

| 场景 | 处理 |
|---|---|
| D1 若不补 `DISPUTED` 到 CHECK | 改用合法值 `REJECTED`（需同步改语义与前端），二选一，**禁绕过约束** |
| D2 删已过账凭证 | 抛 `BusinessException` 引导走红冲（铁律 #3） |
| D3/D4 未实现 | 抛 `BusinessException(501, "功能未实现")`，**不得静默成功** |
| D5 解析失败 | 该行标「解析失败」并计数，**禁静默填 0**（当前行为） |
| D6 环境变量缺失 | 启动即失败（`${JWT_SECRET}` 无默认），不降级到弱密钥 |

## 5. BDD 行为契约（TDD 红→绿，每条先写失败测试）

| # | Given | When | Then | 验证层 |
|---|---|---|---|---|
| AT-107-1 | 对账单 `SENT` | `dispute()` | 不报 `chk_customer_statement_status` 违约；差异记录落库 | 真实 DB |
| AT-107-2 | 已过账凭证 | 调删除 | 抛 `BusinessException`（不物理删分录） | 真实 DB |
| AT-107-3 | DRAFT 凭证 | 调删除 | 分录逻辑删除（`deleted=1`），可查可恢复 | 真实 DB |
| AT-107-4 | 代理批量审核 | 调端点 | 抛「功能未实现」或端点 404，**`success` 不得为 true** | 单元 |
| AT-107-5 | 银行流水 `UNMATCHED` | `confirmMatch` | `match_status=MATCHED` 且新增对账日志 | 真实 DB |
| AT-107-6 | Excel 金额 `12345678.9` | 导入 | 落库 `12345678.90`，无 `899999999` 尾巴 | 真实 DB |
| AT-107-7 | 税额来源为 `Double` | 计算 | 精度不丢（`BigDecimal.valueOf` 路径） | 单元 |
| AT-107-8 | 应用配置 | 静态扫描 | 无 `password: huicai123` 明文；JWT 无可用默认密钥 | 脚本 |

## 6. 竞品对标（铁律 #15）

| 缺陷 | 用友/金蝶 | SAP | Xero | QuickBooks | 行业底线 | 本修法 |
|---|---|---|---|---|---|---|
| D2 凭证物理删除 | 禁止（红冲/冲销） | 禁止 | ✅ 不可改 | ✅ 不可改 | **不可变性是会计系统第一原则** | 改逻辑删除 + 已过账拒删 |
| D3/D4 静默成功 | 失败必报错 | 事务失败 | ✅ | ✅ | **宁可失败不可谎报** | 抛 501/下线端点 |
| D5 金额精度 | 定点数 | DECIMAL | ✅ | ✅ | 禁浮点 | `BigDecimal.valueOf` |
| D6 明文口令 | 环境/密钥库 | 密钥库 | ✅ | ✅ | 禁入库 | 环境变量 + 启动校验 |

**结论**：D2/D3 是**会计系统原则性缺陷**（不可变性、失败可见性），D5/D6 是安全基线，四家竞品均已做到，本项目须补平。

## 7. 风险与不在范围

- **D2 改逻辑删除**影响 3 个调用方（`VoucherServiceImpl:231,253`、`ArapSettlementServiceImpl:558`）→ 逐个核对「仅未过账可删」。
- **D1 若补 CHECK 值**需 V161 migration + 前端状态文案同步 → 二选一并记录。
- **D6 JWT 默认密钥**已入 git 历史 → 改配置后**该密钥须视为已泄露、建议轮换**（运营项，不在本 SPEC 代码范围）。
- **不在范围**：AI 功能、Entity 直入 DTO 隔离（归 P102-A3）、前端 Playwright 接 CI。

## 版本历史

| 版本 | 日期 | 变更人 | 变更内容 |
|---|---|---|---|
| V1.0 | 2026-09-29 | opencode | 初稿：承接 2026-09-29 代码审计，6 项 P0/P1 点状缺陷（D1 CHECK 违约 / D2 凭证物理删 / D3 批量空壳 / D4 对账空壳 / D5 金额精度 / D6 明文口令），与架构级 P102~P105 分离 |
| V1.1 | 2026-09-30 | opencode | 实施结果回写：新增 §5.5 实施结果表（D1~D6 + D8，含红→绿反证与定向回归数字）；**新增 D8**（`chk_stmt_match_status` 缺 `PENDING_CONFIRM` 设计态，D4 实施时暴露）；D2 结论修正（`BaseEntity` 已带 `@TableLogic`，MP 逻辑删除本已生效，缺口仅 XML 三处 + 4 个零调用方物理 DELETE）；D4 人工确认落 `MANUAL_MATCHED` 而非 `MATCHED`；D5 实测 3 处缺陷（P107 登记 2 处） |
