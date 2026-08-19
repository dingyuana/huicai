# 01-总账管理设计

> **关联PRD**：../prd/总账结账-PRD-V1.0.md, ../prd/凭证管理-PRD-V1.0.md
> **关联SPEC**：P22-voucher-state-machine.md, P37-voucher-type-rules.md, S-17-期末自动化结转.md, S-18-结账控制与反结账.md
> **编号**：HUICAI-DES-002
> **版本**：V1.0 | **修改日期**：2026-07-07 | **修改人**：Hermes | **修改内容**：初始创建
> 代码包：`com.huicai.module.finance`
> 设计文档：[项目说明](../CORE-项目说明.md) | [技术方案](../CORE-技术方案.md) | [需求分析](../CORE-需求分析.md)

---

## 1. 模块定位

传统定位：整个财务系统的"心脏"与数据中心。处理所有记账凭证，执行期末结账、试算平衡，所有子模块最终汇总至此。

**超越传统之处：**
- 6态状态机（DRAFT→SUBMITTED→APPROVED→POSTED→CLOSED→REVERSED）替代传统简单审核标记
- 自动红冲级联（前向+反向），传统需手工做红字凭证
- 全链路编号溯源（从凭证可追溯到原始发票），传统只有单向凭证号

## 2. 核心组件

| 组件 | 说明 |
|------|------|
| VoucherService | 凭证CRUD、分页、溯源查询 |
| VoucherStateMachineService | 凭证6态状态机（核心：审核/驳回/过账/红冲） |
| SubjectBalanceService | 科目余额实时更新 + 期间快照 |
| PeriodCloseService | 期末结账（顺序校验+合法性检查） |
| VoucherTemplateService | 凭证模板管理 |
| BeginningBalanceService | 期初余额管理 |
| NumberingTraceService | 全链路编号追溯（6种实体类型双向） |
| VoucherNoService | 凭证号生成（Redis INCR） |

## 3. 数据模型

| 表名 | 说明 | 关键字段 |
|------|------|---------|
| t_voucher | 凭证主表 | voucher_no, period, status, total_debit, total_credit, source_doc_id/type/no |
| t_voucher_entry | 凭证分录 | voucher_id, subject_id, debit, credit, summary, aux_dept_id/project_id/customer_id/vendor_id/employee_id |
| t_subject_balance | 科目余额 | subject_id, period, begin_debit/credit, occur_debit/credit, end_debit/credit |
| t_voucher_template | 凭证模板 | name, subject_id, entry_template(jsonb) |
| t_voucher_template_line | 模板行 | template_id, subject_id, direction(summary) |

**编号关联字段**：source_doc_id, source_doc_no, source_doc_type（指向原始业务单据/发票）。

## 4. 状态机

```
DRAFT ──submit──→ SUBMITTED ──audit──→ AUDITED ──post──→ POSTED ──close──→ CLOSED
  ↕                    ↕                     ↕                    ↕
  edit              reject(→DRAFT)       reverse(→REVERSED)   reverse(→REVERSED)
```

## 5. API 端点

| 端点 | 方法 | 说明 |
|------|------|------|
| /api/v1/vouchers/page | GET | 分页查询 |
| /api/v1/vouchers/{id} | GET | 详情 |
| /api/v1/vouchers | POST | 创建凭证 |
| /api/v1/vouchers/{id}/submit | POST | 提交 |
| /api/v1/vouchers/{id}/audit | POST | 审核 |
| /api/v1/vouchers/{id}/reject | POST | 驳回 |
| /api/v1/vouchers/{id}/post | POST | 过账 |
| /api/v1/vouchers/{id}/close | POST | 结账（POSTED → CLOSED） |
| /api/v1/vouchers/{id}/reverse | POST | 红冲 |
| /api/v1/vouchers/{id}/unpost | POST | 反过账 |
| /api/v1/vouchers/trace | GET | 编号追溯 |
| /api/v1/subject-balances/** | GET | 科目余额 |
| /api/v1/periods/close | POST | 期间结账 |

## 6. AI 叠加场景

| 场景 | 说明 | 优先级 |
|------|------|--------|
| 凭证模板推荐 | 基于摘要语义匹配最佳模板 | 🟡 P2 |
| 异常凭证检测 | 借贷不平衡/金额过大/摘要可疑 | ✅ 已实现（P2-2） |

## 7. Contract-First 开发规范（Contract-First 追溯）

本模块严格遵循 Contract-First（契约优先）开发流程，三层追溯链路如下：

```
PRD（14份子文档）→ DSN（本文档）→ SPEC（P22/P37/S-17等）→ 代码 → @Test
```

| 要素 | 本模块对应内容 |
|------|------------|
| 关联 PRD | `../prd/凭证管理-PRD-V1.0.md`, `../prd/总账结账-PRD-V1.0.md` |
| 关联 SPEC | `P22-voucher-state-machine.md`, `P37-voucher-type-rules.md`, `S-17-期末自动化结转.md`, `S-18-结账控制与反结账.md` |
| 代码包 | `com.huicai.base.voucher`, `com.huicai.base.subject`, `com.huicai.base.period` |
| 测试类 | `VoucherStateMachineServiceImplTest`, `VoucherMapperTest`, `PeriodCloseServiceImplTest` |

**SPEC 强制要求**（详见 `../specs/SPEC-CONTRACT-SCHEMA.md` v3.0）：
- 四段模板：输入契约/输出契约/状态流转/异常处理
- 副作用声明：每个状态转换必须标注对哪些表/缓存/队列产生写操作
- 异常码穷举：每个接口必须穷举所有可能抛出的业务异常码
- test_ref 绑定：SPEC 头部必须指向具体 @Test 类

**新模块开发铁律**：新建模块（如工资薪酬、Agency）必须按 Contract-First 要求编写 SPEC，旧模块暂不强制迁移，但新增功能必须遵守。

## 8. 成熟度与待办

| 维度 | 状态 | 备注 |
|------|------|------|
| 后端 | ✅ 完整 | 含批操作 |
| 前端 | ✅ 完整 | VoucherList/Detail/Edit |
| 测试 | ✅ 良好 | VoucherMapperTest + 600+ 整体测试 |
| 对传统超越 | ✅ 6态状态机、红冲级联、编号溯源 | |

> **文档结束**