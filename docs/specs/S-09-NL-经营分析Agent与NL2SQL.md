# S-09-NL - 经营分析Agent与NL2SQL

> **版本**：0.1（占位骨架，待开发）
> **日期**：2026-07-18
> **状态**：❌ 待开发
> **层级**：高阶管理与AI层
> **预估复杂度**：高
> **关联需求**：待分配 REQ 编号

---

## 概述

意图识别、Text-to-SQL、敏感脱敏、超时降级兜底。AI增强层。

**备注**：AI增强层，P3远期。需先完成 S-09 账簿查询后才有意义。NL2SQL 有注入风险，需严格脱敏。

---

## 1. 输入契约

- 用户自然语言提问
- 当前账套上下文
- 用户权限范围

**前置条件**：
- 待补充（开发时根据业务逻辑定义）

---

## 2. 输出契约

- SQL 查询（脱敏后）
- 查询结果
- 自然语言回答
- 图表数据

**失败响应**：参考全局错误码字典（SPEC-CONTRACT-SCHEMA.md §0.5）

---

## 3. 状态流转

- PENDING -> UNDERSTANDING -> SQL_GENERATING -> EXECUTING -> ANSWERING -> DONE
- 降级：超时 -> FALLBACK

**负向断言**（禁止的跳转）：
- 待补充（开发时根据状态机定义）

---

## 4. 异常处理

- 意图识别失败 -> 20410
- SQL 生成失败 -> 20411
- 查询超时 -> 20402
- 敏感数据脱敏拦截 -> 20412

**降级策略**：待补充

---

## 验收标准（BDD）

> ⚠️ 以下为初始场景草案，开发时需细化和补充。

### 场景 1：经营分析Agent与NL2SQL - 基本流程
- **Given** 待补充前置条件
- **When** 待补充触发动作
- **Then** 待补充期望结果
- **And** 待补充负向断言

### 场景 2：异常场景
- **Given** 待补充异常前置条件
- **When** 待补充触发动作
- **Then** 返回对应错误码
- **And** 不产生副作用

---

## 依赖关系

- 待补充（开发时根据依赖拓扑图定义）

---

```yaml
# === MACHINE-READABLE CONTRACT (PLACEHOLDER) ===
# 待开发时填充完整契约

contract_version: "1.0"
entity: TBD
module: TBD
table: TBD

states: []
transitions: []
constraints: []
acceptance_tests: []
out_of_scope: []
dependencies: []
```
