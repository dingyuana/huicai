# 03-现金与资金管理设计

> **关联PRD**：../prd/资金管理-PRD-V1.0.md
> **编号**：HUICAI-DES-004
> **版本**：V1.0 | **修改日期**：2026-07-07 | **修改人**：Hermes | **修改内容**：初始创建
> 代码包：`com.huicai.module.finance`
> 设计文档：[项目说明](../CORE-项目说明.md) | [技术方案](../CORE-技术方案.md) | [需求分析](../CORE-需求分析.md)

---

## 1. 模块定位

传统定位：出纳工作台，手工录入银行流水，Excel导入银行对账单进行对账。

**超越传统之处：**
- 自动导入：Excel/CSV 智能解析，自动识别列名
- AI 分类引擎：8类自动路由（A/B/C三档），规则+兜底双保险
- 智能对账：pgvector 语义匹配替代手工勾对
- 自动生单：A类自动分类，B类生成收款单/付款单

## 2. 核心组件

| 组件 | 说明 |
|------|------|
| BankStatementService | 银行流水管理（导入/预览/确认/分类） |
| BankReconciliationService | 银行对账（智能匹配+余额调节表） |
| BankJournalService | 银行日记账 |
| CashJournalService | 现金日记账 |
| ClassificationRuleService | 流水分类规则（规则引擎） |
| AutoGenerationService | 自动生单（A/B/C三类路由） |
| FallbackHeuristicService | 兜底启发式分类 |
| ColumnMappingResolver | Excel 列名映射（别名+评分） |

## 3. 流水分类体系（8类）

| 分类 | 路由 | 制证行为 |
|------|------|---------|
| business_receipt 业务收款 | B | 生成收款单 |
| business_payment 业务付款 | B | 生成付款单 |
| internal_transfer 内部转账 | B | 生成转账单 |
| tax_withholding 税费扣缴 | A | 自动分类 |
| salary_social 薪酬与社保 | B | 生成付款单 |
| bank_interest_fee 银行利息与手续费 | A | 按方向区分借贷 |
| financing_invest 筹资与投资 | C | 待人工 |
| other_unknown 其它/待认领 | C | 待人工 |

## 4. 数据模型

| 表名 | 说明 | 关键字段 |
|------|------|---------|
| t_bank_statement | 银行流水 | tx_date, amount, direction, counterparty, summary, classification, review_status |
| t_bank_account | 银行账户 | account_no, bank_name, balance |
| t_bank_journal | 银行日记账 | account_id, tx_date, amount, balance |
| t_cash_journal | 现金日记账 | tx_date, amount, balance |
| t_classification_rule | 分类规则 | keyword, classification, priority |

## 5. 状态机

```
PENDING ──classify──→ CLASSIFIED ──confirm──→ CONFIRMED ──generate──→ voucher_generated
                               ↕                            ↕
                         reclassify(→RECLASSIFIED)    create_payment(→payment_created)
```

## 6. API 端点

| 端点 | 方法 | 说明 |
|------|------|------|
| /api/v1/bank-statements/preview-excel | POST | 预览 |
| /api/v1/bank-statements/import-excel | POST | 导入 |
| /api/v1/bank-statements/page | GET | 分页 |
| /api/v1/bank-statements/{id}/classify | POST | 分类 |
| /api/v1/bank-statements/batch-review | POST | 批量确认 |
| /api/v1/bank-reconciliation/** | POST | 银行对账 |
| /api/v1/classification-rules/** | CRUD | 分类规则 |
| /api/v1/cash-journals/** | CRUD | 现金日记账 |
| /api/v1/bank-journals/** | CRUD | 银行日记账 |

## 7. AI 叠加场景

| 场景 | 说明 | 优先级 |
|------|------|--------|
| 语义分类兜底 | 规则引擎无法匹配时 AI 补充 | 🟡 P2 |
| 智能对账 | pgvector 摘要相似度匹配 | ✅ 已实现 |

## 8. 成熟度与待办

| 维度 | 状态 | 备注 |
|------|------|------|
| 后端 | ✅ 完整 | 含分类引擎+自动生单 |
| 前端 | ✅ 完整 | BankStatementView |
| 测试 | ✅ 良好 | BankStatementServiceTest |
| 对传统超越 | ✅ 自动导入+分类+智能对账 | 远超传统出纳模块 |

> **文档结束**