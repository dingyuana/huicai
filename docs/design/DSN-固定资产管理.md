# 04-固定资产管理设计

> **关联PRD**：../prd/固定资产-PRD-V1.0.md
> **关联SPEC**：S-23-固定资产全生命周期管理.md
> **编号**：HUICAI-DES-005
> **版本**：V1.1 | **修改日期**：2026-09-17 | **修改人**：Hermes | **修改内容**：新增 §8 折旧与资产统计报表设计（对齐竞品多维度折旧报表，填补 G-2 缺口）
> 代码包：`com.huicai.module.asset`
> 设计文档：[项目说明](../CORE-项目说明.md) | [技术方案](../CORE-技术方案.md) | [需求分析](../CORE-需求分析.md)

---

## 1. 模块定位

传统定位：实物资产台账与折旧计算器。核心功能是内置折旧算法（直线法/双倍余额递减法），按月批量计提折旧。

**对比传统：**
- 传统：简单状态（在用/停用/报废），当前：**5态状态机**（DRAFT→IN_USE↔STOPPED/IDLE→DISPOSED + SCRAPPED）
- 传统：折旧公式硬编码，当前：支持多方法 + 自定义算法
- 当前新增：资产盘点（盘点单→差异处理→凭证）

> **⚠️ 代码-设计差异说明：** 设计稿原计划 4 态（IN_USE→IDLE→DISPOSED→SCRAPPED），
> 代码实际实现了 5 态状态机，包含 DRAFT 作为初始态。IDLE 已添加为 STOPPED 的别名常量，
> SCRAPPED 已添加但尚未接入完整处置流程。

## 2. 核心组件

| 组件 | 说明 |
|------|------|
| AssetCardService | 资产卡片CRUD、折旧计算、待折旧查询 |
| AssetCardStateMachineService | 资产状态机（4态） |
| AssetCategoryService | 资产类别管理（含默认折旧参数） |
| AssetDepreciationService | 折旧计提（批量+单张） |
| AssetDisposalService | 资产处置（报废/出售/捐赠） |
| AssetInventoryService | 资产盘点（盘点单→差异） |

## 3. 数据模型

| 表名 | 说明 | 关键字段 |
|------|------|---------|
| t_asset_card | 资产卡片 | asset_code, asset_name, category_id, original_value, residual_value, useful_life, depreciation_method, status, accumulated_depreciation, net_value |
| t_asset_category | 资产类别 | code, name, depreciation_method, useful_life, residual_rate |
| t_asset_depreciation | 折旧明细 | card_id, period, amount, cumulative |
| t_asset_disposal | 资产处置 | card_id, disposal_type, disposal_date, disposal_amount |
| t_asset_inventory | 资产盘点 | period, status |
| t_asset_inventory_entry | 盘点明细 | inventory_id, card_id, book_qty, actual_qty, difference |
| t_asset_change | 资产变动 | card_id, change_type, old_value, new_value |

## 4. 状态机

```
DRAFT ──启用──→ IN_USE ──停用──→ STOPPED (兼容 IDLE) ──处置──→ DISPOSED
  ↕                ↕
  edit          restart(←STOPPED)
                    └──处置也可从 STOPPED/IDLE 直接发起
```

> **说明：** 代码中 STOPPED 是 DB 留存值，新代码推荐使用 {@code IDLE} 常量。
> SCRAPPED 常量已声明但未进入当前状态机流转，为预留状态。

## 5. API 端点

| 端点 | 方法 | 说明 |
|------|------|------|
| /api/v1/asset/cards/** | CRUD | 资产卡片 |
| /api/v1/asset/categories/** | CRUD | 资产类别 |
| /api/v1/asset/cards/{id}/depreciate | POST | 计提折旧 |
| /api/v1/asset/disposals/** | CRUD | 资产处置 |
| /api/v1/asset/inventories/** | CRUD | 资产盘点 |

## 6. AI 叠加场景

**无。** 折旧计算是纯确定性数学（公式固定），AI 不介入。

## 7. 成熟度与待办

| 维度 | 状态 | 备注 |
|------|------|------|
| 后端 | ✅ 完整 | 含 4 个 Controller + 7 个 Service |
| 前端 | ✅ 完整 | 卡片/分类/处置/盘点页面 |
| 测试 | ⚠️ 刚补齐 | AssetCardMapperTest（8场景），Mock 测试存在 |
| 对传统覆盖 | ✅ | 直线法+双倍余额递减法均已实现 |

---

## 8. 折旧与资产统计报表（V1.1 新增，G-2）

**定位**：管理型聚合报表。竞品基线（金蝶"多维度折旧报表统计"）能力：按类别/部门看资产家底与折旧进展。
现状只有单卡片折旧查询（`/{id}/depreciation`），缺"整本资产账"的汇总视图。

### 8.1 报表口径

**报表 A：资产分类汇总**（时点快照）

| 项 | 定义 |
|----|------|
| 数据源 | t_asset_card + t_asset_category，`status ∈ (IN_USE, STOPPED)`（未处置资产） |
| 维度 | 按资产类别（二级展开：类别小计 + 总计行） |
| 指标 | 资产数量、原值合计、累计折旧合计、净值合计、本期应提折旧、净值率 |
| 本期应提 | 按各类别默认折旧方法/年限/残值率公式计算（与计提服务同一算法源，不重复实现） |

**报表 B：折旧计提明细汇总**（期间区间）

| 项 | 定义 |
|----|------|
| 数据源 | t_asset_depreciation（card_id, period, amount, cumulative） |
| 维度 | 期间区间内，按 **部门 × 资产类别** 交叉汇总（支持切换单维度） |
| 指标 | 计提金额合计、涉及资产数、期初累计折旧、期末累计折旧（含恒等式校验：期初+本期=期末） |

### 8.2 API 端点

| 端点 | 方法 | 说明 | SPEC |
|------|------|------|------|
| /api/sme/asset/v1/reports/category-summary | GET | 资产分类汇总（参数：period） | P77 |
| /api/sme/asset/v1/reports/category-summary/export | GET | 导出 Excel | P77 |
| /api/sme/asset/v1/reports/depreciation-summary | GET | 折旧计提汇总（参数：period_from, period_to, group_by=DEPT\|CATEGORY） | P77 |
| /api/sme/asset/v1/reports/depreciation-summary/export | GET | 导出 Excel | P77 |

**响应结构（category-summary 示意）：**

```json
{
  "period": "202609",
  "rows": [
    {"categoryId": 1, "categoryName": "电子设备", "qty": 34, "originalValue": 260000.00,
     "accumulatedDepreciation": 130000.00, "netValue": 130000.00,
     "currentDepreciation": 5400.00, "netRatio": 0.50}
  ],
  "total": {"qty": 120, "originalValue": 1500000.00, "netValue": 820000.00}
}
```

### 8.3 异常与边界

| 场景 | 处理 |
|------|------|
| 未计提过折旧的资产 | 报表 B 中不出现；报表 A 的"本期应提"按公式计算，与已计提无关 |
| 处置资产 | 排除（status=DISPOSED/SCRAPPED 不进入两表） |
| 期间无折旧记录 | 返回空 rows + total 全 0，不报错 |
| 数据权限 | EnterpriseDataPermissionInterceptor 注入 enterprise_id |

### 8.4 铁律约束

折旧金额计算是确定性公式，报表只读、不触发任何计提动作（计提仍走 `depreciate/{period}` 人工入口）。

## 9. 成熟度与待办（更新）

| 维度 | 状态 | 备注 |
|------|------|------|
| 折旧/资产统计报表 | ❌ 待开发 | §8 已设计，SPEC P77 待建 |

> **文档结束**