# 凭证管理 PRD

> **编号**：HUICAI-PRD-002
> **版本**：V1.0 | **日期**：2026-08-19
> **关联总 PRD：`(../CORE-需求分析.md)` 
> **关联设计**：DSN-总账管理.md / SPEC P22-voucher-state-machine.md
> **关联SPEC**：P22-voucher-state-machine.md、P37-voucher-type-rules.md、S-17-期末自动化结转.md
> **对应包**：com.huicai.base.voucher

---

## 1. 模块定位

财务系统核心凭证引擎，管理会计凭证的录入、审核、过账、红冲、编号溯源全流程。

**做什么**：凭证 CRUD、6态状态机、红冲生成、Excel 导出、编号溯源查询。

**不做什么**：
- 不做科目余额计算（属于总账结账模块，R-004）
- 不做期末结账/反结账（属于总账结账模块，R-005）
- 不做三大报表生成（属于报表中心模块，R-008）
- 不做自动科目映射（属于 AI 横切层）

---

## 2. 功能清单

| 编号 | 功能点 | 优先级 | 状态 | 验收标准 |
|------|--------|--------|------|---------|
| V-001 | 凭证录入（含分录） | P0 | ✅ 已完成 | 借贷平衡方可保存；分录≥2行；subjectId 必填 |
| V-002 | 凭证查询/分页/筛选 | P0 | ✅ 已完成 | 按期间/状态/凭证号筛选；含摘要模糊匹配 |
| V-003 | 凭证编辑 | P0 | ✅ 已完成 | 仅 DRAFT/SUBMITTED 可编辑；AUDITED+ 不可改 |
| V-004 | 提交（DRAFT→SUBMITTED） | P0 | ✅ 已完成 | 校验借贷平衡 + 期间未结账 |
| V-005 | 审核（SUBMITTED→AUDITED） | P0 | ✅ 已完成 | 校验来源状态；记录审核人+时间 |
| V-006 | 驳回（SUBMITTED→DRAFT） | P0 | ✅ 已完成 | 必须填写驳回原因；原因记录到 rejectedReason |
| V-007 | 过账（AUDITED→POSTED） | P0 | ✅ 已完成 | 校验期初余额已录入 + 更新科目余额 |
| V-008 | 反过账（POSTED→AUDITED） | P1 | ✅ 已完成 | 仅已过账可反过账；需权限 |
| V-009 | 结账（POSTED→CLOSED） | P0 | ✅ 已完成 | 期间关闭时所有凭证自动转为 CLOSED |
| V-010 | 红冲 | P0 | ✅ 已完成 | 已过账/已结账可红冲；生成红字凭证（DRAFT,source=REVERSAL）；原单不变更状态 |
| V-011 | 凭证模板管理 | P0 | ✅ 已完成 | CRUD + 模板行编辑；模板与凭证类型解耦 |
| V-012 | 编号生成（凭证号） | P0 | ✅ 已完成 | Redis INCR 生成；按期间+凭证类型独立序列；格式 `JZ-yyyyMM-0001` |
| V-013 | 编号全链路溯源 | P0 | ✅ 已完成 | 凭证→发票/业务单/银行流水双向查询 |
| V-014 | 凭证 Excel 导出 | P0 | ✅ 已完成 | 含凭证号/日期/摘要/科目/借贷金额/附件 |
| V-015 | 批量提交/审核/过账 | P1 | ✅ 已完成 | 逐项校验 + 全部通过方可执行；失败中断全部回滚 |

---

## 3. 状态流转

### 3.1 状态机图

```
         submit              audit              post             close
DRAFT ─────────→ SUBMITTED ─────────→ AUDITED ─────────→ POSTED ────────→ CLOSED
  ↑                 ↑                                    ↑                ↑
  |                 | reject(→DRAFT)                     | reverse(→     |
  |                 └── reject_reason                     |  红字凭证)     |
  └── unpost ←────────────────────────────────────────────┘
       (POSTED → AUDITED)
```

### 3.2 合法转换

| 转换 | 前置条件 | 副作用 |
|------|---------|--------|
| DRAFT→SUBMITTED | 借贷平衡、期间未结账 | — |
| SUBMITTED→AUDITED | — | 设置 auditedBy/auditedAt |
| SUBMITTED→DRAFT | reason 非空 | 设置 rejectedReason |
| AUDITED→POSTED | 期初余额已录入 | 更新科目余额 |
| POSTED→AUDITED (unpost) | — | — |
| POSTED→CLOSED | 期间已关闭 | — |
| POSTED/CLOSED→红冲 | — | 生成红字凭证（source=REVERSAL, DRAFT） |

### 3.3 非法转换（必须阻断）

| 场景 | 阻断方式 |
|------|---------|
| DRAFT 直接过账 | BusinessException |
| SUBMITTED 直接过账 | BusinessException |
| CLOSED 修改 | BusinessException |
| 重复提交（已 SUBMITTED 再 submit） | BusinessException |
| 红字凭证被冲销 | BusinessException（isReversed 检查） |
| 已审核凭证修改金额 | BusinessException |

### 3.4 自审拦截（V136 新增）

审核人与制单人相同时，抛出 `BusinessException`，拒绝审核。

**铁律**：已生成凭证（POSTED+）的审核不可弃审。

---

## 4. 核心业务规则

### 4.1 凭证号规则

| 凭证类型 | 前缀 | 格式 |
|---------|------|------|
| 记账凭证 | JZ | `JZ-yyyyMM-0001` |
| 收款凭证 | SK | `SK-yyyyMM-0001` |
| 付款凭证 | FK | `FK-yyyyMM-0001` |

- 序列：Redis INCR，前缀+期间+递增号
- 唯一约束：`(voucher_type_id, period, voucher_no) UNIQUE`

### 4.2 借贷平衡规则

- 保存时：`SUM(debit) == SUM(credit)`，否则拒存
- 金额精度：`BigDecimal`，数据库 `NUMERIC(18,2)`
- 借方合计、贷方合计由系统自动汇总，不手动输入

### 4.3 期间校验

- 所有状态转换必须校验期间未关闭（`status != 'closed'`）
- 期末结账时所有 POSTED 凭证自动转为 CLOSED

### 4.4 凭证类型与模板解耦

- 凭证模板（`t_voucher_template`）与凭证类型（`t_voucher_type`）无直接关联
- 自动制证场景由模板匹配引擎按 `source + businessType + direction` 匹配
- 手动录入不受模板约束

### 4.5 红冲规则

| 规则 | 说明 |
|------|------|
| 红字凭证来源标记 | `source = 'REVERSAL'` |
| 红字凭证状态 | `DRAFT`（需人工审核） |
| 红字凭证分录 | 借贷方向与原凭证互换 |
| 关联字段 | `reversedFrom`（红字→原单） |
| 原单标记 | 原单 `isReversed=true`（V137 新增） |

### 4.6 编号溯源

支持 6 种实体双向追溯：

```
凭证 ↔ 发票 ↔ 业务单据 ↔ 银行流水 ↔ 核销单 ↔ 预付款
```

通过 `source_doc_id` + `source_doc_no` + `source_doc_type` 三字段关联。

---

## 5. 验收标准

| ID | BDD 场景 | 对应测试 |
|----|---------|---------|
| AT-01 | Given DRAFT凭证 When submit Then 状态=SUBMITTED | VoucherStateMachineServiceTest.submit_positive |
| AT-02 | Given SUBMITTED凭证 When audit Then 状态=AUDITED + auditedBy 非空 | audit_positive |
| AT-03 | Given SUBMITTED凭证 When audit 制单人=审核人 Then 抛异常 | audit_selfReview_throws |
| AT-04 | Given SUBMITTED凭证 When reject(空原因) Then 抛异常 | reject_emptyReason_throws |
| AT-05 | Given SUBMITTED凭证 When reject("金额有误") Then 状态=DRAFT + rejectedReason 写入 | reject_positive |
| AT-06 | Given AUDITED凭证 When post Then 状态=POSTED + 科目余额已更新 | post_positive |
| AT-07 | Given AUDITED凭证 When post(期初未录) Then 抛异常 | post_openingNotEntered_throws |
| AT-08 | Given DRAFT凭证 When post Then 抛异常 | post_illegalState_throws |
| AT-09 | Given POSTED凭证 When reverse Then 生成红字凭证(source=REVERSAL, DRAFT) + 原单isReversed=true | reverse_positive |
| AT-10 | Given POSTED凭证 When reverse(已红冲) Then 抛异常 | reverse_alreadyReversed_throws |
| AT-11 | Given POSTED凭证 When unpost Then 状态=AUDITED | unpost_positive |
| AT-12 | Given POSTED凭证 When close(期间已关) Then 状态=CLOSED | close_positive |
| AT-13 | Given CLOSED凭证 When 编辑金额 Then 抛异常 | close_immutable_throws |
| AT-14 | Given 10笔DRAFT凭证 When batchSubmit(1笔已提交) Then 全部回滚 | batchRollback |

---

## 6. 不做的事

| 不做 | 理由 |
|------|------|
| 凭证打印/套打 | 当前版本不做，V2.0 考虑 |
| 凭证图片/附件展示 | 附件走 MinIO 对象存储，不在凭证模块内 |
| 自动审核/自动过账 | 违反铁律（人是唯一审核主体） |
| 凭证合并/拆分 | 无业务场景支撑 |
| 多币种凭证 | 单币种 |
| 凭证号自定义规则 | 固定 `前缀-期间-序号` 格式 |
| 跨期凭证强制阻断 | 账期控制由期末结账模块处理 |

---

## 7. API 清单

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/v1/vouchers/page` | 分页查询 |
| GET | `/api/v1/vouchers/{id}` | 详情 |
| POST | `/api/v1/vouchers` | 创建 |
| PUT | `/api/v1/vouchers/{id}` | 编辑 |
| DELETE | `/api/v1/vouchers/{id}` | 删除（仅 DRAFT） |
| POST | `/api/v1/vouchers/{id}/submit` | 提交 |
| POST | `/api/v1/vouchers/{id}/audit` | 审核 |
| POST | `/api/v1/vouchers/{id}/reject` | 驳回 |
| POST | `/api/v1/vouchers/{id}/post` | 过账 |
| POST | `/api/v1/vouchers/{id}/unpost` | 反过账 |
| POST | `/api/v1/vouchers/{id}/close` | 结账 |
| POST | `/api/v1/vouchers/{id}/reverse` | 红冲 |
| GET | `/api/v1/vouchers/trace` | 编号溯源 |
| GET | `/api/v1/vouchers/export` | Excel 导出 |
| POST | `/api/v1/vouchers/batch-submit` | 批量提交 |
| POST | `/api/v1/vouchers/batch-audit` | 批量审核 |
| POST | `/api/v1/vouchers/batch-post` | 批量过账 |

---

> **文档结束。** 下篇：`总账结账-PRD-V1.0.md`（科目余额/期初/结账/反结账）