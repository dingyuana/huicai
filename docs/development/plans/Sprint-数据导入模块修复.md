# 慧财财务 — 数据导入模块 Sprint 开发计划

> **编号**：HUICAI-DEV-003
> **版本**：V1.0 | **修改日期**：2026-07-07 | **修改人**：Hermes | **修改内容**：添加编号头部

## Sprint 目标

修复银行对账单导入 4 个 Bug + 完成销售发票导入全链路 + 完善测试覆盖。

**总估算**：3 天（3 Phases × 1 天）

---

## Sprint Backlog

### Task 1: 列名映射优化 (F1)

**状态**：⏳ Pending | **估算**：2h | **优先级**：P0

**修改文件**：
- `backend/.../ColumnMappingResolver.java`

**改动**：第二轮 contains 匹配改为按别名长度降序匹配，避免"付款人开户行行号"优先于"付款人名称"。

**验证**：导入 7月对账单.xlsx，验证对方名称列显示正确公司名。

### Task 2: 流水去重导入 (F2)

**状态**：⏳ Pending | **估算**：3h | **优先级**：P0

**修改文件**：
- `backend/.../mapper/BankStatementMapper.java` + `BankStatementMapper.xml`
- `backend/.../BankStatementExcelImportService.java`

**改动**：添加去重查询 SQL + 导入时检查重复行并标记。

**验证**：同一文件导入两次，第二次预览显示重复标记。

### Task 3: 单条流水删除 (F3)

**状态**：⏳ Pending | **估算**：2h | **优先级**：P0

**修改文件**：
- `backend/.../BankStatementService.java` + `BankStatementServiceImpl.java`
- `backend/.../BankStatementController.java`
- `frontend/.../BankStatementView.vue`

**验证**：前端点删除 → 确认弹窗 → 记录消失。

### Task 4: 一键清空数据 (F4)

**状态**：⏳ Pending | **估算**：4h | **优先级**：P1

**修改文件**：
- `backend/.../ClearDataService.java`
- `backend/.../ClearDataController.java`
- `frontend/.../ClearDataView.vue`
- `frontend/.../base.ts` + `AppSidebar.vue`

**验证**：清空后表数据为空，操作记入审计日志。

### Task 5: 销售发票导入完整验证 (F5)

**状态**：⏳ Pending | **估算**：2h | **优先级**：P1

**修改文件**：
- `backend/.../SalesInvoiceImportService.java`（验证 + 可能的修复）

**验证**：预览→确认→生成业务单据(DRAFT)→生成凭证(DRAFT)。

### Task 6: 测试 (F6)

**状态**：⏳ Pending | **估算**：7h | **优先级**：P1

**修改文件**：
- `backend/src/test/.../*Test.java`
- `frontend/e2e/*.spec.ts`

**验证**：`mvn test` + `playwright test` 通过。

---

## 每日站会

### Day 1 — Bug 修复

| 时段 | 工作 |
|:---|:---|
| 上午 | Task 1: 列名映射优化 |
| 下午 | Task 2: 去重导入 |
| 晚上 | Task 3: 单条流水删除 |

### Day 2 — 功能增强

| 时段 | 工作 |
|:---|:---|
| 上午 | Task 4: 一键清空数据 |
| 下午 | Task 5: 销售发票完整验证 |
| 晚上 | 修复测试中发现的 Bug |

### Day 3 — 测试

| 时段 | 工作 |
|:---|:---|
| 上午 | 后端单元测试 + 集成测试 |
| 下午 | 前端 E2E 测试 |
| 晚上 | 回归测试 + 验收 |