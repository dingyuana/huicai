# 测试防错机制 HUICAI-TST-002

> **编号**：HUICAI-TST-002
> **版本**：V1.0 | **修改日期**：2026-07-07 | **修改人**：Hermes
> **目的**：建立系统性防错流程，防止"代码改了、测试没跟、断层产生"类问题再次发生

---

## 1. 问题根因复盘

本次 bug 暴露了三个层面的系统性问题：

| 层面 | 问题 | 根因 |
|------|------|------|
| 数据层 | INVOICE_OUT 类型单据在工作台不可见 | 前端 docTypes 数组未同步新增 docType |
| 业务层 | docType 是新增长期字段，非原始设计 | 增量开发缺乏"影响面分析"环节 |
| 测试层 | pageQuery() 零测试、前端零组件测试、链路 E2E 空白 | 测试编写依赖"想到哪写到哪"，无强制覆盖要求 |

**核心教训**：这不是一个可以靠"多写几个测试"解决的问题，而是一个**流程缺陷**。

---

## 2. 防错机制：三步闭环

### Step 1: 需求变更影响面分析（编码前）

**触发条件**：任何涉及以下内容的变更：
- 新增/修改 docType、status、direction 等枚举字段
- 新增 API 查询参数
- 修改前端 tab/筛选/路由逻辑
- 新增数据库列或约束

**执行动作**：
1. 在任务书中明确列出"影响面清单"
2. 清单必须包含：
   - 涉及的 Service 方法（特别是 pageQuery/list/getDetail）
   - 涉及的前端组件（特别是列表页、筛选器、tab 切换）
   - 涉及的 E2E 链路（从数据创建到最终可见）
   - 需要同步更新的 docType/status 枚举数组

**示例**（本次 bug 应该产生的清单）：
```
影响面清单：
[ ] Service: BusinessDocService.pageQuery() — 新增 INVOICE_OUT 参数
[ ] Frontend: ReconciliationWorkbench.vue — docTypes 数组需添加 INVOICE_OUT
[ ] Frontend: 核销推荐 API 调用方
[ ] E2E: 销售发票导入 → 工作台可见性验证
[ ] Test: BusinessDocServiceImplTest.pageQuery() 新增测试
[ ] Test: ReconciliationWorkbench 组件测试
```

### Step 2: 测试覆盖率门禁（提交前）

**强制规则**：以下场景必须写测试，否则 PR 不合并：

| 场景 | 必须有的测试 |
|------|-------------|
| 新增/修改枚举字段（docType/status/direction） | Service 层参数化测试 + 前端组件测试 + E2E 链路测试 |
| 新增 API 查询参数 | Controller + Service 测试 |
| 修改前端 tab/筛选逻辑 | 前端组件测试（至少断言 tab 切换后请求参数正确） |
| 跨模块数据流变更 | E2E 测试（验证端到端链路） |
| 数据库 schema 变更 | Mapper 层约束测试 |

**验证方式**：
- 后端：`mvn test -pl backend` 全部通过
- 前端：`npm run test` 全部通过
- E2E：新增链路 E2E 用例通过

### Step 3: 定期审计（每周/每月）

**自动化**：
- CI 中运行测试覆盖率检查（JaCoCo 阈值 ≥ 80%）
- 前端 vitest 覆盖率检查

**人工**：
- 每完成一个模块，更新 `test-coverage-matrix.md`
- 每月审查一次空白区域

---

## 3. 枚举字段变更检查清单

每次涉及枚举字段（docType/status/direction/类型码）变更时，**必须逐项核对**：

### 3.1 后端

- [ ] Service 层所有查询方法是否接受新枚举值？
- [ ] MyBatis XML/Wrapper 是否有硬编码过滤？
- [ ] 前端 API 调用方的参数对象是否同步更新？
- [ ] 状态机转换图是否包含新枚举值？
- [ ] 凭证模板映射是否包含新枚举值？
- [ ] 报表/统计查询是否包含新枚举值？
- [ ] 权限配置是否包含新枚举值？

### 3.2 前端

- [ ] 所有下拉框/单选组/多选框选项列表
- [ ] tab 切换后的查询参数（docTypes 数组）
- [ ] 筛选器的枚举选择器
- [ ] 路由守卫/权限检查
- [ ] 表格列的显示/隐藏条件
- [ ] 表单验证规则

### 3.3 测试

- [ ] Service 层 pageQuery() 测试覆盖所有枚举值组合
- [ ] 前端组件测试覆盖 tab 切换 + 参数传递
- [ ] E2E 测试覆盖"数据创建→查询可见→操作"完整链路
- [ ] 跨实体链路测试（如：发票→业务单→工作台→核销）

---

## 4. 端到端链路测试规范

### 4.1 链路定义

每个业务模块必须定义完整的端到端链路，并在 E2E 测试中覆盖：

```
数据输入 → 数据处理 → 数据存储 → 数据查询 → 数据操作 → 下游影响
```

### 4.2 核销工作台链路（本次 bug 的缺失）

```
销售发票导入 → OutputInvoiceEntity 插入 → BusinessDocServiceImpl.create()
→ INVOICE_OUT 类型 t_business_doc 插入 → pageQuery() 查询 → ReconciliationWorkbench.vue 渲染
→ tab=RECEIPT → docTypes=['RECEIPT','INVOICE_OUT','OTHER_RECEIVABLE']
→ 前端请求 GET /api/v1/business-docs?page=1&size=20&docTypes=RECEIPT,INVOICE_OUT,OTHER_RECEIVABLE
→ 后端 pageQuery() 执行 → 返回 INVOICE_OUT 记录 → 前端表格显示
→ 点击"核销推荐" → 调用推荐 API → 返回匹配结果
```

**E2E 测试必须验证每一步**，不能只停在"销售发票导入成功"。

### 4.3 链路测试模板

```java
// 伪代码：链路测试结构
@Test
void fullChain_invoiceToWorkbench_shouldBeVisible() {
    // 1. 导入销项发票
    OutputInvoiceEntity invoice = createAndImportOutputInvoice("9999.E2E.WB.001");
    
    // 2. 验证 INVOICE_OUT 业务单已创建
    BusinessDocEntity doc = businessDocMapper.selectOne(
        new LambdaQueryWrapper<BusinessDocEntity>()
            .eq(BusinessDocEntity::getDocType, "INVOICE_OUT")
            .eq(BusinessDocEntity::getInvoiceNo, invoice.getInvoiceNo())
    );
    assertNotNull(doc);
    
    // 3. 验证 pageQuery 返回该单据
    BusinessDocQueryDTO query = new BusinessDocQueryDTO();
    query.setDocTypes(List.of("RECEIPT", "INVOICE_OUT", "OTHER_RECEIVABLE"));
    IPage<BusinessDocVO> page = businessDocService.pageQuery(query);
    assertTrue(page.getRecords().stream()
        .anyMatch(v -> "INVOICE_OUT".equals(v.getDocType())));
    
    // 4. 验证前端组件能渲染（E2E）
    // playwright: goto 工作台 → 选 tab RECEIPT → 断言 INVOICE_OUT 行可见
}
```

---

## 5. 执行纪律

### 5.1 开发时

- 每次新增枚举值，先写影响面清单
- 清单未完成，不开始编码
- 编码完成后，对照清单逐项打勾

### 5.2 提 PR 前

- 测试覆盖率门禁检查通过
- 影响面清单全部打勾
- E2E 链路测试新增/更新

### 5.3 审查时

-  reviewer 必须检查影响面清单
-  reviewer 必须验证 E2E 链路完整性
-  reviewer 必须确认枚举变更的同步更新

---

## 6. 与现有流程的集成

本机制不新增独立流程，而是嵌入现有三步闭环：

| 现有步骤 | 本机制集成点 |
|----------|-------------|
| SPEC 编写 | SPEC 中必须包含"枚举变更影响面"小节 |
| Plan 编写 | Plan 中必须列出测试覆盖清单 |
| 审核门 | 审核时必须检查影响面清单和测试覆盖 |
| 执行 | 编码时对照清单逐项完成 |
| Verify | 验证时检查 E2E 链路 |
| Spec Audit | 审计时检查文档与实际一致性 |

---

> **文档结束**
