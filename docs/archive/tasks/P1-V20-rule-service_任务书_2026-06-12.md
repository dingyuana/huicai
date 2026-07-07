# P1 任务书：V20 种子规则 + ClassificationRule 三件套

> 日期：2026-06-12 | 任务 ID：P1-V20-RULE-SERVICE
> 上游：docs/specs/P1-bank-import-classification.md §1.1 + 01 章节 §4.3
> 策略：方案 C（V20 静态演示种子 + Service 预留 seedForNewTenant）

## 目标

实现分类规则管理所需的全栈三件套：
1. **V20** 静态种子数据（tenant_id=1，8 条）
2. **Entity**：`ClassificationRuleEntity`（17 字段，匹配 V18 schema）
3. **Mapper**：`ClassificationRuleMapper`（MyBatis-Plus BaseMapper 即可）
4. **Service**：`ClassificationRuleService` 接口 + `ClassificationRuleServiceImpl` 实现
5. **API 端点**：`/api/v1/classification-rules` CRUD + reorder + seed + match（按 P1 SPEC §API 端点）

## 实施步骤

### Step 1: V20 迁移（静态种子数据）

新建 `backend/src/main/resources/db/migration/V20__p1_seed_classification_rules.sql`：

- 8 条 INSERT 全部带 tenant_id=1
- 8 条规则严格按 01 章节 §4.3 表格：

| 规则名 | pattern | match_field | direction | classification | priority |
|---|---|---|---|---|---|
| 银行手续费 | 手续费\|工本费\|年费\|账户管理费 | description | out | bank_fee | 1 |
| 利息收入 | 利息\|结息\|存款利息 | description | in | interest_income | 2 |
| 业务收款 | 货款 | description | in | business_receipt | 3 |
| 业务付款 | 货款 | description | out | business_payment | 4 |
| 内部转账 | 转账\|转存\|调拨\|上划\|下拨 | description | (空) | internal_transfer | 5 |
| 税务缴费 | 税\|税务\|缴税\|税金\|税款\|增值税\|所得税\|城建税\|教育费附加\|国家金库\|国库\|印花 | description | out | tax_payment | 6 |
| 社保缴费 | 社保\|公积金\|养老\|医疗\|失业\|工伤\|生育 | description | out | social_security | 7 |
| 保险费用 | 保险\|保费\|投保\|财产险\|责任险\|雇主责任险\|意外险 | description | out | insurance_fee | 8 |

- 5 条带科目映射（来自 01 章节 §4.6）：
  - bank_fee: 借 6602.01 / 贷 1002
  - interest_income: 借 1002 / 贷 6602.02
  - tax_payment: 借 2221.X / 贷 1002
  - social_security: 借 2211.社保 / 贷 1002
  - insurance_fee: 借 6602.06 / 贷 1002
- 3 条留 NULL（business_receipt/business_payment/internal_transfer——走业务单据不需要自动凭证）
- 科目 ID 查找：通过 `SELECT id FROM t_subject WHERE code='1002'` 等 SQL 动态查（避免硬编码 ID）
- `is_active=TRUE`, `rule_type='keyword_regex'`, `created_at/updated_at=now()`

### Step 2: Entity

新建 `backend/src/main/java/com/huicai/module/finance/entity/ClassificationRuleEntity.java`：

- 17 字段全部映射 V18 schema（驼峰命名）
- 关键注解：@TableName("t_classification_rule")、@TableLogic on deleted、@TableField(fill=FieldFill.INSERT) on created_at/updated_at
- 参考 BankStatementEntity 风格

### Step 3: Mapper

新建 `backend/src/main/java/com/huicai/module/finance/mapper/ClassificationRuleMapper.java`：

- 继承 `BaseMapper<ClassificationRuleEntity>`
- 不需要自定义方法（MyBatis-Plus 通用 CRUD 够用）

### Step 4: Service 接口 + 实现

新建：
- `service/ClassificationRuleService.java`（接口）
- `service/impl/ClassificationRuleServiceImpl.java`（实现）

接口方法（严格按 P1 SPEC §API 端点）：
- `R<Page<ClassificationRuleEntity>> page(Long tenantId, Integer current, Integer size)` — 列表分页
- `R<ClassificationRuleEntity> getById(Long id)` — 详情
- `R<ClassificationRuleEntity> create(ClassificationRuleEntity entity)` — 新增
- `R<ClassificationRuleEntity> update(Long id, ClassificationRuleEntity entity)` — 更新
- `R<Void> delete(Long id)` — 逻辑删除
- `R<Void> reorder(List<Long> ids)` — 拖拽排序（按 ids 顺序设 priority 1,2,3...）
- `R<Void> seedForNewTenant(Long tenantId)` — **预留**：为新租户初始化 8 条种子（用 Java 常量 8 条，**不依赖 V20 migration**）
- `R<ClassificationRuleEntity> match(String description, String direction)` — **预留**：单笔测试匹配（本期只写接口骨架，**不实现匹配逻辑**——分类引擎是后续任务）

实现要点：
- 用 `R<T>` 响应体（项目规范，参考 TaxServiceImpl）
- 所有写操作打 `created_by` / `updated_by`（从 SecurityContext 取当前用户，**先取不到就 hardcode 1L**——避免阻塞主流程）
- `seedForNewTenant`：用 Java 常量定义 8 条种子（与 V20 migration 数据一致），按 tenantId 复制后插入；幂等（先 count，>0 直接返回）

### Step 5: Controller

新建 `controller/ClassificationRuleController.java`：

- 8 个端点（按 P1 SPEC）：
  - GET /api/v1/classification-rules
  - GET /{id}
  - POST /
  - PUT /{id}
  - DELETE /{id}
  - POST /reorder
  - POST /seed
  - POST /match

## 验收标准

1. 编译通过（`./mvnw compile` 不报错）
2. V20 跑通：t_classification_rule 有 8 条新数据（tenant_id=1）
3. Service 单测最小：create → getById → update → delete 流程跑通（用 @SpringBootTest + @Transactional 回滚）
4. Controller 8 端点全部 200 OK（用 @WebMvcTest 或 curl）
5. seedForNewTenant 幂等（连续调 2 次，第二次 count 不变）
6. commit message: feat(finance): V20 种子规则 + ClassificationRule 三件套 (P1)

## 不做的事（明确边界）

- ❌ 不实现规则匹配逻辑（match 只写接口骨架）—— 分类引擎是后续任务
- ❌ 不实现兜底启发式
- ❌ 不写 /review /batch-review 端点
- ❌ 不改任何已有 V*__*.sql 文件
- ❌ 不动 t_bank_statement / t_ai_feedback_log
- ❌ 不写前端

## 风险

- 8 条规则 pattern 较长，SQL 转义需谨慎
- V20 启动时若 V18 未跑（理论不可能）会失败——但 Flyway 保证顺序
- @SpringBootTest 启动慢（参考现有 test 写法，能省则省）
