# 业务单据通用引擎 PRD

> **编号**：HUICAI-PRD-001
> **版本**：V1.0 | **日期**：2026-08-19 | **作者**：Hermes
> **状态**：✅ 生效
> **类型**：产品需求文档（业务视角）
> **关联文档**：[DSN-应收应付管理](./DSN-应收应付管理.md)、[DSN-应收应付状态机设计](./DSN-应收应付状态机设计.md)、[ARC-核心链路映射](../architecture/ARC-核心链路映射.md)

---

## 0. 定位说明

本 PRD 是**慧财现有业务单据体系（`t_business_doc`）的补充改造 PRD**，不是重新设计通用引擎。当前项目已有一张统一表 + 8 种 docType 的"轻量通用"方案，本 PRD 只列**当前确实缺失且有 SME 价值的缺口**，明确不做 ERP 级元数据引擎。

**当前已覆盖**：收款单(RECEIPT)、付款单(PAYMENT)、费用报销(EXPENSE)、进项发票(INVOICE_IN)、销项发票(INVOICE_OUT)、其他应收(OTHER_RECEIVABLE)、其他应付(OTHER_PAYABLE)、工资单(SALARY)

**不做的事**（详见 §9）：元数据配置引擎、多模板自定义字段、多级审批流、销货单/进货单/退货单独立单据、多币种税额拆分、可视化打印设计器、智能浮动面板

---

## 1. 目标

1. 补齐业务单据生命周期中的 P0/P1 安全与体验缺口
2. 保持现有"以票定账"架构，不在业务单据层引入 ERP 级财务模型
3. 所有改造必须通过已有测试体系验证，不破坏现有 8 种 docType 的行为

---

## 2. 角色与权限

沿用当前 RBAC，不做新增角色。新增 2 个权限校验点：

| 校验点 | 说明 | 优先级 |
|-------|------|--------|
| 自审拦截 | 制单人不能审核自己提交的单据 | P0 |
| 弃审锁定 | 已生成凭证或已红冲的单据，弃审按钮隐藏并后端拦截 | P0 |

---

## 3. 单据类型枚举

沿用当前 8 种 docType，**不新增** SALE_OUT/SALE_OUT_RET/PUR_IN/PUR_IN_RET。退货走发票红冲。

---

## 4. 状态流转

沿用当前 `ArapStatus`（DRAFT/SUBMITTED/APPROVED/REVERSED/CANCELLED/VOUCHERED），**不新增状态**。

改造点：

1. **弃审锁定**：VOUCHERED 或 REVERSED 状态的单据禁止弃审（当前缺）
2. **自审拦截**：制单人 = 审核人时，审核操作后端拒绝（当前缺）
3. 保持现有"以票定账"：审核通过时由 `AutoGenerationService` 决定是否自动制证

---

## 5. 第一梯队实施项（P0/P1，高价值缺口）

### 5.1 自审拦截

**现状**：`approve()` 方法无制单人=审核人校验

**要求**：
- `BusinessDocServiceImpl.approve()` 中增加：`if (entity.getCreatedBy().equals(userId)) throw BusinessException("制单人不能审核自己提交的单据")`
- 前端 `BusinessDocList.vue` 审核按钮在 createdBy=当前用户时 `:disabled`

**BDD**：
- Given 单据 createdBy=1, When userId=1 调用 approve(), Then 抛出 BusinessException，And 状态保持 SUBMITTED
- Given 单据 createdBy=1, When userId=2 调用 approve(), Then 状态变为 APPROVED

### 5.2 红冲关联字段（父端标记）

**现状**：`reversedFrom` 已存在（红冲单→原单），但原单缺 `isReversed` 标记，无法从原单判断是否已被红冲

**要求**：
- Flyway migration V137 添加：`is_reversed BOOLEAN DEFAULT FALSE NOT NULL`
- `BusinessDocEntity` 添加 `isReversed` 字段
- `BusinessDocServiceImpl.reverse()` 生成红冲单时，同步将原单 `isReversed=true`
- 前端列表显示"已红冲"标识

**BDD**：
- Given 原单据 id=100 已生成凭证，When 生成红冲单，Then 红冲单.reversedFrom=100, And 原单.isReversed=true

### 5.3 审核状态锁定（VOUCHERED/REVERSED 不可操作）

**现状**：`approve()` 仅允许 SUBMITTED 状态，天然阻止 VOUCHERED 重复操作。**但 `reject()` 和 `reverse()` 也仅在 SUBMITTED/已生成凭证时允许，无漏洞。** 此条为确认项，无需代码改动。

### 5.4 抽屉式编辑（前端）

**现状**：编辑页是独立路由跳转

**要求**：
- `BusinessDocList.vue` 新增 `el-drawer`，编辑/新增在同一页面右侧滑出
- 编辑完成后 drawer 关闭，列表刷新
- 兼容现有 `BusinessDocEdit.vue`

**BDD**：
- Given 列表页，When 点击"新增"，Then 右侧抽屉展开，And 列表仍可见

---

## 6. 第二梯队（可晚做，1-2 天内可完成）

| # | 功能 | 工作量 | 理由 |
|---|------|--------|------|
| 6.1 | 尾差自动调整 | 0.5天 | 分录合计与单据金额 ±0.01 自动修正到最后一行 |
| 6.2 | 查询方案保存 | 1天 | 常用筛选条件存 localStorage/数据库 |
| 6.3 | 复制单据 | 0.5天 | 复制草稿→DRAFT，清空审核信息 |

---

## 7. 核心业务规则（确认当前实现）

| 规则 | 当前状态 | 说明 |
|------|---------|------|
| 编号规则（类型+年月+流水） | ✅ 已实现 | `VoucherNoService` |
| 红冲规则（金额取负） | ✅ 已实现 | 凭证层红冲 |
| 作废规则（仅草稿可作废） | ✅ 已实现 | `BusinessDocServiceImpl` |
| 结账锁 | ✅ 已实现 | `PeriodCloseService` |
| 辅助核算校验 | 🔴 缺 | P2，暂不实现 |
| 凭证生成 | ✅ 已实现 | `AutoGenerationService` |
| 上下游核销 | ✅ 已实现 | `ReconciliationService` |
| 乐观锁 version | ✅ 已实现 | Entity 已有 version 字段 |

---

## 8. 后端接口清单

沿用现有接口，新增 1 个：

| 端点 | 方法 | 说明 | 优先级 |
|------|------|------|--------|
| `/api/sme/business-docs/{id}/reverse` | POST | 生成红冲单（复制原单，金额取负，填 reverse_from_id） | P1 |
| `/api/sme/business-docs/{id}/copy` | POST | 复制单据为草稿 | P2 |

---

## 9. 明确不做（砍掉的 40%）

| 需求 | 砍掉理由 |
|------|---------|
| 元数据配置引擎 + configVersion | SME 不需要 Salesforce 级自定义表单，现有硬编码+共享 Service 已覆盖 |
| 多模板 + 公式字段 + 自定义字段 | 同上，成本远超收益 |
| 多级审批流（主管+财务+总经理） | 慧财审核是单步（SUBMITTED→APPROVED），不搞 OA 级审批 |
| 智能浮动面板 + 拖拽录入 | UX 噱头，SME 用户不需要 |
| 可视化打印设计器 | HTML 模板 + 浏览器打印够用 |
| SALE_OUT/SALE_OUT_RET/PUR_IN/PUR_IN_RET 独立单据 | 当前"发票即业务单据"已覆盖，重复建设 |
| 多币种 + 税额拆分到业务单据层 | 税额在发票层处理，业务单据存 amount 即可 |
| 批量操作（批量审核/弃审/作废） | SME 单据量不大，价值不高 |
| 查询方案保存 | P2，不急 |
| 弃审占锁（已被审核人打开则撤回失败） | SME 无抢占场景，加锁反而降低体验 |
| 移动审批 | Agency 分支待建，SME 不涉 |
| AI 智能审核 | AI 横切层骨架已有，等 AI Agent 就绪后再做 |

---

## 10. 验收标准

- [ ] `mvn test` 全量通过，0 fail
- [ ] 弃审锁定：VOUCHERED/REVERSED 状态弃审被拒绝
- [ ] 自审拦截：制单人审核自己被拒绝
- [ ] 红冲关联：红冲单 `reverse_from_id` 正确，原单 `is_reversed=true`
- [ ] 抽屉式编辑：列表页内打开编辑，保存后刷新列表
- [ ] 不影响现有 8 种 docType 的增删改查

---

> **文档结束**