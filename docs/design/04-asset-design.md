# 04-固定资产管理设计

> **编号**：HUICAI-DES-005
> **版本**：V1.0 | **修改日期**：2026-07-07 | **修改人**：Hermes | **修改内容**：初始创建
> 代码包：`com.huicai.module.asset`
> 设计文档：[主文档](../DESIGN.md)

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

> **文档结束**