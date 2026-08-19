# 财务软件-业务单据管理模块产品需求文档（PRD）

> **编号**：HUICAI-PRD-000
> **版本**：V1.0 | **日期**：2026-08-19
> **关联总 PRD**：`../CORE-需求分析.md`
> **关联设计**：DSN-应收应付管理.md
> **关联SPEC**：P-BUSINESSDOC-LIST.md、P-SALARY.md、P-TRANSFER.md、P30-reconciliation-workbench-enhance.md
> **对应包**：com.huicai.sme.arap / com.huicai.base.business

---

## 1. 模块定位

管理业务单据（收款单/付款单/费用报销/进销项发票/转账单/工资单等）的全生命周期：录入→审核→自动生成凭证→过账→核销→红冲。

**做什么**：业务单据 CRUD、状态机、凭证自动生成、结算账户绑定、列表增强。

**不做什么**：
- 不做元数据驱动的动态表单
- 不做多级审批流
- 不做可视化凭证打印设计器
- 不做发票税额拆分（税额在发票层处理）
- 不做独立销货单/进货单类型

---

## 2. 功能清单

| 编号 | 功能点 | 优先级 | 状态 | 验收标准 |
|------|--------|--------|------|---------|
| DOC-01 | 业务单据 CRUD | P0 | ✅ | 支持 11 种 docType；含 header+entry 两表 |
| DOC-02 | 状态机（DRAFT→SUBMITTED→APPROVED→VOUCHERED） | P0 | ✅ | 不可逆状态流转；禁止非法跳转 |
| DOC-03 | 凭证自动生成 | P0 | ✅ | 审核后调用 AutoGenerationService；凭证状态=DRAFT |
| DOC-04 | 自审拦截 | P0 | ✅ | 制单人不能审核自己提交的单据 |
| DOC-05 | 红冲 | P0 | ✅ | 生成红冲单据；原单标记 isReversed=true |
| DOC-06 | 核销关联 | P1 | ✅ | 收款/付款单核销后更新核销状态 |
| DOC-07 | 结算账户绑定 | P1 | ✅ | 业务单据记录 settlementAccountId |
| DOC-08 | 列表增强（日期/金额筛选/核销徽章/源单号跳转） | P1 | ✅ | 见 P-BUSINESSDOC-LIST.md |
| DOC-09 | 转账单（TRANSFER） | P1 | ✅ | 借/贷科目从分录读取 |
| DOC-10 | 工资单（SALARY） | P1 | ✅ | SALARY docType 注册；凭证科目 2211/1002 |
| DOC-11 | 预收预付管理页 | P1 | ✅ | PrepaymentView.vue 前端页面 |
| DOC-12 | Excel 导出 | P2 | ⚠️ | 部分 docType 有导出 |

---

## 3. 状态流转

```
DRAFT → SUBMITTED → APPROVED → VOUCHERED
     ↕            ↕
   (编辑)      REJECTED(→DRAFT)

VOUCHERED → REVERSED（红冲，不可恢复）
```

| 状态 | 说明 | 可执行操作 |
|------|------|---------|
| DRAFT | 草稿，可编辑 | 编辑、提交、删除 |
| SUBMITTED | 已提交，待审核 | 审核、驳回 |
| APPROVED | 已审核，待过账 | 过账、红冲 |
| VOUCHERED | 已过账 | 红冲 |
| REVERSED | 已红冲，只读 | 无 |
| REJECTED | 已驳回，回退 DRAFT | 编辑、提交 |

---

## 4. API 清单

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/sme/arap/business-doc/page` | 列表分页 |
| GET | `/api/sme/arap/business-doc/{id}` | 详情 |
| POST | `/api/sme/arap/business-doc` | 创建 |
| PUT | `/api/sme/arap/business-doc/{id}` | 编辑 |
| POST | `/api/sme/arap/business-doc/{id}/submit` | 提交 |
| POST | `/api/sme/arap/business-doc/{id}/approve` | 审核 |
| POST | `/api/sme/arap/business-doc/{id}/reject` | 驳回 |
| POST | `/api/sme/arap/business-doc/{id}/post` | 过账 |
| POST | `/api/sme/arap/business-doc/{id}/reverse` | 红冲 |

---

## 5. 验收标准

| ID | BDD 场景 | 关联 SPEC |
|----|---------|----------|
| AT-01 | Given DRAFT 单据 When 制单人审核 Then 拒绝（自审拦截） | P-BUSINESSDOC-LIST.md |
| AT-02 | Given 审核通过 When 自动生成凭证 Then 凭证状态=DRAFT | P-BUSINESSDOC-LIST.md |
| AT-03 | Given APPROVED 单据 When 红冲 Then 原单 isReversed=true | P30-reconciliation-workbench-enhance.md |
| AT-04 | Given 转账单 When 提交 Then 借贷科目从分录读取 | P-TRANSFER.md |
| AT-05 | Given VOUCHERED 单据 When 尝试修改 Then 拒绝 | P-BUSINESSDOC-LIST.md |
| AT-06 | Given 列表 When 日期范围筛选 Then 返回符合范围的单据 | P-BUSINESSDOC-LIST.md |