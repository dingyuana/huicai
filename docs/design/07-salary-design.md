# 07-工资薪酬管理设计

> **编号**：HUICAI-DES-008
> **版本**：V1.0 | **修改日期**：2026-07-07 | **修改人**：Hermes | **修改内容**：初始创建（待建蓝图）
> 状态：❌ 待建 — 传统8模块中唯一未实现的模块
> 代码包：无（待创建 `com.huicai.module.salary`）
> 设计文档：[主文档](../DESIGN.md)

---

## 1. 模块定位

传统定位：人力资源与财务的交叉模块。设定薪资公式，计算基本工资、绩效、社保公积金及个税代扣代缴，月末生成工资汇总表，将人工成本按部门分摊生成凭证传入总账。

**当前状态：零代码实现。**

## 2. 传统功能清单（待实现）

| 功能 | 传统实现 | 优先级 |
|------|---------|--------|
| 工资项目维护 | 基本工资/绩效/补贴/扣款 | P0 |
| 薪资结构 | 固定工资+浮动工资+津贴 | P0 |
| 个税计算 | 累计预扣法 | P0 |
| 社保公积金 | 按基数+比例计算 | P1 |
| 工资表管理 | 月度工资表+历史查询 | P0 |
| 成本分摊 | 按部门/项目分摊生成工资凭证 | P1 |
| 工资条 | 员工查询 | P2 |
| 与总账对接 | 自动生成工资凭证 | P1 |

## 3. 建议数据模型（蓝图）

| 表名 | 说明 | 关键字段 |
|------|------|---------|
| t_salary_item | 工资项目 | code, name, calculation_type(fixed/formula), amount |
| t_salary_structure | 薪资结构 | employee_id, salary_item_id, amount, effective_date |
| t_salary_sheet | 工资表 | period, employee_id, total_amount, status |
| t_salary_sheet_entry | 工资表明细 | sheet_id, salary_item_id, amount |
| t_social_insurance | 社保公积金 | employee_id, base, pension_rate, medical_rate, unemployment_rate, housing_rate |

## 4. 建议状态机

```
DRAFT → SUBMITTED → APPROVED → VOUCHERED → CLOSED
```

## 5. 建议 API 端点

| 端点 | 方法 | 说明 |
|------|------|------|
| /api/v1/salary/items/** | CRUD | 工资项目 |
| /api/v1/salary/structures/** | CRUD | 薪资结构 |
| /api/v1/salary/sheets/page | GET | 工资表分页 |
| /api/v1/salary/sheets/calculate | POST | 计算工资 |
| /api/v1/salary/sheets/{id}/approve | POST | 审批 |
| /api/v1/salary/sheets/{id}/generate-voucher | POST | 生成凭证 |

## 6. AI 叠加场景

| 场景 | 说明 | 优先级 |
|------|------|--------|
| 薪资异常检测 | 与历史对比：异常涨薪/漏发 | 🟡 P3 |
| 社保预测 | 下月社保基数预测 | 🟢 远期 |

## 7. 对传统覆盖度

| 维度 | 状态 | 备注 |
|------|------|------|
| 后端 | ❌ 零实现 | 需从零开始 |
| 前端 | ❌ 零实现 | 需配套 |
| 个税算法 | ❌ 需实现 | 累计预扣法 |
| 社保 | ❌ 需实现 | 各地比例不同 |

> **文档结束。该模块待 P3 或独立阶段开发。**